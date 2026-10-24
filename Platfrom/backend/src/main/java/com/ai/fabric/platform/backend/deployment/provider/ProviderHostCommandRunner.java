package com.ai.fabric.platform.backend.deployment.provider;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;

import java.util.List;

public interface ProviderHostCommandRunner {
    record Result(int exitCode, String output) { }

    Result run(TargetIntent intent, String expectedServerUuid, List<String> command);
}
