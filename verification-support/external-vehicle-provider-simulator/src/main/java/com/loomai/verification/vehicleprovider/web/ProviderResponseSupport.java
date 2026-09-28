package com.loomai.verification.vehicleprovider.web;

import com.loomai.verification.vehicleprovider.model.SimulatorContracts.ActiveFault;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.FaultMode;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.Profile;
import com.loomai.verification.vehicleprovider.service.ProviderApiException;
import com.loomai.verification.vehicleprovider.service.SimulatorService;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ProviderResponseSupport {

    private final SimulatorService simulator;

    public ProviderResponseSupport(SimulatorService simulator) {
        this.simulator = simulator;
    }

    public FaultMode applyFault(Profile profile, String accountId) {
        Optional<ActiveFault> configured = simulator.consumeFault(profile, accountId);
        if (configured.isEmpty()) {
            return FaultMode.NONE;
        }
        ActiveFault fault = configured.get();
        if (fault.delayMs() > 0) {
            try {
                Thread.sleep(fault.delayMs());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ProviderApiException(503, "SIMULATOR_INTERRUPTED", "The simulated provider request was interrupted.");
            }
        }
        switch (fault.mode()) {
            case UNAUTHORIZED -> throw new ProviderApiException(401, "TOKEN_REJECTED", "The simulated credential was rejected.");
            case FORBIDDEN -> throw new ProviderApiException(403, "CAPABILITY_DENIED", "The simulated capability is not granted.");
            case RATE_LIMITED -> throw new ProviderApiException(429, "RATE_LIMITED", "The simulated provider rate limit was reached.");
            case UNAVAILABLE -> throw new ProviderApiException(503, "SERVICE_UNAVAILABLE", "The simulated provider is unavailable.");
            case TIMEOUT -> throw new ProviderApiException(504, "UPSTREAM_TIMEOUT", "The simulated provider timed out.");
            default -> {
                return fault.mode();
            }
        }
    }
}
