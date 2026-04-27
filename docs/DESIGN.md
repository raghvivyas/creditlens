# CreditLens — Design Document

> **Author:** raghvivyas  
> **Stack:** Java 8 · Spring Boot 2.7 · PostgreSQL · Flyway · OpenAI GPT-4 · AWS ECS / RDS · JWT  
> **Domain context:** Built on direct experience supporting Bank of Baroda as a client — modelling real PSU bank credit underwriting workflows, FOIR/LTV/CIBIL eligibility gates, and the audit-trail requirements of RBI-regulated lending.

---

## 1. Problem Statement

Indian banks process hundreds of thousands of loan applications monthly. The traditional underwriting process is:

1. **Manual** — a credit officer reads the application and applies heuristic rules learned over years.
2. **Inconsistent** — different officers interpret the same FOIR differently.
3. **Opaque** — the applicant receives "Rejected" with no explanation.
4. **Slow** — manual assessment takes 2–5 business days.

CreditLens solves all four: it applies a consistent rule engine, enriches every decision with a natural-language GPT-4 explanation, and persists a full immutable audit trail for RBI compliance — all in under 200 ms per assessment.

---

## 2. High-Level Architecture

```
Client (Bank Officer / API Consumer)
         │
         │  POST /api/assessments  { application payload }
         ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Spring MVC REST Layer                         │
│   AssessmentController ─── AuthController ─── EmiCalculator    │
└────────────────────┬────────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                  AssessmentService (orchestrator)               │
│                                                                 │
│   1. RuleEngineService                                          │
│      ├─ calculateEmi()      EMI = P·r·(1+r)^n / ((1+r)^n−1)   │
│      ├─ calculateFoir()     (existingEMI + newEMI) / income     │
│      ├─ calculateLtv()      loanAmount / collateralValue        │
│      ├─ calculateDti()      totalDebt / totalIncome             │
│      ├─ evaluate()          stream 8 Java-8 RiskRules           │
│      ├─ decide()            score→ APPROVED/CONDITIONAL/REJECTED│
│      └─ riskBand()          LOW / MEDIUM / HIGH / VERY_HIGH     │
│                                                                 │
│   2. AiExplanationService                                       │
│      ├─ GPT-4 Chat API  (if OPENAI_API_KEY configured)         │
│      └─ Template fallback (always works, zero cost)             │
│                                                                 │
│   3. LoanApplicationRepository.save()  ── full audit record     │
└─────────────────────────────────────────────────────────────────┘
         │
         ▼
┌───────────────────────────┐
│  PostgreSQL (AWS RDS)     │
│  loan_applications table  │
│  users table              │
└───────────────────────────┘
```

**No message queue, no cache** — CreditLens is deliberately simpler than StockStream. Loan assessments are synchronous request-response: the officer submits an application and gets a decision back in the same HTTP call. Adding Kafka would be premature optimisation — it belongs when the throughput exceeds what a single connection pool can handle (typically 500+ req/s sustained).

---

## 3. The Rule Engine — Design Rationale

### 3.1 Why a custom rule engine over Drools or a scoring model?

Three reasons:

1. **Auditability.** RBI regulations require every credit decision to be explainable. A decision from a trained ML model is a black box. A functional rule engine fires named flags — `HIGH_FOIR`, `LOW_CREDIT_SCORE` — that can be read verbatim in an audit log.

2. **Threshold configurability.** Risk appetite differs between loan products. A gold loan can tolerate a higher LTV (85%) than a personal loan (nil). Every threshold in CreditLens is an environment variable — `MAX_FOIR=0.65` in production overrides the default without a redeployment.

3. **Testability.** Each rule is a pure `(application, metrics) → RiskFlag` function. Pure functions have no side effects, which means they are trivially unit-testable with zero mocking. The `RuleEngineServiceTest` suite covers 11 cases with no Spring context, no database, and no network — it runs in under 100 ms.

### 3.2 Java 8 Functional Rule Engine — Code Walk-Through

The engine defines a `@FunctionalInterface RiskRule`:

```java
@FunctionalInterface
private interface RiskRule {
    RiskFlag evaluate(LoanApplicationRequest app, RiskMetrics metrics);
}
```

All 8 rules are collected into a `List<RiskRule>` using lambdas:

```java
List<RiskRule> rules = Arrays.asList(
    (app, m) -> app.getAge() < props.getMinAge()
                ? RiskFlag.UNDERAGE : RiskFlag.CLEAR,

    (app, m) -> m.getFoir() != null &&
                m.getFoir().compareTo(BigDecimal.valueOf(props.getMaxFoir())) > 0
                ? RiskFlag.HIGH_FOIR : RiskFlag.CLEAR,

    (app, m) -> app.getCreditScore() < props.getMinCreditScore()
                ? RiskFlag.LOW_CREDIT_SCORE : RiskFlag.CLEAR,

    // ... 5 more rules
);
```

Evaluation is a single stream pipeline:

```java
List<RiskFlag> flags = buildRules().stream()
    .map(rule -> rule.evaluate(app, partialMetrics))
    .filter(flag -> flag != RiskFlag.CLEAR)
    .collect(Collectors.toList());
```

This is idiomatic Java 8. The pipeline is:
- **Readable** — adding a new rule is one lambda in one list.
- **Composable** — rules are independent; the order doesn't matter.
- **O(n) where n = number of rules** — 8 rules fire in a few microseconds.

### 3.3 Risk Score and Decision Mapping

| Score Range | Decision             | Meaning                                      |
|-------------|----------------------|----------------------------------------------|
| 0–39        | `APPROVED`           | Strong profile; proceed to documentation     |
| 40–59       | `CONDITIONAL_APPROVAL` | Borderline; specific conditions must be met |
| 60–100      | `REJECTED`           | Insufficient creditworthiness at this time   |

Score is computed as:
```
score = 20 (base)  +  Σ penalty(flag)
```

Penalties are calibrated to PSU bank norms:
- `HIGH_FOIR`: +25 (most common rejection reason at BoB)
- `LOW_CREDIT_SCORE`: +25 (second most common)
- `INSUFFICIENT_INCOME`: +30 (hardest to resolve — rejected immediately)
- `UNDERAGE` / `OVERAGE`: +40 (absolute eligibility gate — no exceptions)

---

## 4. AI Explanation Layer — Design Rationale

### 4.1 Why GPT-4 over GPT-3.5 for this use case?

GPT-3.5 produces grammatically correct text but can be imprecise about financial figures ("your FOIR is somewhat high"). GPT-4 accurately references specific percentages, names the relevant RBI guideline, and produces advice-quality explanations. For a banking product where the explanation has legal and compliance significance, the quality uplift justifies the cost (~₹0.75 per assessment at GPT-4 rates).

### 4.2 Prompt Engineering

The system prompt establishes the model's persona and format constraints:

```
"You are a senior credit underwriter at an Indian public sector bank with 20
years of experience evaluating loan applications per RBI guidelines. Provide
a concise, professional explanation of the loan decision in 3–4 sentences.
Mention specific financial metrics (FOIR, credit score, LTV) where relevant."
```

The user prompt is a structured table of computed values — not raw JSON — because structured tables use fewer tokens than JSON keys and produce more focused responses. `temperature=0.4` keeps the output professional and repeatable while still allowing natural variation.

### 4.3 Graceful Degradation

If `OPENAI_API_KEY` is empty or the API call throws any exception, the service falls back to a deterministic template-based explanation. This means:

- **Zero-cost demo** — the full application works without an API key.
- **No single point of failure** — an OpenAI outage does not block loan assessments.
- **Identical response schema** — the caller sees the same `AssessmentResponse` shape regardless of which code path ran.

---

## 5. Database Design

### 5.1 Why `NUMERIC` for all financial amounts?

IEEE 754 `DOUBLE PRECISION` cannot represent `0.1` or `0.2` exactly. Over thousands of FOIR calculations:

```
income = 100000.00
emi    = 60000.00
ratio  = 60000.00 / 100000.00  →  0.5999999999... (DOUBLE)
                                →  0.6000          (NUMERIC)
```

The double representation would fire the FOIR threshold check incorrectly. `NUMERIC(15,2)` is exact. This matches how NSE stores prices and how RBI mandates financial record-keeping.

### 5.2 Audit Trail Design

Every loan application record is **immutable after creation** — there is no UPDATE endpoint. This is deliberate:

- RBI requires a complete, unmodifiable audit trail for all credit decisions.
- If a credit officer wants to re-assess an application with different parameters, they submit a new application. The history of all assessments is preserved.
- The `assessed_by` column records which officer triggered the assessment, enabling branch-level accountability reports.

### 5.3 Schema

```
loan_applications
─────────────────────────────────────────────────────
id                  BIGSERIAL PK
applicant_name      VARCHAR(150)   NOT NULL
age                 INTEGER        NOT NULL
employment_type     VARCHAR(30)    NOT NULL           -- SALARIED|SELF_EMPLOYED|...
monthly_income      NUMERIC(15,2)  NOT NULL
existing_emi/month  NUMERIC(15,2)  NOT NULL
credit_score        INTEGER        NOT NULL           -- 300–900 CIBIL scale
loan_amount         NUMERIC(15,2)  NOT NULL
tenure_months       INTEGER        NOT NULL
annual_interest_rate NUMERIC(6,2)  NOT NULL
loan_purpose        VARCHAR(30)    NOT NULL           -- HOME_LOAN|PERSONAL_LOAN|...
collateral_value    NUMERIC(15,2)  NULL               -- only for secured loans
officer_remarks     TEXT           NULL

── Computed ──────────────────────────────────────────
decision            VARCHAR(30)    NOT NULL  CHECK IN (APPROVED,CONDITIONAL_APPROVAL,REJECTED)
risk_score          INTEGER        NOT NULL  CHECK BETWEEN 0 AND 100
risk_band           VARCHAR(20)              -- LOW|MEDIUM|HIGH|VERY_HIGH
foir                NUMERIC(6,4)             -- e.g. 0.5832
ltv                 NUMERIC(6,4)             -- NULL for unsecured loans
dti                 NUMERIC(6,4)
proposed_emi        NUMERIC(15,2)
flag_descriptions   TEXT                     -- comma-delimited
ai_explanation      TEXT                     -- GPT-4 explanation or template
conditions          TEXT                     -- comma-delimited conditions
assessed_by         VARCHAR(50)              -- officer username
assessed_at         TIMESTAMPTZ
created_at          TIMESTAMPTZ

Indexes: decision, assessed_at DESC, applicant_name, loan_purpose
```

### 5.4 Flyway — Why Schema Versioning Matters

`spring.jpa.hibernate.ddl-auto=validate` means Hibernate checks the schema but never modifies it. All schema changes go through Flyway migration files (`V1__`, `V2__`, ...). This is the pattern used in production banking systems because:

- Schema changes are peer-reviewed before merging.
- The migration file is the single source of truth for the schema at any point in time.
- Rollbacks are safe: revert `V2__` by deploying `V2__rollback.sql`.
- `ddl-auto=create-drop` is dangerous in production and banned in any serious engineering shop.

---

## 6. Authentication and Multi-Role Design

Three roles are seeded:

| Role    | Can do                                                         |
|---------|----------------------------------------------------------------|
| ADMIN   | All endpoints + user management                                |
| OFFICER | Submit assessments, view own assessments, dashboard            |
| VIEWER  | Read-only: list assessments, view dashboard, EMI calculator    |

**Why stateless JWT over sessions?**  
CreditLens is designed to run on ECS Fargate behind an ALB. Sessions require sticky routing or a shared session store. JWT is self-contained — any ECS task can validate any token independently. Token signing uses HS512 (HMAC-SHA-512) rather than HS256, providing stronger collision resistance against brute-force attacks with negligible CPU overhead on modern hardware.

---

## 7. EMI Calculator Endpoint

`GET /api/calculator/emi?principal=5000000&rate=8.5&months=240`

The standard EMI formula used across all Indian banks:

```
EMI = P × r × (1+r)^n
      ─────────────────
         (1+r)^n − 1

where r = annualRate / 100 / 12   (monthly interest rate)
      n = tenure in months
```

Edge case handled: when `rate=0` (zero-interest schemes), the formula divides by zero. CreditLens detects this and returns `P / n` (simple division).

This endpoint is intentionally public to allow embedding in affordability calculators, loan comparison widgets, and branch kiosks without requiring authentication.

---

## 8. Trade-offs and Alternatives Considered

| Decision | Chosen | Alternative | Why chosen |
|----------|--------|-------------|------------|
| Rule engine | Java 8 lambdas + stream | Drools / OpenL Tablets | Zero dependency; trivially testable; each rule is one line |
| AI response format | Structured free-text | JSON mode / function calling | Fewer tokens → lower cost; GPT-4 is reliable enough at 3-sentence explanations |
| Schema management | Flyway + `ddl-auto=validate` | `ddl-auto=create-drop` | Audit compliance; production safety |
| Amounts | `NUMERIC` / `BigDecimal` | `double` / `DOUBLE PRECISION` | Exact decimal arithmetic; no floating-point drift |
| AI fallback | Deterministic template | Throw exception | Zero single point of failure; fully functional without API key |
| Auth | Stateless JWT HS512 | Session + Redis | Horizontal scaling without sticky routing |
| Assessment immutability | Insert-only, no UPDATE | Mutable records | RBI audit-trail compliance |
| Concurrency | JDBC / JPA default | Pessimistic locking | Assessment creation is not contentious; optimistic is sufficient |

---

## 9. Performance Characteristics

| Operation | Typical latency | Bottleneck |
|-----------|----------------|------------|
| Rule evaluation (8 rules) | < 1 ms | CPU (pure computation) |
| EMI calculation | < 0.5 ms | CPU (Math.pow) |
| Database write (assessment) | 5–15 ms | Network to RDS |
| OpenAI GPT-4 call | 3–8 s | External API |
| Full assessment (with AI) | 3–9 s | OpenAI latency |
| Full assessment (fallback) | 10–25 ms | RDS write |

**Practical implication:** if sub-second response is required, run OpenAI asynchronously and return a `202 Accepted` with a polling URL. The current synchronous implementation is the correct starting point — optimise only when a real user is waiting and complaining, not in advance.

---

## 10. AWS Deployment Architecture

```
Client
  │
  ▼
API Gateway (HTTP API)
  │  JWT authorizer
  ▼
ALB
  │
  ▼
ECS Fargate (creditlens-app)
  │  512 CPU / 1024 MB
  │  env vars from SSM Parameter Store
  │
  ├──▶ RDS PostgreSQL (Multi-AZ, db.t3.small)
  │       creditlens database
  │
  └──▶ OpenAI API (external)
           api.openai.com/v1/chat/completions
```

**Secrets management:** All sensitive values (`DB_PASSWORD`, `JWT_SECRET`, `OPENAI_API_KEY`) are stored in SSM Parameter Store as `SecureString`. The ECS task definition references them by ARN — they are injected as environment variables at container start, never baked into the image or visible in CloudWatch logs.

**Cost estimate (Mumbai region, minimal traffic):**
- RDS db.t3.small Multi-AZ: ~$60/month
- ECS Fargate (512 CPU, 1 GB, 1 task): ~$15/month
- API Gateway (1M req/month): ~$3.50/month
- OpenAI GPT-4 (1000 assessments/month at 300 tokens each): ~₹750/month
- **Total: ~$80/month**

---

## 11. Security Controls

| Layer | Control |
|-------|---------|
| REST endpoints | JWT Bearer token required (except `/api/auth/**`, `/actuator/health`, `/api/calculator/emi`) |
| Passwords | BCrypt cost factor 10 (auto-upgrades with Spring Security) |
| JWT | HS512 signed; 24 h expiry; configurable via env var |
| Database secrets | SSM Parameter Store `SecureString`; never in code or images |
| Schema | Flyway enforces; no DDL at runtime |
| Docker image | Non-root user `creditlens`; Alpine JRE base |
| Input validation | JSR-380 Bean Validation on all request fields; `GlobalExceptionHandler` returns 400 with message |
| SQL injection | JPA/Hibernate parameterised queries throughout; no native string concatenation |
| CORS | Configurable; lock down to specific origins in production |

---

## 12. What I Would Add With More Time

1. **Async AI enrichment** — return `202 Accepted` immediately, enrich with GPT-4 in a background thread, push result via WebSocket or polling endpoint. Eliminates the 3–8 s wait.
2. **Co-applicant support** — when `HIGH_FOIR` fires, the rule engine should accept a second applicant's income and re-evaluate. Common in home loan processing.
3. **Credit bureau integration** — replace mock `creditScore` field with a real CIBIL/Experian API pull using the applicant's PAN. Requires agreement with the bureau.
4. **Product-specific rule sets** — HOME_LOAN, PERSONAL_LOAN, and LAP have different thresholds. Currently handled via env vars; could be extended to a per-product `RuleSet` entity stored in the database.
5. **Prometheus metrics** — expose approval rate, P99 assessment latency, and OpenAI call success rate as Micrometer gauges for a Grafana dashboard.
6. **Rate limiting** — protect the `/api/assessments` endpoint with the VaultCache rate limiter (Project 4) to prevent bulk submission abuse.
