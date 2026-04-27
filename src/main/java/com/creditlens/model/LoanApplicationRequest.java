package com.creditlens.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.*;
import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LoanApplicationRequest {

    @NotBlank(message = "Applicant name is required")
    private String applicantName;

    @Min(value = 18, message = "Applicant must be at least 18 years old")
    @Max(value = 80, message = "Applicant age cannot exceed 80")
    private int age;

    @NotNull(message = "Employment type is required")
    private EmploymentType employmentType;

    /** Monthly gross income in INR */
    @NotNull
    @DecimalMin(value = "0.01", message = "Monthly income must be positive")
    private BigDecimal monthlyIncome;

    /** Sum of all existing monthly EMIs before this loan */
    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal existingEmiPerMonth;

    /** Credit score (300–900 CIBIL scale) */
    @Min(300) @Max(900)
    private int creditScore;

    /** Requested loan amount in INR */
    @NotNull
    @DecimalMin(value = "10000.00", message = "Minimum loan amount is ₹10,000")
    private BigDecimal loanAmount;

    /** Loan tenure in months */
    @Min(value = 6,   message = "Minimum tenure is 6 months")
    @Max(value = 360, message = "Maximum tenure is 360 months (30 years)")
    private int tenureMonths;

    /** Annual interest rate as a percentage (e.g., 8.5 for 8.5%) */
    @DecimalMin("1.0") @DecimalMax("36.0")
    private BigDecimal annualInterestRate;

    @NotNull(message = "Loan purpose is required")
    private LoanPurpose loanPurpose;

    /**
     * Current market value of the collateral / property being pledged.
     * Optional — only required for HOME_LOAN and LAP.
     */
    private BigDecimal collateralValue;

    /** Free-text remarks from the bank officer (passed to AI for context) */
    private String officerRemarks;
}
