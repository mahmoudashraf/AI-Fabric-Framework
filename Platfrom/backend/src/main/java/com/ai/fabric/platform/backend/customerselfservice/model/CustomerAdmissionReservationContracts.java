package com.ai.fabric.platform.backend.customerselfservice.model;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** Customer-safe admission contracts. Capacity and provider evidence remain staff-owned. */
public final class CustomerAdmissionReservationContracts {
    private CustomerAdmissionReservationContracts() { }

    public record Request(@NotBlank String targetProfileId,
                          @NotBlank String manifestId,
                          @NotBlank String versionId,
                          String releaseId,
                          @NotNull Operation operation,
                          @NotBlank String operationKey) { }

    public record Summary(String id,
                          String deploymentId,
                          String targetProfileId,
                          TargetIntent intent,
                          Operation operation,
                          String state,
                          long revision,
                          Instant expiresAt) { }
}
