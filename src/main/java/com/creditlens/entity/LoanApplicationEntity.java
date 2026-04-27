package com.creditlens.entity;

import com.creditlens.model.Decision;
import com.creditlens.model.EmploymentType;
import com.creditlens.model.LoanPurpose;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "loan_applications",
       indexes = {
           @Index(name = "idx_loan_decision",     columnList = "decision"),
           @Index(name = "idx_loan_assessed_at",  columnList = "assessed_at DESC"),
           @Index(name = "idx_loan_applicant",    columnList = "applicant_name")
       })
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LoanApplicationEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "applicant_name", nullable = false, length = 150)
    private String applicantName;

    @Column(name = "age", nullable = false)
    private int age;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 30)
    private EmploymentType employmentType;

    @Column(name = "monthly_income", nullable = false, precision = 15, scale = 2)
    private BigDecimal monthlyIncome;

    @Column(name = "existing_emi_per_month", nullable = false, precision = 15, scale = 2)
    private BigDecimal existingEmiPerMonth;

    @Column(name = "credit_score", nullable = false)
    private int creditScore;

    @Column(name = "loan_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal loanAmount;

    @Column(name = "tenure_months", nullable = false)
    private int tenureMonths;

    @Column(name = "annual_interest_rate", nullable = false, precision = 6, scale = 2)
    private BigDecimal annualInterestRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_purpose", nullable = false, length = 30)
    private LoanPurpose loanPurpose;

    @Column(name = "collateral_value", precision = 15, scale = 2)
    private BigDecimal collateralValue;

    @Column(name = "officer_remarks", columnDefinition = "TEXT")
    private String officerRemarks;

    // ── Computed results ────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 30)
    private Decision decision;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Column(name = "risk_band", length = 20)
    private String riskBand;

    @Column(name = "foir", precision = 6, scale = 4)
    private BigDecimal foir;

    @Column(name = "ltv", precision = 6, scale = 4)
    private BigDecimal ltv;

    @Column(name = "dti", precision = 6, scale = 4)
    private BigDecimal dti;

    @Column(name = "proposed_emi", precision = 15, scale = 2)
    private BigDecimal proposedEmi;

    @Column(name = "flag_descriptions", columnDefinition = "TEXT")
    private String flagDescriptions;   // comma-delimited

    @Column(name = "ai_explanation", columnDefinition = "TEXT")
    private String aiExplanation;

    @Column(name = "conditions", columnDefinition = "TEXT")
    private String conditions;         // comma-delimited

    @Column(name = "assessed_at")
    private Instant assessedAt;

    @Column(name = "assessed_by", length = 50)
    private String assessedBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt   = Instant.now();
        assessedAt  = Instant.now();
    }
}
