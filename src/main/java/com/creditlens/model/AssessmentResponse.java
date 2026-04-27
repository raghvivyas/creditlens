package com.creditlens.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AssessmentResponse {

    private Long   assessmentId;
    private String applicantName;
    private Decision decision;

    /** 0–100, lower = safer */
    private int riskScore;

    /** Human-readable risk band */
    private String riskBand;        // LOW / MEDIUM / HIGH / VERY_HIGH

    /** FOIR expressed as a percentage string, e.g. "58.3%" */
    private String foirPct;
    private String ltvPct;
    private String dtiPct;
    private BigDecimal proposedEmi;

    private List<String> flagDescriptions;

    /** AI-generated natural-language explanation */
    private String aiExplanation;

    /** Conditions attached when decision = CONDITIONAL_APPROVAL */
    private List<String> conditions;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant assessedAt;
}
