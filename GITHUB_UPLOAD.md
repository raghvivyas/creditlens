# How to Upload CreditLens to GitHub

## Step 1 — Create the Repository on GitHub

1. Go to https://github.com/new
2. Fill in:
   - **Repository name:** `creditlens`
   - **Description:** `AI-powered loan risk assessment API — Java 8 rule engine + GPT-4 explanations + PostgreSQL audit trail`
   - **Visibility:** Public
   - **DO NOT** check "Add README", "Add .gitignore" — already included
3. Click **Create repository**
4. Copy the URL: `https://github.com/YOUR_USERNAME/creditlens.git`

---

## Step 2 — Update Placeholders

In `README.md`, replace `YOUR_USERNAME` with your GitHub username (2 occurrences).

In `docs/DESIGN.md`, replace `<Your Name>` with your actual name.

In `infra/ecs-task-definition.json`, replace `ACCOUNT_ID` (4 occurrences) with your AWS account ID.

---

## Step 3 — Initialise Git and Push

Run from inside the `creditlens/` folder:

```bash
cd creditlens

git init

git add .

git commit -m "feat: initial CreditLens implementation

- Java 8 functional rule engine: 8 lambda-based risk rules (FOIR, LTV, DTI,
  CIBIL, age gates, income sufficiency, loan amount cap)
- EMI formula: P·r·(1+r)^n / ((1+r)^n−1) using BigDecimal for exact arithmetic
- OpenAI GPT-4 explanation layer with deterministic template fallback
- Immutable audit trail in PostgreSQL (insert-only, no UPDATE endpoint)
- NUMERIC(15,2) for all financial amounts — no floating-point drift
- Flyway schema migrations (V1: loan_applications, users)
- JWT HS512 stateless auth with three roles: ADMIN, OFFICER, VIEWER
- GlobalExceptionHandler with Bean Validation (JSR-380)
- Dashboard stats: approval rate, avg risk score, top rejection reason
- EMI calculator endpoint (no auth — embeddable in affordability tools)
- Multi-stage Docker build + docker-compose (Postgres + App)
- AWS ECS task definition with SSM secrets, API Gateway config, ECR push script
- GitHub Actions CI with H2 in-memory test database"

git remote add origin https://github.com/YOUR_USERNAME/creditlens.git
git branch -M main
git push -u origin main
```

---

## Step 4 — Add Repository Topics

On your GitHub repo page, click the gear ⚙️ next to About and add:

`java` `spring-boot` `openai` `banking` `fintech` `credit-scoring` `rule-engine` `postgresql` `flyway` `jwt` `aws` `loan-assessment`

---

## Step 5 — Pin to Your Profile

1. Go to your GitHub profile
2. Click **Customize your pins**
3. Select both `stockstream` and `creditlens`
4. Click **Save pins**

---

## Conventional Commits for Future Changes

```
feat: add co-applicant income support for FOIR recalculation
fix: handle zero collateral value in LTV calculation
docs: add CIBIL integration notes to DESIGN.md
refactor: extract penalty weights to AppProperties
test: add edge case for tenure=6 months EMI calculation
perf: add index on loan_purpose for product-level reporting
chore: upgrade Spring Boot to 2.7.18
```
