package com.creditlens.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.creditlens.config.AppProperties;
import com.creditlens.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Calls OpenAI GPT-4 Chat Completions to generate a natural-language
 * explanation of the loan decision.
 *
 * Prompt design:
 *   - System prompt establishes the AI as a Bank of Baroda-style credit officer
 *   - User prompt provides structured application data + computed metrics
 *   - The model is instructed to respond in a clear format so parsing is stable
 *   - Falls back to a deterministic template-based explanation when no API key
 *     is configured, ensuring the app is fully functional without AI costs
 *
 * Token budget: max_tokens=300 (one concise paragraph). GPT-4 costs ~$0.03/1K
 * output tokens — at 300 tokens per assessment that's ~₹0.75 per application.
 */
@Slf4j
@Service
public class AiExplanationService {

    private static final String SYSTEM_PROMPT =
        "You are a senior credit underwriter at an Indian public sector bank with 20 years of experience " +
        "evaluating loan applications per RBI guidelines. Provide a concise, professional explanation of " +
        "the loan decision in 3–4 sentences. Mention specific financial metrics (FOIR, credit score, LTV) " +
        "where relevant. Be direct but respectful. Use plain English, no jargon acronyms without expansion. " +
        "Do not recommend other banks or products.";

    private final AppProperties props;
    private final RestTemplate  restTemplate;
    private final ObjectMapper  objectMapper;

    public AiExplanationService(AppProperties props,
                                @Qualifier("openAiRestTemplate") RestTemplate restTemplate,
                                ObjectMapper objectMapper) {
        this.props        = props;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Generates an AI explanation for the loan decision.
     * Returns a fallback template-based string if OpenAI is not configured.
     */
    public String explain(LoanApplicationRequest app, RiskMetrics metrics, Decision decision) {
        if (!isOpenAiConfigured()) {
            return buildFallbackExplanation(app, metrics, decision);
        }
        try {
            return callOpenAi(app, metrics, decision);
        } catch (Exception e) {
            log.error("OpenAI call failed: {}. Using fallback explanation.", e.getMessage());
            return buildFallbackExplanation(app, metrics, decision);
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private boolean isOpenAiConfigured() {
        return props.getOpenaiApiKey() != null && !props.getOpenaiApiKey().trim().isEmpty();
    }

    private String callOpenAi(LoanApplicationRequest app, RiskMetrics metrics,
                               Decision decision) throws Exception {
        String userPrompt = buildUserPrompt(app, metrics, decision);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.getOpenaiModel());
        body.put("max_tokens", 300);
        body.put("temperature", 0.4);

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> sys = new LinkedHashMap<>();
        sys.put("role", "system");
        sys.put("content", SYSTEM_PROMPT);
        messages.add(sys);

        Map<String, String> usr = new LinkedHashMap<>();
        usr.put("role", "user");
        usr.put("content", userPrompt);
        messages.add(usr);

        body.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(props.getOpenaiApiKey());

        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
        ResponseEntity<String> resp = restTemplate.postForEntity(
                props.getOpenaiBaseUrl() + "/chat/completions", req, String.class);

        if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
            JsonNode root = objectMapper.readTree(resp.getBody());
            String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
            if (!content.isEmpty()) return content;
        }
        return buildFallbackExplanation(app, metrics, decision);
    }

    private String buildUserPrompt(LoanApplicationRequest app, RiskMetrics metrics,
                                    Decision decision) {
        StringBuilder sb = new StringBuilder();
        sb.append("Loan Application Assessment Summary\n");
        sb.append("====================================\n");
        sb.append("Decision: ").append(decision).append("\n\n");
        sb.append("Applicant: ").append(app.getApplicantName())
          .append(", Age: ").append(app.getAge())
          .append(", Employment: ").append(app.getEmploymentType()).append("\n");
        sb.append("Loan: ₹").append(formatAmount(app.getLoanAmount()))
          .append(" for ").append(app.getTenureMonths()).append(" months at ")
          .append(app.getAnnualInterestRate()).append("% p.a.")
          .append(" | Purpose: ").append(app.getLoanPurpose()).append("\n");
        sb.append("Monthly Income: ₹").append(formatAmount(app.getMonthlyIncome()))
          .append(" | Existing EMIs: ₹").append(formatAmount(app.getExistingEmiPerMonth()))
          .append(" | Proposed EMI: ₹").append(formatAmount(metrics.getProposedEmi())).append("\n");
        sb.append("Credit Score (CIBIL): ").append(app.getCreditScore()).append("\n\n");

        sb.append("Computed Metrics:\n");
        sb.append("  FOIR: ").append(pct(metrics.getFoir()))
          .append(" (max allowed: ").append((int)(props.getMaxFoir()*100)).append("%)\n");
        if (metrics.getLtv() != null)
            sb.append("  LTV: ").append(pct(metrics.getLtv()))
              .append(" (max allowed: ").append((int)(props.getMaxLtv()*100)).append("%)\n");
        sb.append("  DTI: ").append(pct(metrics.getDti())).append("\n");
        sb.append("  Risk Score: ").append(metrics.getRawScore()).append("/100\n\n");

        if (metrics.getFlags() != null && !metrics.getFlags().isEmpty()) {
            sb.append("Risk Flags Fired: ").append(metrics.getFlags()).append("\n\n");
        }
        if (app.getOfficerRemarks() != null && !app.getOfficerRemarks().trim().isEmpty()) {
            sb.append("Officer Remarks: ").append(app.getOfficerRemarks()).append("\n\n");
        }

        sb.append("Please provide a clear 3–4 sentence explanation of this decision " +
                  "suitable for the applicant and for the bank's audit trail.");
        return sb.toString();
    }

    /**
     * Template-based fallback — deterministic, zero cost, always works.
     * Used when OPENAI_API_KEY is blank or the API call fails.
     */
    private String buildFallbackExplanation(LoanApplicationRequest app,
                                             RiskMetrics metrics, Decision decision) {
        StringBuilder sb = new StringBuilder();
        switch (decision) {
            case APPROVED:
                sb.append("The loan application for ₹").append(formatAmount(app.getLoanAmount()))
                  .append(" has been approved. ");
                sb.append("The applicant demonstrates a strong repayment capacity with a FOIR of ")
                  .append(pct(metrics.getFoir()))
                  .append(" and a CIBIL score of ").append(app.getCreditScore()).append(". ");
                sb.append("The proposed EMI of ₹").append(formatAmount(metrics.getProposedEmi()))
                  .append(" is comfortably within the applicant's repayment capacity. ");
                sb.append("Disbursement can proceed subject to standard documentation verification.");
                break;

            case CONDITIONAL_APPROVAL:
                sb.append("The application shows borderline eligibility with a risk score of ")
                  .append(metrics.getRawScore()).append("/100. ");
                sb.append("The FOIR of ").append(pct(metrics.getFoir()))
                  .append(" is approaching the maximum permissible limit, indicating a significant ")
                  .append("existing debt burden relative to income. ");
                sb.append("Approval is recommended subject to the stated conditions being met. ");
                sb.append("A co-applicant with independent income would materially strengthen this application.");
                break;

            case REJECTED:
            default:
                sb.append("The loan application cannot be approved at this time due to elevated credit risk. ");
                if (!metrics.getFlags().isEmpty()) {
                    sb.append("Key concerns include: ");
                    sb.append(String.join("; ", flagSummaries(metrics.getFlags()))).append(". ");
                }
                sb.append("The applicant is encouraged to improve their credit profile and reapply after ")
                  .append("6–12 months. ");
                sb.append("Set OPENAI_API_KEY for AI-powered personalized guidance.");
                break;
        }
        return sb.toString();
    }

    private List<String> flagSummaries(List<RiskFlag> flags) {
        List<String> summaries = new ArrayList<>();
        for (RiskFlag f : flags) {
            switch (f) {
                case HIGH_FOIR:         summaries.add("high fixed obligations relative to income"); break;
                case LOW_CREDIT_SCORE:  summaries.add("CIBIL score below minimum threshold"); break;
                case HIGH_LTV:          summaries.add("insufficient collateral coverage"); break;
                case HIGH_DTI:          summaries.add("excessive debt-to-income ratio"); break;
                case UNDERAGE:          summaries.add("applicant below minimum eligible age"); break;
                case OVERAGE:           summaries.add("applicant above maximum eligible age"); break;
                case INSUFFICIENT_INCOME: summaries.add("income insufficient to service EMI"); break;
                case EXCESSIVE_LOAN_AMOUNT: summaries.add("loan amount exceeds income-based cap"); break;
                default:                summaries.add(f.name().toLowerCase().replace('_', ' '));
            }
        }
        return summaries;
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "N/A";
        return String.format("%,.2f", amount);
    }

    private String pct(BigDecimal ratio) {
        if (ratio == null) return "N/A";
        return ratio.multiply(new BigDecimal("100")).setScale(1, RoundingMode.HALF_UP) + "%";
    }
}
