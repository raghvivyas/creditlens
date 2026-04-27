package com.creditlens.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "creditlens")
public class AppProperties {

    private String jwtSecret = "creditlens-default-secret-change-in-production-256bits!!";
    private long   jwtExpirationMs = 86400000L;
    private String openaiApiKey    = "";
    private String openaiModel     = "gpt-4";
    private String openaiBaseUrl   = "https://api.openai.com/v1";

    // Risk thresholds — tunable via environment variables
    private double maxFoir    = 0.60;   // Fixed Obligation to Income Ratio
    private double maxLtv     = 0.80;   // Loan-to-Value Ratio
    private double maxDti     = 0.55;   // Debt-to-Income Ratio
    private int    minAge     = 21;
    private int    maxAge     = 65;
    private int    minCreditScore = 650;

    public String getJwtSecret()           { return jwtSecret; }
    public void   setJwtSecret(String v)   { this.jwtSecret = v; }
    public long   getJwtExpirationMs()     { return jwtExpirationMs; }
    public void   setJwtExpirationMs(long v) { this.jwtExpirationMs = v; }
    public String getOpenaiApiKey()        { return openaiApiKey; }
    public void   setOpenaiApiKey(String v){ this.openaiApiKey = v; }
    public String getOpenaiModel()         { return openaiModel; }
    public void   setOpenaiModel(String v) { this.openaiModel = v; }
    public String getOpenaiBaseUrl()       { return openaiBaseUrl; }
    public void   setOpenaiBaseUrl(String v){ this.openaiBaseUrl = v; }
    public double getMaxFoir()             { return maxFoir; }
    public void   setMaxFoir(double v)     { this.maxFoir = v; }
    public double getMaxLtv()              { return maxLtv; }
    public void   setMaxLtv(double v)      { this.maxLtv = v; }
    public double getMaxDti()              { return maxDti; }
    public void   setMaxDti(double v)      { this.maxDti = v; }
    public int    getMinAge()              { return minAge; }
    public void   setMinAge(int v)         { this.minAge = v; }
    public int    getMaxAge()              { return maxAge; }
    public void   setMaxAge(int v)         { this.maxAge = v; }
    public int    getMinCreditScore()      { return minCreditScore; }
    public void   setMinCreditScore(int v) { this.minCreditScore = v; }
}
