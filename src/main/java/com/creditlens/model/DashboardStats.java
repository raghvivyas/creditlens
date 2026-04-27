package com.creditlens.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data @Builder
public class DashboardStats {
    private long totalApplications;
    private long approved;
    private long conditionalApproval;
    private long rejected;
    private double approvalRatePct;
    private double rejectionRatePct;
    private BigDecimal avgRiskScore;
    private BigDecimal avgLoanAmount;
    private String topRejectionReason;
}
