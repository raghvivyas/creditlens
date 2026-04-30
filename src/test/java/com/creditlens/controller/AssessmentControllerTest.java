package com.creditlens.controller;

import com.creditlens.exception.ResourceNotFoundException;
import com.creditlens.model.*;
import com.creditlens.service.AssessmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit test for AssessmentController.
 * No Spring context, no MockMvc, no security filter chain.
 * Calls controller methods directly as plain Java.
 */
class AssessmentControllerTest {

    @Mock
    private AssessmentService assessmentService;

    @InjectMocks
    private AssessmentController controller;

    private Authentication auth;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("officer");
    }

    // ── assess() ──────────────────────────────────────────────────────────────

    @Test
    void assess_validRequest_returns200WithApprovedDecision() {
        AssessmentResponse mockResponse = AssessmentResponse.builder()
                .assessmentId(1L)
                .applicantName("Rahul Verma")
                .decision(Decision.APPROVED)
                .riskScore(28)
                .riskBand("LOW")
                .foirPct("38.2%")
                .ltvPct("N/A")
                .dtiPct("31.5%")
                .proposedEmi(new BigDecimal("18500"))
                .flagDescriptions(Collections.emptyList())
                .aiExplanation("Strong repayment capacity.")
                .conditions(Collections.emptyList())
                .assessedAt(Instant.now())
                .build();

        when(assessmentService.assess(any(LoanApplicationRequest.class), anyString()))
                .thenReturn(mockResponse);

        LoanApplicationRequest req = buildValidRequest();
        ResponseEntity<ApiResponse<AssessmentResponse>> response = controller.assess(req, auth);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().getDecision()).isEqualTo(Decision.APPROVED);
        assertThat(response.getBody().getData().getRiskScore()).isEqualTo(28);
        assertThat(response.getBody().getData().getRiskBand()).isEqualTo("LOW");
    }

    @Test
    void assess_rejectedApplication_returnsRejectedDecision() {
        AssessmentResponse mockResponse = AssessmentResponse.builder()
                .assessmentId(2L)
                .applicantName("Test User")
                .decision(Decision.REJECTED)
                .riskScore(80)
                .riskBand("VERY_HIGH")
                .foirPct("78.5%")
                .flagDescriptions(Collections.singletonList("FOIR exceeds maximum allowed threshold of 60%"))
                .conditions(Collections.emptyList())
                .assessedAt(Instant.now())
                .build();

        when(assessmentService.assess(any(LoanApplicationRequest.class), anyString()))
                .thenReturn(mockResponse);

        ResponseEntity<ApiResponse<AssessmentResponse>> response =
                controller.assess(buildValidRequest(), auth);

        assertThat(response.getBody().getData().getDecision()).isEqualTo(Decision.REJECTED);
        assertThat(response.getBody().getData().getRiskScore()).isEqualTo(80);
        assertThat(response.getBody().getData().getFlagDescriptions()).hasSize(1);
    }

    @Test
    void assess_conditionalApproval_returnsConditions() {
        AssessmentResponse mockResponse = AssessmentResponse.builder()
                .assessmentId(3L)
                .applicantName("Test User")
                .decision(Decision.CONDITIONAL_APPROVAL)
                .riskScore(50)
                .riskBand("MEDIUM")
                .foirPct("62.1%")
                .flagDescriptions(Collections.singletonList("FOIR exceeds maximum allowed threshold of 60%"))
                .conditions(Collections.singletonList("Co-applicant with independent income required"))
                .assessedAt(Instant.now())
                .build();

        when(assessmentService.assess(any(LoanApplicationRequest.class), anyString()))
                .thenReturn(mockResponse);

        ResponseEntity<ApiResponse<AssessmentResponse>> response =
                controller.assess(buildValidRequest(), auth);

        assertThat(response.getBody().getData().getDecision()).isEqualTo(Decision.CONDITIONAL_APPROVAL);
        assertThat(response.getBody().getData().getConditions()).isNotEmpty();
    }

    @Test
    void assess_nullAuth_usesSystemAsOfficer() {
        when(assessmentService.assess(any(), anyString())).thenReturn(
                AssessmentResponse.builder().assessmentId(1L).decision(Decision.APPROVED)
                        .riskScore(25).assessedAt(Instant.now())
                        .flagDescriptions(Collections.emptyList())
                        .conditions(Collections.emptyList()).build());

        // auth = null should not throw, officer defaults to "system"
        ResponseEntity<ApiResponse<AssessmentResponse>> response =
                controller.assess(buildValidRequest(), null);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
    }

    // ── getById() ─────────────────────────────────────────────────────────────

    @Test
    void getById_existingId_returnsAssessment() {
        AssessmentResponse mockResponse = AssessmentResponse.builder()
                .assessmentId(42L)
                .applicantName("Priya Sharma")
                .decision(Decision.APPROVED)
                .riskScore(30)
                .assessedAt(Instant.now())
                .flagDescriptions(Collections.emptyList())
                .conditions(Collections.emptyList())
                .build();

        when(assessmentService.getById(42L)).thenReturn(mockResponse);

        ResponseEntity<ApiResponse<AssessmentResponse>> response = controller.getById(42L);

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody().getData().getAssessmentId()).isEqualTo(42L);
        assertThat(response.getBody().getData().getApplicantName()).isEqualTo("Priya Sharma");
    }

    @Test
    void getById_nonExistentId_throwsResourceNotFoundException() {
        when(assessmentService.getById(999L))
                .thenThrow(new ResourceNotFoundException("Assessment not found with id: 999"));

        assertThatThrownBy(() -> controller.getById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    // ── dashboard() ───────────────────────────────────────────────────────────

    @Test
    void dashboard_returnsStats() {
        DashboardStats stats = DashboardStats.builder()
                .totalApplications(100L)
                .approved(70L)
                .conditionalApproval(15L)
                .rejected(15L)
                .approvalRatePct(85.0)
                .rejectionRatePct(15.0)
                .avgRiskScore(new BigDecimal("32.5"))
                .avgLoanAmount(new BigDecimal("850000"))
                .topRejectionReason("FOIR exceeds maximum allowed threshold of 60%")
                .build();

        when(assessmentService.dashboardStats()).thenReturn(stats);

        ResponseEntity<ApiResponse<DashboardStats>> response = controller.dashboard();

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody().getData().getTotalApplications()).isEqualTo(100L);
        assertThat(response.getBody().getData().getApproved()).isEqualTo(70L);
        assertThat(response.getBody().getData().getApprovalRatePct()).isEqualTo(85.0);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private LoanApplicationRequest buildValidRequest() {
        return LoanApplicationRequest.builder()
                .applicantName("Rahul Verma")
                .age(32)
                .employmentType(EmploymentType.SALARIED)
                .monthlyIncome(new BigDecimal("80000"))
                .existingEmiPerMonth(new BigDecimal("5000"))
                .creditScore(750)
                .loanAmount(new BigDecimal("800000"))
                .tenureMonths(60)
                .annualInterestRate(new BigDecimal("10.5"))
                .loanPurpose(LoanPurpose.PERSONAL_LOAN)
                .build();
    }
}
