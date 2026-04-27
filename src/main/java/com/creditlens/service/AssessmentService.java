package com.creditlens.service;

import com.creditlens.entity.LoanApplicationEntity;
import com.creditlens.model.*;
import com.creditlens.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Orchestrates the full loan assessment pipeline:
 *   1. RuleEngineService → compute metrics and fire risk rules
 *   2. Determine Decision from risk score
 *   3. AiExplanationService → enrich with natural-language explanation
 *   4. Persist full audit record to PostgreSQL
 *   5. Return a structured AssessmentResponse
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssessmentService {

    private final RuleEngineService         ruleEngine;
    private final AiExplanationService      aiExplanation;
    private final LoanApplicationRepository repo;

    @Transactional
    public AssessmentResponse assess(LoanApplicationRequest request, String assessedBy) {
        log.info("Starting assessment for applicant='{}' loan=₹{} purpose={}",
                request.getApplicantName(), request.getLoanAmount(), request.getLoanPurpose());

        // Step 1: Rule engine
        RiskMetrics metrics  = ruleEngine.evaluate(request);
        Decision    decision = ruleEngine.decide(metrics.getRawScore());
        String      band     = ruleEngine.riskBand(metrics.getRawScore());

        List<String> flagDescs  = ruleEngine.flagDescriptions(metrics.getFlags());
        List<String> conditions = Decision.CONDITIONAL_APPROVAL.equals(decision)
                                  ? ruleEngine.conditions(metrics.getFlags())
                                  : java.util.Collections.emptyList();

        // Step 2: AI explanation
        String explanation = aiExplanation.explain(request, metrics, decision);

        // Step 3: Persist
        LoanApplicationEntity entity = LoanApplicationEntity.builder()
                .applicantName(request.getApplicantName())
                .age(request.getAge())
                .employmentType(request.getEmploymentType())
                .monthlyIncome(request.getMonthlyIncome())
                .existingEmiPerMonth(request.getExistingEmiPerMonth())
                .creditScore(request.getCreditScore())
                .loanAmount(request.getLoanAmount())
                .tenureMonths(request.getTenureMonths())
                .annualInterestRate(request.getAnnualInterestRate())
                .loanPurpose(request.getLoanPurpose())
                .collateralValue(request.getCollateralValue())
                .officerRemarks(request.getOfficerRemarks())
                .decision(decision)
                .riskScore(metrics.getRawScore())
                .riskBand(band)
                .foir(metrics.getFoir())
                .ltv(metrics.getLtv())
                .dti(metrics.getDti())
                .proposedEmi(metrics.getProposedEmi())
                .flagDescriptions(String.join(",", flagDescs))
                .aiExplanation(explanation)
                .conditions(String.join(",", conditions))
                .assessedBy(assessedBy)
                .build();

        entity = repo.save(entity);

        log.info("Assessment complete: id={} applicant='{}' decision={} score={}",
                entity.getId(), entity.getApplicantName(), decision, metrics.getRawScore());

        return buildResponse(entity, flagDescs, conditions);
    }

    @Transactional(readOnly = true)
    public AssessmentResponse getById(Long id) {
        LoanApplicationEntity entity = repo.findById(id)
                .orElseThrow(() -> new com.creditlens.exception.ResourceNotFoundException(
                        "Assessment not found with id: " + id));
        List<String> flags = splitCsv(entity.getFlagDescriptions());
        List<String> conds = splitCsv(entity.getConditions());
        return buildResponse(entity, flags, conds);
    }

    @Transactional(readOnly = true)
    public Page<AssessmentResponse> list(Decision decision, Instant from, Instant to,
                                         Pageable pageable) {
        Page<LoanApplicationEntity> page = repo.findWithFilters(decision, from, to, pageable);
        return page.map(e -> buildResponse(e, splitCsv(e.getFlagDescriptions()), splitCsv(e.getConditions())));
    }

    @Transactional(readOnly = true)
    public DashboardStats dashboardStats() {
        long total    = repo.count();
        long approved = repo.countByDecision(Decision.APPROVED);
        long cond     = repo.countByDecision(Decision.CONDITIONAL_APPROVAL);
        long rejected = repo.countByDecision(Decision.REJECTED);

        List<Object[]> topFlag = repo.findTopRejectionFlag();
        String topRejection = topFlag.isEmpty() ? "N/A" : topFlag.get(0)[0].toString();

        double approvalRate   = total == 0 ? 0 : (approved  + cond)  * 100.0 / total;
        double rejectionRate  = total == 0 ? 0 : rejected * 100.0 / total;

        return DashboardStats.builder()
                .totalApplications(total)
                .approved(approved)
                .conditionalApproval(cond)
                .rejected(rejected)
                .approvalRatePct(Math.round(approvalRate * 10.0) / 10.0)
                .rejectionRatePct(Math.round(rejectionRate * 10.0) / 10.0)
                .avgRiskScore(repo.findAvgRiskScore())
                .avgLoanAmount(repo.findAvgLoanAmount())
                .topRejectionReason(topRejection)
                .build();
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private AssessmentResponse buildResponse(LoanApplicationEntity e,
                                              List<String> flags, List<String> conds) {
        String foirPct = e.getFoir()  != null
                ? e.getFoir().multiply(new java.math.BigDecimal("100"))
                              .setScale(1, java.math.RoundingMode.HALF_UP) + "%" : "N/A";
        String ltvPct  = e.getLtv()   != null
                ? e.getLtv().multiply(new java.math.BigDecimal("100"))
                              .setScale(1, java.math.RoundingMode.HALF_UP) + "%" : "N/A";
        String dtiPct  = e.getDti()   != null
                ? e.getDti().multiply(new java.math.BigDecimal("100"))
                              .setScale(1, java.math.RoundingMode.HALF_UP) + "%" : "N/A";

        return AssessmentResponse.builder()
                .assessmentId(e.getId())
                .applicantName(e.getApplicantName())
                .decision(e.getDecision())
                .riskScore(e.getRiskScore())
                .riskBand(e.getRiskBand())
                .foirPct(foirPct)
                .ltvPct(ltvPct)
                .dtiPct(dtiPct)
                .proposedEmi(e.getProposedEmi())
                .flagDescriptions(flags)
                .aiExplanation(e.getAiExplanation())
                .conditions(conds)
                .assessedAt(e.getAssessedAt())
                .build();
    }

    private List<String> splitCsv(String csv) {
        if (csv == null || csv.trim().isEmpty()) return java.util.Collections.emptyList();
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
