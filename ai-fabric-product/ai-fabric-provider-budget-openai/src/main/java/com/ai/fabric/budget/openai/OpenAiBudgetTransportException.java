package com.ai.fabric.budget.openai;

import com.ai.fabric.budget.BudgetException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Typed SDK cause: host integration must inspect this type, never parse exception prose. */
public final class OpenAiBudgetTransportException extends IOException {
    private final UUID attemptId;
    private final BudgetException.Code budgetCode;
    private final OpenAiBudgetException.Code guardCode;
    private final FailureMetadata failureMetadata;

    OpenAiBudgetTransportException(UUID attemptId, BudgetException.Code budgetCode, OpenAiBudgetException.Code guardCode) {
        this(attemptId, budgetCode, guardCode, null);
    }

    OpenAiBudgetTransportException(UUID attemptId, BudgetException.Code budgetCode, OpenAiBudgetException.Code guardCode,
        FailureMetadata failureMetadata) {
        super(budgetCode == null ? guardCode.name() : budgetCode.name());
        this.attemptId = attemptId;
        this.budgetCode = budgetCode;
        this.guardCode = guardCode;
        this.failureMetadata = failureMetadata;
    }

    public UUID attemptId() { return attemptId; }
    public Optional<BudgetException.Code> budgetCode() { return Optional.ofNullable(budgetCode); }
    public Optional<OpenAiBudgetException.Code> guardCode() { return Optional.ofNullable(guardCode); }
    public Optional<FailureMetadata> failureMetadata() { return Optional.ofNullable(failureMetadata); }

    public enum FailureStage {
        TRANSPORT, HTTP_NON_SUCCESS, BODY_MISSING, ENCODING, CONTENT_TYPE, BODY_READ, BODY_TOO_LARGE, JSON,
        RESPONSE_OBJECT, PROVIDER_ERROR, RESPONSE_MODEL, USAGE_OBJECT, USAGE_INPUT_TOKENS, USAGE_OUTPUT_TOKENS,
        USAGE_TOTAL_TOKENS, USAGE_SUM, USAGE_BOUND, SERVICE_TIER, SETTLEMENT
    }

    public enum FailureType { SOCKET_TIMEOUT, INTERRUPTED_IO, CONNECT, TLS, IO, JSON, GUARD, BUDGET, OTHER }

    /** Private diagnostic evidence only; recognition here never authorizes a model, tier or price. */
    public record FailureMetadata(FailureStage stage, Integer httpStatus, String providerRequestId,
                                  String requestedModel, Long effectiveOutputCap, String responseModel,
                                  String responseServiceTier, String providerErrorType, String providerErrorCode,
                                  String providerErrorCodeSha256, String providerErrorParam, FailureType failureType) {
        private static final Set<String> TIERS = Set.of("default", "auto", "standard", "priority", "flex", "scale");
        private static final Set<String> ERROR_TYPES = Set.of("invalid_request_error", "authentication_error",
            "permission_error", "rate_limit_error", "server_error", "api_error", "insufficient_quota", "not_found_error");
        private static final Set<String> ERROR_CODES = Set.of("unsupported_parameter", "unsupported_value", "invalid_value",
            "invalid_parameter", "missing_required_parameter", "model_not_found", "model_not_available", "invalid_api_key",
            "insufficient_quota", "rate_limit_exceeded", "context_length_exceeded", "permission_denied", "server_error");
        private static final Set<String> ERROR_PARAMS = Set.of("model", "max_tokens", "max_completion_tokens", "reasoning_effort",
            "service_tier", "response_format", "response_format.type", "stream", "n", "messages", "temperature", "top_p",
            "presence_penalty", "frequency_penalty", "logit_bias", "seed", "stop", "tools", "tool_choice", "parallel_tool_calls");

        public FailureMetadata {
            httpStatus = httpStatus != null && httpStatus >= 100 && httpStatus <= 599 ? httpStatus : null;
            providerRequestId = providerRequestId != null && providerRequestId.length() <= 128
                && providerRequestId.matches("req_[A-Za-z0-9_-]+") ? providerRequestId : null;
            requestedModel = safeModel(requestedModel);
            responseModel = safeModel(responseModel);
            effectiveOutputCap = effectiveOutputCap != null && effectiveOutputCap >= 1
                && effectiveOutputCap <= ReviewedOpenAiRateCard.CHAT_MAX_OUTPUT_TOKENS ? effectiveOutputCap : null;
            responseServiceTier = recognized(responseServiceTier, TIERS);
            providerErrorType = recognized(providerErrorType, ERROR_TYPES);
            providerErrorParam = recognized(providerErrorParam, ERROR_PARAMS);
            if (providerErrorCode != null && !ERROR_CODES.contains(providerErrorCode)) {
                providerErrorCodeSha256 = digest(providerErrorCode);
                providerErrorCode = null;
            }
            providerErrorCodeSha256 = providerErrorCodeSha256 != null && providerErrorCodeSha256.matches("[a-f0-9]{64}")
                ? providerErrorCodeSha256 : null;
        }

        private static String recognized(String value, Set<String> allowed) {
            return value != null && allowed.contains(value) ? value : null;
        }

        private static String safeModel(String value) {
            return value != null && value.length() <= 96
                && value.matches("gpt-[0-9][a-z0-9]*(?:\\.[0-9]+)?(?:-[a-z0-9]+){1,8}") ? value : null;
        }

        private static String digest(String value) {
            if (value.length() > 8192) return null;
            try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
            catch (java.security.NoSuchAlgorithmException unavailable) { return null; }
        }
    }

    public static Optional<OpenAiBudgetTransportException> find(Throwable error) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = error; current != null && visited.add(current); current = current.getCause()) {
            if (current instanceof OpenAiBudgetTransportException failure) return Optional.of(failure);
        }
        return Optional.empty();
    }
}
