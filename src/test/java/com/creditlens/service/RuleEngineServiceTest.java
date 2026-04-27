package com.creditlens.service;

import com.creditlens.config.AppProperties;
import com.creditlens.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEngineServiceTest {

    private RuleEngineService engine;

    @BeforeEach
    void setUp() {
        engine = new RuleEngineService(new AppProperties());
    }

    @Test
    void calculateEmi_standardHomeLoan_returnsCorrectEmi() {
        BigDecimal emi = engine.calculateEmi(
                new BigDecimal("5000000"), new BigDecimal("8.5"), 240);
        assertThat(emi).isBetween(new BigDecimal("43000"), new BigDecimal("44000"));
    }

    @Test
    void calculateEmi_zeroInterest_returnsSimpleDivision() {
        BigDecimal emi = engine.calculateEmi(new BigDecimal("120000"), BigDecimal.ZERO, 12);
        assertThat(emi).isEqualByComparingTo(new BigDecimal("10000.00"));
    }

    @Test
    void evaluate_healthyApplication_noFlags() {
        RiskMetrics metrics = engine.evaluate(buildApp(
                30, 850, "100000", "0", "500000", 60, "10.0", null, LoanPurpose.PERSONAL_LOAN));
        assertThat(metrics.getFlags()).isEmpty();
        assertThat(metrics.getRawScore()).isLessThan(40);
    }

    @Test
    void evaluate_lowCreditScore_firesFlag() {
        RiskMetrics m = engine.evaluate(buildApp(
                30, 580, "80000", "0", "300000", 36, "12.0", null, LoanPurpose.PERSONAL_LOAN));
        assertThat(m.getFlags()).contains(RiskFlag.LOW_CREDIT_SCORE);
    }

    @Test
    void evaluate_highFoir_firesFlag() {
        RiskMetrics m = engine.evaluate(buildApp(
                35, 750, "80000", "50000", "500000", 60, "10.0", null, LoanPurpose.PERSONAL_LOAN));
        assertThat(m.getFlags()).contains(RiskFlag.HIGH_FOIR);
    }

    @Test
    void evaluate_highLtv_firesFlag() {
        RiskMetrics m = engine.evaluate(buildApp(
                40, 700, "150000", "0", "900000", 180, "9.5", "1000000", LoanPurpose.HOME_LOAN));
        assertThat(m.getFlags()).contains(RiskFlag.HIGH_LTV);
    }

    @Test
    void evaluate_underage_firesFlag() {
        RiskMetrics m = engine.evaluate(buildApp(
                19, 720, "50000", "0", "200000", 24, "12.0", null, LoanPurpose.PERSONAL_LOAN));
        assertThat(m.getFlags()).contains(RiskFlag.UNDERAGE);
    }

    @Test void decide_lowScore_approved()     { assertThat(engine.decide(30)).isEqualTo(Decision.APPROVED); }
    @Test void decide_midScore_conditional()  { assertThat(engine.decide(50)).isEqualTo(Decision.CONDITIONAL_APPROVAL); }
    @Test void decide_highScore_rejected()    { assertThat(engine.decide(75)).isEqualTo(Decision.REJECTED); }
    @Test void riskBand_10_isLow()            { assertThat(engine.riskBand(10)).isEqualTo("LOW"); }
    @Test void riskBand_40_isMedium()         { assertThat(engine.riskBand(40)).isEqualTo("MEDIUM"); }
    @Test void riskBand_65_isHigh()           { assertThat(engine.riskBand(65)).isEqualTo("HIGH"); }
    @Test void riskBand_90_isVeryHigh()       { assertThat(engine.riskBand(90)).isEqualTo("VERY_HIGH"); }

    private LoanApplicationRequest buildApp(int age, int cs, String income, String emi,
            String loan, int months, String rate, String collateral, LoanPurpose purpose) {
        return LoanApplicationRequest.builder()
                .applicantName("Test User").age(age)
                .employmentType(EmploymentType.SALARIED)
                .monthlyIncome(new BigDecimal(income))
                .existingEmiPerMonth(new BigDecimal(emi))
                .creditScore(cs)
                .loanAmount(new BigDecimal(loan))
                .tenureMonths(months)
                .annualInterestRate(new BigDecimal(rate))
                .loanPurpose(purpose)
                .collateralValue(collateral != null ? new BigDecimal(collateral) : null)
                .build();
    }
}
