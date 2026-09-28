package com.loomai.verification.vehicleprovider.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "simulator")
public record SimulatorProperties(
    @NotBlank String fixtureVersion,
    @NotBlank String controlApiKey,
    @NotBlank String tokenSigningSecret,
    @Min(30) @Max(3600) int tokenTtlSeconds,
    @NotEmpty List<@NotBlank String> allowedWebhookHosts,
    @Valid ProfileA profileA,
    @Valid ProfileB profileB
) {

    public record ProfileA(
        @NotBlank String accountId,
        @NotBlank String key,
        @NotBlank String secret,
        @NotBlank String webhookSecret
    ) {
    }

    public record ProfileB(
        @NotBlank String accountId,
        @NotBlank String apiKey,
        @NotBlank String webhookSecret
    ) {
    }
}
