package com.creditlens.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.creditlens.config.AppProperties;
import com.creditlens.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class AiExplanationServiceTest {

    @Mock private AppProperties props;
    @Mock private RestTemplate  restTemplate;
    private AiExplanationService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(props.getOpenaiApiKey()).thenReturn("");
        when(props.getMaxFoir()).thenReturn(0.60);
        when(props.getMaxLtv()).thenReturn(0.80);
        when(props.getMinCreditScore()).thenReturn(650);
        service = new AiExplanationService(props, restTemplate, new ObjectMapper());
    }

    @Test
    void explain_approved_returnsNonBlank() {
        String result = service.explain(req(), metrics(Collections.emptyList(), 25), Decision.APPROVED);
        assertThat(result).isNotBlank();
    }

    @Test
    void explain_rejected_isNonBlank() {
        String result = service.explain(req(),
                metrics(Arrays.asList(RiskFlag.HIGH_FOIR, RiskFlag.LOW_CREDIT_SCORE), 75),
                Decision.REJECTED);
        assertThat(result).isNotBlank().hasSizeGreaterThan(50);
    }

    @Test
    void explain_conditional_isNonBlank() {
        String result = service.explain(req(),
                metrics(Collections.singletonList(RiskFlag.HIGH_FOIR), 48),
                Decision.CONDITIONAL_APPROVAL);
        assertThat(result).isNotBlank();
    }

    private LoanApplicationRequest req() {
        return LoanApplicationRequest.builder()
                .applicantName("Priya Sharma").age(32)
                .employmentType(EmploymentType.SALARIED)
                .monthlyIncome(new BigDecimal("80000"))
                .existingEmiPerMonth(new BigDecimal("10000"))
                .creditScore(710)
                .loanAmount(new BigDecimal("1000000")).tenureMonths(60)
                .annualInterestRate(new BigDecimal("11.5"))
                .loanPurpose(LoanPurpose.PERSONAL_LOAN).build();
    }

    private RiskMetrics metrics(java.util.List<RiskFlag> flags, int score) {
        return RiskMetrics.builder()
                .foir(new BigDecimal("0.45")).proposedEmi(new BigDecimal("22000"))
                .flags(flags).rawScore(score).build();
    }
}
