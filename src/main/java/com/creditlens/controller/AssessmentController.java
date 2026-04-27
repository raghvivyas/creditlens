package com.creditlens.controller;

import com.creditlens.model.*;
import com.creditlens.service.AssessmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.Instant;

@RestController
@RequestMapping("/api/assessments")
@RequiredArgsConstructor
public class AssessmentController {

    private final AssessmentService assessmentService;

    /**
     * POST /api/assessments
     * Submit a loan application for AI-powered risk assessment.
     *
     * The authenticated user's username is recorded as the assessing officer
     * in the audit trail.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<AssessmentResponse>> assess(
            @Valid @RequestBody LoanApplicationRequest request,
            Authentication auth) {
        String officer = auth != null ? auth.getName() : "system";
        AssessmentResponse result = assessmentService.assess(request, officer);
        return ResponseEntity.ok(ApiResponse.ok("Assessment complete", result));
    }

    /**
     * GET /api/assessments/{id}
     * Retrieve a specific assessment by its ID (full audit record).
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AssessmentResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(assessmentService.getById(id)));
    }

    /**
     * GET /api/assessments?decision=REJECTED&page=0&size=20&from=&to=
     * List assessments with optional filters (decision, date range, pagination).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AssessmentResponse>>> list(
            @RequestParam(required = false) Decision decision,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(
                page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "assessedAt"));

        Page<AssessmentResponse> results =
                assessmentService.list(decision, from, to, pageable);
        return ResponseEntity.ok(ApiResponse.ok(results));
    }

    /**
     * GET /api/assessments/dashboard
     * Aggregate stats: approval rate, average risk score, top rejection reason.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardStats>> dashboard() {
        return ResponseEntity.ok(ApiResponse.ok(assessmentService.dashboardStats()));
    }
}
