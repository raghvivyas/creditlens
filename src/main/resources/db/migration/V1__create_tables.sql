-- ============================================================
-- CreditLens Database Schema
-- V1 — Initial tables: loan_applications, users
-- ============================================================

CREATE TABLE IF NOT EXISTS loan_applications (
    id                    BIGSERIAL     PRIMARY KEY,

    -- Application inputs
    applicant_name        VARCHAR(150)  NOT NULL,
    age                   INTEGER       NOT NULL,
    employment_type       VARCHAR(30)   NOT NULL,
    monthly_income        NUMERIC(15,2) NOT NULL,
    existing_emi_per_month NUMERIC(15,2) NOT NULL DEFAULT 0,
    credit_score          INTEGER       NOT NULL,
    loan_amount           NUMERIC(15,2) NOT NULL,
    tenure_months         INTEGER       NOT NULL,
    annual_interest_rate  NUMERIC(6,2)  NOT NULL,
    loan_purpose          VARCHAR(30)   NOT NULL,
    collateral_value      NUMERIC(15,2),
    officer_remarks       TEXT,

    -- Assessment outputs
    decision              VARCHAR(30)   NOT NULL,
    risk_score            INTEGER       NOT NULL,
    risk_band             VARCHAR(20),
    foir                  NUMERIC(6,4),
    ltv                   NUMERIC(6,4),
    dti                   NUMERIC(6,4),
    proposed_emi          NUMERIC(15,2),
    flag_descriptions     TEXT,
    ai_explanation        TEXT,
    conditions            TEXT,
    assessed_by           VARCHAR(50),
    assessed_at           TIMESTAMPTZ   DEFAULT NOW(),
    created_at            TIMESTAMPTZ   DEFAULT NOW(),

    CONSTRAINT chk_risk_score    CHECK (risk_score BETWEEN 0 AND 100),
    CONSTRAINT chk_credit_score  CHECK (credit_score BETWEEN 300 AND 900),
    CONSTRAINT chk_decision      CHECK (decision IN ('APPROVED','CONDITIONAL_APPROVAL','REJECTED'))
);

CREATE INDEX idx_loan_decision    ON loan_applications (decision);
CREATE INDEX idx_loan_assessed_at ON loan_applications (assessed_at DESC);
CREATE INDEX idx_loan_applicant   ON loan_applications (applicant_name);
CREATE INDEX idx_loan_purpose     ON loan_applications (loan_purpose);

-- ────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS users (
    id         BIGSERIAL    PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(20)  NOT NULL DEFAULT 'OFFICER',
    created_at TIMESTAMPTZ  DEFAULT NOW(),

    CONSTRAINT chk_role CHECK (role IN ('ADMIN','OFFICER','VIEWER'))
);
