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

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageValidityExtensionContracts.*;

@RestController
@RequestMapping("/api/deployments/provider-usage/validity-extensions")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class ProviderUsageValidityExtensionController {
    private final ProviderUsageValidityExtensionService service;
    private final ObjectMapper strict;

    public ProviderUsageValidityExtensionController(ProviderUsageValidityExtensionService service, ObjectMapper mapper) {
        this.service = service;
        strict = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
        strict.coercionConfigFor(com.fasterxml.jackson.databind.type.LogicalType.Textual)
            .setCoercion(com.fasterxml.jackson.databind.cfg.CoercionInputShape.Integer, com.fasterxml.jackson.databind.cfg.CoercionAction.Fail)
            .setCoercion(com.fasterxml.jackson.databind.cfg.CoercionInputShape.Float, com.fasterxml.jackson.databind.cfg.CoercionAction.Fail)
            .setCoercion(com.fasterxml.jackson.databind.cfg.CoercionInputShape.Boolean, com.fasterxml.jackson.databind.cfg.CoercionAction.Fail);
    }

    @PostMapping("/plan")
    public Plan plan(@RequestBody String body) { return service.plan(parse(body, ReviewRequest.class)); }
    @PostMapping("/{extensionId}/execute")
    public Result execute(@PathVariable String extensionId, @RequestBody String body) {
        return service.execute(extensionId, parse(body, Command.class));
    }
    @GetMapping("/{extensionId}")
    public Result read(@PathVariable String extensionId) { return service.read(extensionId); }
    @PostMapping("/{extensionId}/reconcile")
    public Result reconcile(@PathVariable String extensionId) { return service.reconcile(extensionId); }

    private <T> T parse(String body, Class<T> type) {
        try {
            T value = strict.readValue(body, type);
            if (value == null) throw ProviderUsageAllowanceFailure.invalid();
            return value;
        } catch (Exception invalid) { throw ProviderUsageAllowanceFailure.invalid(); }
    }
}
