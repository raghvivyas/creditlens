package com.creditlens.controller;

import com.creditlens.model.ApiResponse;
import com.creditlens.service.RuleEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Standalone EMI calculator endpoint — useful for front-end affordability checks
 * before submitting a full loan application.
 */
@RestController
@RequestMapping("/api/calculator")
@RequiredArgsConstructor
public class EmiCalculatorController {

    private final RuleEngineService ruleEngine;

    /**
     * GET /api/calculator/emi?principal=500000&rate=8.5&months=60
     * Returns proposed EMI, total interest, and total payable.
     */
    @GetMapping("/emi")
    public ResponseEntity<ApiResponse<Map<String, Object>>> calculateEmi(
            @RequestParam BigDecimal principal,
            @RequestParam BigDecimal rate,
            @RequestParam @Min(6) @Max(360) int months) {

        BigDecimal emi          = ruleEngine.calculateEmi(principal, rate, months);
        BigDecimal totalPayable = emi.multiply(BigDecimal.valueOf(months))
                                     .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalInterest = totalPayable.subtract(principal)
                                               .setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("principal",     principal);
        result.put("annualRatePct", rate);
        result.put("tenureMonths",  months);
        result.put("monthlyEmi",    emi);
        result.put("totalPayable",  totalPayable);
        result.put("totalInterest", totalInterest);

        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
