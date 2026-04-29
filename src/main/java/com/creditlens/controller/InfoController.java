package com.creditlens.controller;

import com.creditlens.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InfoController {

    private final AppProperties props;

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        Map<String, Object> thresholds = new LinkedHashMap<>();
        thresholds.put("maxFoir",        props.getMaxFoir());
        thresholds.put("maxLtv",         props.getMaxLtv());
        thresholds.put("maxDti",         props.getMaxDti());
        thresholds.put("minCreditScore", props.getMinCreditScore());
        thresholds.put("minAge",         props.getMinAge());
        thresholds.put("maxAge",         props.getMaxAge());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("app",            "CreditLens");
        m.put("version",        "1.0.0");
        m.put("aiEnabled",      props.getOpenaiApiKey() != null
                                && !props.getOpenaiApiKey().trim().isEmpty());
        m.put("riskThresholds", thresholds);
        m.put("timestamp",      Instant.now());
        return ResponseEntity.ok(m);
    }
}
