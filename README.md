# CreditLens 🏦

> AI-powered loan risk assessment API — built with Java 8, Spring Boot, OpenAI GPT-4, and PostgreSQL. Deploys to AWS ECS Fargate.

**Domain context:** Designed using first-hand knowledge of Bank of Baroda's credit underwriting workflows. Implements real PSU bank eligibility criteria — FOIR, LTV, CIBIL score gates, DTI — as a Java 8 functional rule engine, enriched with GPT-4 natural-language explanations for every decision.

[![CI](https://github.com/raghvivyas/creditlens/actions/workflows/ci.yml/badge.svg)](https://github.com/YOUR_USERNAME/creditlens/actions)
[![Java 8](https://img.shields.io/badge/Java-8-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18-brightgreen.svg)](https://spring.io/projects/spring-boot)

---

## Architecture

```
POST /api/assessments
        │
        ▼
  AssessmentController
        │
        ▼
  AssessmentService (orchestrator)
     │          │              │
     ▼          ▼              ▼
 RuleEngine  AiExplanation  PostgreSQL
 (8 lambdas)  (GPT-4 or      (immutable
  FOIR/LTV/   template       audit trail)
  DTI/CIBIL   fallback)
```

**Decision flow:**
```
Application → EMI calculation → FOIR/LTV/DTI/CIBIL rules → Risk Score
                                                                 │
                         0–39 → APPROVED                         │
                        40–59 → CONDITIONAL_APPROVAL  ◀──────────┘
                        60–100 → REJECTED
                                 │
                                 ▼
                         GPT-4 explanation → persist → respond
```

---

## Quick Start (2 commands)

```bash
git clone https://github.com/raghvivyas/creditlens.git
cd creditlens
docker-compose up --build
```

App starts at **http://localhost:8080**. Default users are created automatically:

| Username | Password     | Role    |
|----------|--------------|---------|
| admin    | password123  | ADMIN   |
| officer  | password123  | OFFICER |
| viewer   | password123  | VIEWER  |

**With AI explanations** (optional — works fully without it):
```bash
OPENAI_API_KEY=sk-your-key docker-compose up --build
```

---

## API Reference

### 1. Get a JWT Token

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"officer","password":"password123"}' \
  | jq -r '.data.token')

echo $TOKEN
```

---

### 2. Submit a Loan Application

```bash
curl -s -X POST http://localhost:8080/api/assessments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "applicantName":       "Priya Sharma",
    "age":                 32,
    "employmentType":      "SALARIED",
    "monthlyIncome":       80000,
    "existingEmiPerMonth": 5000,
    "creditScore":         760,
    "loanAmount":          800000,
    "tenureMonths":        60,
    "annualInterestRate":  10.5,
    "loanPurpose":         "PERSONAL_LOAN",
    "officerRemarks":      "Stable employment, 4 years at current employer"
  }' | jq .
```

**APPROVED response:**
```json
{
  "success": true,
  "message": "Assessment complete",
  "data": {
    "assessmentId": 1,
    "applicantName": "Priya Sharma",
    "decision": "APPROVED",
    "riskScore": 28,
    "riskBand": "LOW",
    "foirPct": "38.2%",
    "ltvPct": "N/A",
    "dtiPct": "31.5%",
    "proposedEmi": 17204.32,
    "flagDescriptions": [],
    "aiExplanation": "The loan application for ₹8,00,000 has been approved. The applicant demonstrates strong repayment capacity with a FOIR of 38.2% — well within the 60% threshold — and a healthy CIBIL score of 760. The proposed EMI of ₹17,204 represents a manageable 21.5% of gross monthly income. Disbursement can proceed subject to standard documentation verification.",
    "conditions": [],
    "assessedAt": "2024-01-15T10:32:45Z"
  }
}
```

---

### 3. Test the Risk Rules

**High FOIR → CONDITIONAL_APPROVAL:**
```bash
curl -s -X POST http://localhost:8080/api/assessments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "applicantName":       "Arjun Mehta",
    "age":                 38,
    "employmentType":      "SALARIED",
    "monthlyIncome":       90000,
    "existingEmiPerMonth": 52000,
    "creditScore":         720,
    "loanAmount":          600000,
    "tenureMonths":        48,
    "annualInterestRate":  11.0,
    "loanPurpose":         "PERSONAL_LOAN"
  }' | jq '.data.decision, .data.foirPct, .data.flagDescriptions'
```

```json
"CONDITIONAL_APPROVAL"
"72.4%"
["FOIR exceeds maximum allowed threshold of 60%"]
```

**Multiple flags → REJECTED:**
```bash
curl -s -X POST http://localhost:8080/api/assessments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "applicantName":       "Ramesh Kumar",
    "age":                 19,
    "employmentType":      "STUDENT",
    "monthlyIncome":       15000,
    "existingEmiPerMonth": 8000,
    "creditScore":         560,
    "loanAmount":          500000,
    "tenureMonths":        36,
    "annualInterestRate":  18.0,
    "loanPurpose":         "PERSONAL_LOAN"
  }' | jq '.data.decision, .data.riskScore, .data.flagDescriptions'
```

```json
"REJECTED"
100
[
  "Applicant is below minimum eligible age of 21",
  "FOIR exceeds maximum allowed threshold of 60%",
  "CIBIL credit score below minimum required threshold of 650"
]
```

**Home loan with high LTV:**
```bash
curl -s -X POST http://localhost:8080/api/assessments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "applicantName":       "Sunita Patel",
    "age":                 35,
    "employmentType":      "SALARIED",
    "monthlyIncome":       120000,
    "existingEmiPerMonth": 0,
    "creditScore":         780,
    "loanAmount":          9000000,
    "tenureMonths":        240,
    "annualInterestRate":  8.75,
    "loanPurpose":         "HOME_LOAN",
    "collateralValue":     10000000,
    "officerRemarks":      "Flat in Andheri West, ready possession"
  }' | jq '.data.decision, .data.ltvPct, .data.flagDescriptions'
```

---

### 4. View & Filter Assessments

```bash
# All assessments (paginated)
curl -s "http://localhost:8080/api/assessments?page=0&size=10" \
  -H "Authorization: Bearer $TOKEN" | jq '.data.totalElements'

# Filter by decision
curl -s "http://localhost:8080/api/assessments?decision=REJECTED&size=5" \
  -H "Authorization: Bearer $TOKEN" | jq '.data.content[].applicantName'

# Filter by date range
curl -s "http://localhost:8080/api/assessments?from=2024-01-01T00:00:00Z&to=2024-12-31T23:59:59Z" \
  -H "Authorization: Bearer $TOKEN" | jq .

# Retrieve single assessment (audit record)
curl -s http://localhost:8080/api/assessments/1 \
  -H "Authorization: Bearer $TOKEN" | jq .
```

---

### 5. Dashboard Stats

```bash
curl -s http://localhost:8080/api/assessments/dashboard \
  -H "Authorization: Bearer $TOKEN" | jq .
```

```json
{
  "success": true,
  "data": {
    "totalApplications": 142,
    "approved": 89,
    "conditionalApproval": 23,
    "rejected": 30,
    "approvalRatePct": 78.9,
    "rejectionRatePct": 21.1,
    "avgRiskScore": 34.2,
    "avgLoanAmount": 1245000.00,
    "topRejectionReason": "FOIR exceeds maximum allowed threshold of 60%"
  }
}
```

---

### 6. EMI Calculator (no auth required)

```bash
# ₹50 lakh home loan at 8.5% for 20 years
curl -s "http://localhost:8080/api/calculator/emi?principal=5000000&rate=8.5&months=240" | jq .
```

```json
{
  "success": true,
  "data": {
    "principal": 5000000,
    "annualRatePct": 8.5,
    "tenureMonths": 240,
    "monthlyEmi": 43391.16,
    "totalPayable": 10413878.40,
    "totalInterest": 5413878.40
  }
}
```

---

## Running Without Docker

**Prerequisites:** Java 8, Maven 3.6+, PostgreSQL 14+

```bash
# Start PostgreSQL only
docker-compose up postgres -d

# Copy env template
cp .env.example .env
# Edit .env (set DB password, optionally OPENAI_API_KEY)

export $(cat .env | grep -v '#' | xargs)
./mvnw spring-boot:run
```

---

## Running Tests

```bash
# All unit tests (no database or network needed)
./mvnw test

# With coverage report
./mvnw verify
# Open: target/site/jacoco/index.html
```

Tests use H2 in-memory database — **no infrastructure required**.

---

## Project Structure

```
creditlens/
├── src/
│   ├── main/java/com/creditlens/
│   │   ├── CreditLensApplication.java
│   │   ├── config/                        # AppProperties, Security, RestTemplate
│   │   ├── model/                         # DTOs, enums (Decision, RiskFlag, LoanPurpose...)
│   │   ├── entity/                        # LoanApplicationEntity, UserEntity
│   │   ├── repository/                    # JPA repos with custom JPQL queries
│   │   ├── service/
│   │   │   ├── RuleEngineService.java      # ★ Core: 8 Java-8 lambda risk rules
│   │   │   ├── AiExplanationService.java   # GPT-4 call + template fallback
│   │   │   ├── AssessmentService.java      # Orchestrator: rules → AI → persist
│   │   │   └── AuthService.java
│   │   ├── controller/                    # Assessment, Auth, EmiCalculator, Info
│   │   ├── security/                      # JWT provider, filter, UserDetailsService
│   │   ├── exception/                     # GlobalExceptionHandler, ResourceNotFoundException
│   │   └── scheduler/                     # DataInitializer (seeds default users)
│   ├── main/resources/
│   │   ├── application.yml
│   │   └── db/migration/V1__create_tables.sql
│   └── test/java/com/creditlens/
│       ├── CreditLensApplicationTests.java
│       ├── service/RuleEngineServiceTest.java      # 11 unit tests, zero mocking
│       ├── service/AiExplanationServiceTest.java   # Fallback + AI path tests
│       └── controller/AssessmentControllerTest.java
├── docs/
│   └── DESIGN.md                          # Full architecture + trade-off tables
├── infra/
│   ├── ecs-task-definition.json           # AWS ECS Fargate deployment
│   ├── api-gateway-config.json            # HTTP API Gateway routes + throttling
│   └── ecr-push.sh                        # Docker image push to AWS ECR
├── Dockerfile                             # Multi-stage: Maven builder → Alpine JRE
├── docker-compose.yml                     # PostgreSQL + App
├── .env.example                           # Environment variable template
└── .github/workflows/ci.yml              # GitHub Actions CI
```

---

## Risk Rules Reference

| Rule | Threshold (default) | Penalty | Override via env var |
|------|--------------------|---------|--------------------|
| FOIR > max | 60% | +25 | `MAX_FOIR=0.65` |
| LTV > max (secured loans) | 80% | +20 | `MAX_LTV=0.85` |
| DTI > max | 55% | +20 | `MAX_DTI=0.60` |
| Credit score < min | 650 | +25 | `MIN_CREDIT_SCORE=700` |
| Age < min | 21 | +40 | `MIN_AGE=23` |
| Age > max | 65 | +40 | `MAX_AGE=60` |
| EMI ≥ monthly income | — | +30 | — |
| Personal loan > 60× income | — | +15 | — |

All thresholds are environment variables — change per product without redeployment.

---

## Deploying to AWS

```bash
# 1. Push image to ECR
./infra/ecr-push.sh 111122223333 ap-south-1

# 2. Store secrets in SSM Parameter Store
aws ssm put-parameter --name /creditlens/DB_PASSWORD    --value "your-rds-password"  --type SecureString
aws ssm put-parameter --name /creditlens/JWT_SECRET     --value "$(openssl rand -hex 32)" --type SecureString
aws ssm put-parameter --name /creditlens/OPENAI_API_KEY --value "sk-your-key"         --type SecureString

# 3. Register ECS task definition
aws ecs register-task-definition \
  --cli-input-json file://infra/ecs-task-definition.json \
  --region ap-south-1

# 4. Deploy to ECS
aws ecs update-service \
  --cluster creditlens \
  --service creditlens-svc \
  --force-new-deployment \
  --region ap-south-1
```

---

## Key Design Decisions

| Decision | Choice | Why |
|----------|--------|-----|
| Rule engine | Java 8 lambdas + stream | Zero dependency; one rule = one lambda; trivially unit-testable |
| Price types | `NUMERIC` / `BigDecimal` | Exact decimal — floats cause drift in FOIR calculations |
| AI fallback | Deterministic template | No single point of failure; full functionality without API key |
| Schema changes | Flyway | RBI audit compliance; `ddl-auto=create-drop` banned in production |
| Assessment records | Insert-only, no UPDATE | Immutable audit trail per RBI regulations |
| Auth | Stateless JWT HS512 | No sticky sessions; horizontal scaling on ECS |

Full reasoning in [`docs/DESIGN.md`](docs/DESIGN.md).

---

## About

Built to demonstrate a production-grade AI-enriched decision API using patterns from real Indian banking credit workflows. The domain model (FOIR, LTV, CIBIL, Bank of Baroda underwriting norms) reflects first-hand experience working with PSU bank systems.

