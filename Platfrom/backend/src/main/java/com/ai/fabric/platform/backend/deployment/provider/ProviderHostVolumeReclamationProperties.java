package com.ai.fabric.platform.backend.deployment.provider;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

@ConfigurationProperties(prefix = "platform.provider-host-volume-reclamation")
public record ProviderHostVolumeReclamationProperties(boolean enabled, Duration timeout,
                                                       Map<TargetIntent, Host> hosts) {
    public ProviderHostVolumeReclamationProperties {
        timeout = timeout == null ? Duration.ofSeconds(15) : timeout;
        hosts = hosts == null ? Map.of() : Map.copyOf(hosts);
    }

    public record Host(String username, Integer port, String privateKeySecretName,
                       String knownHostsSecretName) {
        public int effectivePort() {
            return port == null ? 22 : port;
        }
    }
}
