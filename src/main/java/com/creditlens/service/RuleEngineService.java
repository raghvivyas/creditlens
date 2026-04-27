package com.creditlens.service;

import com.creditlens.config.AppProperties;
import com.creditlens.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Deterministic rule engine that evaluates a loan application against
 * configurable risk thresholds using Java 8 functional interfaces.
 *
 * Each RiskRule is a Function<LoanApplicationRequest, RiskFlag> — a pure
 * function that examines one facet of the application and returns either
 * RiskFlag.CLEAR or a specific flag. Rules are composed by streaming them
 * over the application and collecting non-CLEAR flags.
 *
 * Risk score computation:
 *   - Base score: 20 (everyone starts with some inherent risk)
 *   - Each flag adds a configurable penalty
 *   - Score is clamped to [0, 100]
 *
 * This mirrors the credit underwriting models used at Bank of Baroda and
 * most PSU banks: deterministic eligibility gates first, then AI enrichment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleEngineService {

    private final AppProperties props;

    // ── Rule definitions ────────────────────────────────────────────────────

    /**
     * A rule is a Function that takes the application and computed metrics,
     * and returns a RiskFlag. We use a small inner class to bundle both.
     */
    @FunctionalInterface
    private interface RiskRule {
        RiskFlag evaluate(LoanApplicationRequest app, RiskMetrics metrics);
    }

    private List<RiskRule> buildRules() {
        return Arrays.asList(
            // Age gates
            (app, m) -> app.getAge() < props.getMinAge()
                        ? RiskFlag.UNDERAGE : RiskFlag.CLEAR,
            (app, m) -> app.getAge() > props.getMaxAge()
                        ? RiskFlag.OVERAGE  : RiskFlag.CLEAR,

            // FOIR: (existingEMI + proposedEMI) / monthlyIncome > threshold
            (app, m) -> m.getFoir() != null &&
                        m.getFoir().compareTo(BigDecimal.valueOf(props.getMaxFoir())) > 0
                        ? RiskFlag.HIGH_FOIR : RiskFlag.CLEAR,

            // LTV: loanAmount / collateralValue > threshold (only for secured loans)
            (app, m) -> m.getLtv() != null &&
                        m.getLtv().compareTo(BigDecimal.valueOf(props.getMaxLtv())) > 0
                        ? RiskFlag.HIGH_LTV : RiskFlag.CLEAR,

            // DTI: total debt / annual income > threshold
            (app, m) -> m.getDti() != null &&
                        m.getDti().compareTo(BigDecimal.valueOf(props.getMaxDti())) > 0
                        ? RiskFlag.HIGH_DTI : RiskFlag.CLEAR,

            // Credit score
            (app, m) -> app.getCreditScore() < props.getMinCreditScore()
                        ? RiskFlag.LOW_CREDIT_SCORE : RiskFlag.CLEAR,

            // Minimum income sanity check (proposed EMI must not exceed gross income)
            (app, m) -> m.getProposedEmi() != null &&
                        m.getProposedEmi().compareTo(app.getMonthlyIncome()) >= 0
                        ? RiskFlag.INSUFFICIENT_INCOME : RiskFlag.CLEAR,

            // Loan amount cap: max 60× monthly income for personal loans
            (app, m) -> app.getLoanPurpose() == LoanPurpose.PERSONAL_LOAN &&
                        app.getLoanAmount().compareTo(app.getMonthlyIncome().multiply(new BigDecimal("60"))) > 0
                        ? RiskFlag.EXCESSIVE_LOAN_AMOUNT : RiskFlag.CLEAR
        );
    }

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Computes derived metrics (FOIR, LTV, DTI, proposed EMI) and runs all
     * risk rules, returning a fully populated RiskMetrics object.
     */
    public RiskMetrics evaluate(LoanApplicationRequest app) {
        BigDecimal proposedEmi = calculateEmi(
                app.getLoanAmount(), app.getAnnualInterestRate(), app.getTenureMonths());

        BigDecimal foir = calculateFoir(
                app.getMonthlyIncome(), app.getExistingEmiPerMonth(), proposedEmi);

        BigDecimal ltv = calculateLtv(app.getLoanAmount(), app.getCollateralValue());

        BigDecimal dti = calculateDti(
                app.getLoanAmount(), app.getExistingEmiPerMonth(),
                app.getMonthlyIncome(), app.getTenureMonths());

        // Collect all fired flags (Java 8 stream + functional rules)
        RiskMetrics partialMetrics = RiskMetrics.builder()
                .foir(foir).ltv(ltv).dti(dti).proposedEmi(proposedEmi)
                .build();

        List<RiskFlag> flags = buildRules().stream()
                .map(rule -> rule.evaluate(app, partialMetrics))
                .filter(flag -> flag != RiskFlag.CLEAR)
                .collect(Collectors.toList());

        int rawScore = computeRawScore(flags);

        return RiskMetrics.builder()
                .foir(foir)
                .ltv(ltv)
                .dti(dti)
                .proposedEmi(proposedEmi)
                .rawScore(rawScore)
                .flags(flags)
                .build();
    }

    /**
     * Maps risk score to a Decision.
     * Score 0–39  → APPROVED
     * Score 40–59 → CONDITIONAL_APPROVAL
     * Score 60+   → REJECTED
     */
    public Decision decide(int riskScore) {
        if (riskScore < 40)  return Decision.APPROVED;
        if (riskScore < 60)  return Decision.CONDITIONAL_APPROVAL;
        return Decision.REJECTED;
    }

    /** Maps numeric score to a human-readable band. */
    public String riskBand(int score) {
        if (score < 25)  return "LOW";
        if (score < 50)  return "MEDIUM";
        if (score < 75)  return "HIGH";
        return "VERY_HIGH";
    }

    /** Converts a list of flags to readable descriptions. */
    public List<String> flagDescriptions(List<RiskFlag> flags) {
        return flags.stream().map(this::describe).collect(Collectors.toList());
    }

    /** Suggests conditions for CONDITIONAL_APPROVAL based on active flags. */
    public List<String> conditions(List<RiskFlag> flags) {
        List<String> conds = new ArrayList<>();
        if (flags.contains(RiskFlag.HIGH_FOIR)) {
            conds.add("Co-applicant with independent income required to reduce FOIR below " +
                      (int)(props.getMaxFoir() * 100) + "%");
        }
        if (flags.contains(RiskFlag.HIGH_LTV)) {
            conds.add("Additional collateral or higher down payment required to reduce LTV below " +
                      (int)(props.getMaxLtv() * 100) + "%");
        }
        if (flags.contains(RiskFlag.LOW_CREDIT_SCORE)) {
            conds.add("Credit score must improve to at least " + props.getMinCreditScore() +
                      " before disbursement");
        }
        if (flags.contains(RiskFlag.HIGH_DTI)) {
            conds.add("Existing debts must be partially repaid before loan can be processed");
        }
        return conds;
    }

    // ── Financial calculations ──────────────────────────────────────────────

    /**
     * Standard EMI formula:
     * EMI = P × r × (1+r)^n / ((1+r)^n − 1)
     * where r = monthly interest rate, n = number of months
     */
    public BigDecimal calculateEmi(BigDecimal principal, BigDecimal annualRatePct, int months) {
        if (principal == null || annualRatePct == null || months <= 0) return BigDecimal.ZERO;

        double r = annualRatePct.doubleValue() / 100.0 / 12.0;
        if (r == 0) {
            // Zero interest: simple division
            return principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        }
        double pow = Math.pow(1 + r, months);
        double emi = principal.doubleValue() * r * pow / (pow - 1);
        return BigDecimal.valueOf(emi).setScale(2, RoundingMode.HALF_UP);
    }

    /** FOIR = (existingEMI + proposedEMI) / monthlyIncome */
    private BigDecimal calculateFoir(BigDecimal income, BigDecimal existingEmi, BigDecimal newEmi) {
        if (income == null || income.compareTo(BigDecimal.ZERO) == 0) return null;
        BigDecimal totalObligations = existingEmi.add(newEmi);
        return totalObligations.divide(income, 4, RoundingMode.HALF_UP);
    }

    /** LTV = loanAmount / collateralValue (null if no collateral) */
    private BigDecimal calculateLtv(BigDecimal loanAmount, BigDecimal collateralValue) {
        if (collateralValue == null || collateralValue.compareTo(BigDecimal.ZERO) == 0) return null;
        return loanAmount.divide(collateralValue, 4, RoundingMode.HALF_UP);
    }

    /**
     * DTI = totalDebt / totalGrossIncome over the loan tenure
     * totalDebt = loanAmount + (existingEMI × tenureMonths)
     * totalGrossIncome = monthlyIncome × tenureMonths
     */
    private BigDecimal calculateDti(BigDecimal loanAmount, BigDecimal existingEmi,
                                    BigDecimal monthlyIncome, int tenureMonths) {
        if (monthlyIncome == null || monthlyIncome.compareTo(BigDecimal.ZERO) == 0) return null;
        BigDecimal totalDebt   = loanAmount.add(existingEmi.multiply(BigDecimal.valueOf(tenureMonths)));
        BigDecimal totalIncome = monthlyIncome.multiply(BigDecimal.valueOf(tenureMonths));
        return totalDebt.divide(totalIncome, 4, RoundingMode.HALF_UP);
    }

    /** Score = 20 (base) + weighted sum of flag penalties, clamped to 100 */
    private int computeRawScore(List<RiskFlag> flags) {
        int score = 20;
        for (RiskFlag flag : flags) {
            score += penaltyFor(flag);
        }
        return Math.min(score, 100);
    }

    private int penaltyFor(RiskFlag flag) {
        switch (flag) {
            case HIGH_FOIR:             return 25;
            case LOW_CREDIT_SCORE:      return 25;
            case HIGH_LTV:              return 20;
            case HIGH_DTI:              return 20;
            case INSUFFICIENT_INCOME:   return 30;
            case EXCESSIVE_LOAN_AMOUNT: return 15;
            case UNDERAGE:
            case OVERAGE:               return 40;
            default:                    return 0;
        }
    }

    private String describe(RiskFlag flag) {
        switch (flag) {
            case HIGH_FOIR:             return "FOIR exceeds maximum allowed threshold of " + (int)(props.getMaxFoir()*100) + "%";
            case HIGH_LTV:              return "Loan-to-Value ratio exceeds " + (int)(props.getMaxLtv()*100) + "% of collateral value";
            case HIGH_DTI:              return "Debt-to-Income ratio indicates excessive debt burden";
            case LOW_CREDIT_SCORE:      return "CIBIL credit score below minimum required threshold of " + props.getMinCreditScore();
            case UNDERAGE:              return "Applicant is below minimum eligible age of " + props.getMinAge();
            case OVERAGE:               return "Applicant exceeds maximum eligible age of " + props.getMaxAge();
            case INSUFFICIENT_INCOME:   return "Monthly income insufficient to service proposed EMI";
            case EXCESSIVE_LOAN_AMOUNT: return "Loan amount exceeds 60× monthly income limit for personal loans";
            default:                    return flag.name();
        }
    }
}
