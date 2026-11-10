package com.ai.fabric.runtime.authoring.boot;

import com.ai.fabric.runtime.auth.RuntimeAuthIngressMode;
import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;

/** Dedicated ingress and finite accounting are requirements, not optional inference fallbacks. */
final class ConfigurationAuthoringBootValidator implements SmartInitializingSingleton {
    private final Environment environment;
    private final RuntimeAuthProperties auth;

    ConfigurationAuthoringBootValidator(Environment environment, RuntimeAuthProperties auth) {
        this.environment = environment;
        this.auth = auth;
    }

    @Override public void afterSingletonsInstantiated() {
        if (!environment.getProperty("loomai.authoring.enabled", Boolean.class, false)
            || !environment.getProperty("loomai.provider-budget.enabled", Boolean.class, false)) {
            throw new IllegalStateException("Dedicated authoring requires loomai.authoring.enabled=true and loomai.provider-budget.enabled=true.");
        }
        if (auth.getIngress().getMode() != RuntimeAuthIngressMode.VERIFIED_CONTEXT_REQUIRED) {
            throw new IllegalStateException("Dedicated authoring requires VERIFIED_CONTEXT_REQUIRED private ingress.");
        }
    }
}
