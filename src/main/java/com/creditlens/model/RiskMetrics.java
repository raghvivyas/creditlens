package com.creditlens.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Computed risk metrics for a loan application.
 * All ratios are expressed as decimals (e.g. 0.45 = 45%).
 */
@Data @Builder
public class RiskMetrics {

    /** Fixed Obligation to Income Ratio = (existingEMI + newEMI) / monthlyIncome */
    private BigDecimal foir;

    /** Loan-to-Value Ratio = loanAmount / collateralValue (null if no collateral) */
    private BigDecimal ltv;

    /** Debt-to-Income Ratio = totalDebt / (monthlyIncome * tenureMonths) */
    private BigDecimal dti;

    /** Proposed new EMI calculated from loan amount, rate, and tenure */
    private BigDecimal proposedEmi;

    /** Raw numeric risk score — sum of weighted flag penalties, 0–100 (lower = safer) */
    private int rawScore;

    /** List of fired risk flags */
    private List<RiskFlag> flags;
}
