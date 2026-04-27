package com.creditlens.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.creditlens.model.*;
import com.creditlens.security.JwtAuthFilter;
import com.creditlens.security.JwtTokenProvider;
import com.creditlens.security.UserDetailsServiceImpl;
import com.creditlens.service.AssessmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AssessmentController.class)
class AssessmentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AssessmentService assessmentService;
    @MockBean private JwtAuthFilter jwtAuthFilter;
    @MockBean private UserDetailsServiceImpl userDetailsService;
    @MockBean private JwtTokenProvider jwtTokenProvider;

    @Test
    @WithMockUser(username = "officer", roles = {"OFFICER"})
    void assess_validRequest_returns200WithDecision() throws Exception {
        AssessmentResponse response = AssessmentResponse.builder()
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
                .aiExplanation("The application demonstrates strong repayment capacity.")
                .conditions(Collections.emptyList())
                .assessedAt(Instant.now())
                .build();

        when(assessmentService.assess(any(LoanApplicationRequest.class), anyString()))
                .thenReturn(response);

        LoanApplicationRequest req = LoanApplicationRequest.builder()
                .applicantName("Rahul Verma").age(32)
                .employmentType(EmploymentType.SALARIED)
                .monthlyIncome(new BigDecimal("80000"))
                .existingEmiPerMonth(new BigDecimal("5000"))
                .creditScore(750)
                .loanAmount(new BigDecimal("800000")).tenureMonths(60)
                .annualInterestRate(new BigDecimal("10.5"))
                .loanPurpose(LoanPurpose.PERSONAL_LOAN)
                .build();

        mockMvc.perform(post("/api/assessments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.decision").value("APPROVED"))
                .andExpect(jsonPath("$.data.riskScore").value(28));
    }

    @Test
    @WithMockUser
    void assess_missingApplicantName_returns400() throws Exception {
        LoanApplicationRequest req = LoanApplicationRequest.builder()
                .age(30).employmentType(EmploymentType.SALARIED)
                .monthlyIncome(new BigDecimal("80000"))
                .existingEmiPerMonth(BigDecimal.ZERO)
                .creditScore(720).loanAmount(new BigDecimal("500000"))
                .tenureMonths(48).annualInterestRate(new BigDecimal("11.0"))
                .loanPurpose(LoanPurpose.PERSONAL_LOAN)
                .build();

        mockMvc.perform(post("/api/assessments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void getById_nonExistent_returns404() throws Exception {
        when(assessmentService.getById(999L))
                .thenThrow(new com.creditlens.exception.ResourceNotFoundException("Assessment not found with id: 999"));

        mockMvc.perform(get("/api/assessments/999"))
                .andExpect(status().isNotFound());
    }
}
