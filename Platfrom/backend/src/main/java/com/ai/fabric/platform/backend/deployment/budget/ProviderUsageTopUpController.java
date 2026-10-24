package com.ai.fabric.platform.backend.deployment.budget;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageTopUpContracts.*;

@RestController
@RequestMapping("/api/deployments/provider-usage/top-ups")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class ProviderUsageTopUpController {
    private final ProviderUsageTopUpService service;
    private final ObjectMapper strict;

    public ProviderUsageTopUpController(ProviderUsageTopUpService service, ObjectMapper mapper) {
        this.service = service;
        strict = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
                DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        strict.coercionConfigFor(com.fasterxml.jackson.databind.type.LogicalType.Textual)
            .setCoercion(com.fasterxml.jackson.databind.cfg.CoercionInputShape.Integer, com.fasterxml.jackson.databind.cfg.CoercionAction.Fail)
            .setCoercion(com.fasterxml.jackson.databind.cfg.CoercionInputShape.Float, com.fasterxml.jackson.databind.cfg.CoercionAction.Fail)
            .setCoercion(com.fasterxml.jackson.databind.cfg.CoercionInputShape.Boolean, com.fasterxml.jackson.databind.cfg.CoercionAction.Fail);
        strict.coercionConfigFor(com.fasterxml.jackson.databind.type.LogicalType.Integer)
            .setCoercion(com.fasterxml.jackson.databind.cfg.CoercionInputShape.EmptyString, com.fasterxml.jackson.databind.cfg.CoercionAction.Fail);
    }

    @PostMapping("/plan")
    public Plan plan(@RequestBody String body) { return service.plan(parse(body, ReviewRequest.class)); }
    @PostMapping("/{topUpId}/execute")
    public Result execute(@PathVariable String topUpId, @RequestBody String body) { return service.execute(topUpId, parse(body, Command.class)); }
    @GetMapping("/{topUpId}")
    public Result read(@PathVariable String topUpId) { return service.read(topUpId); }
    @PostMapping("/{topUpId}/reconcile")
    public Result reconcile(@PathVariable String topUpId) { return service.reconcile(topUpId); }

    private <T> T parse(String body, Class<T> type) {
        try {
            T value = strict.readValue(body, type);
            if (value == null) throw ProviderUsageAllowanceFailure.invalid();
            return value;
        } catch (Exception invalid) { throw ProviderUsageAllowanceFailure.invalid(); }
    }
}
