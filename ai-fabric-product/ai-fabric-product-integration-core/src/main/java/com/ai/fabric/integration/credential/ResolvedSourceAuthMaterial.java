package com.ai.fabric.integration.credential;

import org.springframework.util.StringUtils;

import java.util.Map;

public record ResolvedSourceAuthMaterial(
    Map<String, String> resolvedSecrets,
    Map<String, String> localSecretAliases
) {

    public String secret(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String direct = resolvedSecrets == null ? null : resolvedSecrets.get(key);
        if (StringUtils.hasText(direct)) {
            return direct.trim();
        }
        String alias = localSecretAliases == null ? null : localSecretAliases.get(key);
        if (!StringUtils.hasText(alias)) {
            return null;
        }
        String envValue = System.getenv(alias.trim());
        return StringUtils.hasText(envValue) ? envValue.trim() : null;
    }
}
