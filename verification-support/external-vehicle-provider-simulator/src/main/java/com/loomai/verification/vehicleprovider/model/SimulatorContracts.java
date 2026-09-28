package com.loomai.verification.vehicleprovider.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.List;

public final class SimulatorContracts {

    private SimulatorContracts() {
    }

    public enum Profile {
        PROFILE_A("profile-a"),
        PROFILE_B("profile-b");

        private final String value;

        Profile(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        public static Profile parse(String value) {
            for (Profile profile : values()) {
                if (profile.value.equalsIgnoreCase(value)) {
                    return profile;
                }
            }
            throw new IllegalArgumentException("Unknown provider profile.");
        }
    }

    public enum FaultMode {
        NONE,
        UNAUTHORIZED,
        FORBIDDEN,
        RATE_LIMITED,
        UNAVAILABLE,
        TIMEOUT,
        MALFORMED_RESPONSE,
        PARTIAL_PAGE
    }

    public enum EventVariant {
        VALID,
        DUPLICATE,
        DELAYED,
        OUT_OF_ORDER,
        MALFORMED,
        WRONG_RESOURCE,
        WRONG_SIGNATURE
    }

    public record VehicleInput(
        @NotBlank String id,
        @NotBlank String make,
        @NotBlank String model,
        @NotBlank String derivative,
        @Min(1990) @Max(2100) int year,
        @PositiveOrZero long priceMinor,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotBlank String fuelType,
        @NotBlank String bodyStyle,
        @NotBlank String transmission,
        @PositiveOrZero int mileage,
        @NotBlank @Pattern(regexp = "active|reserved|sold|deleted") String state
    ) {
    }

    public record VehicleRecord(
        String profile,
        String accountId,
        String id,
        String make,
        String model,
        String derivative,
        int year,
        long priceMinor,
        String currency,
        String fuelType,
        String bodyStyle,
        String transmission,
        int mileage,
        String state,
        long version,
        Instant updatedAt
    ) {
    }

    public record FixtureRun(String fixtureVersion, String resetId, Instant resetAt) {
    }

    public record AccountState(String profile, String accountId, long sourceVersion, long eventSequence) {
    }

    public record FaultRequest(
        @NotNull FaultMode mode,
        @Min(1) @Max(100) int remaining,
        @Min(0) @Max(30_000) int delayMs
    ) {
    }

    public record ActiveFault(FaultMode mode, int remaining, int delayMs) {
    }

    public record EventRequest(
        @NotNull EventVariant variant,
        @NotBlank String targetUrl,
        String vehicleId,
        String eventId
    ) {
    }

    public record EventAttempt(int attempt, int status, String responseClass) {
    }

    public record EventDelivery(
        String eventId,
        EventVariant variant,
        String profile,
        String accountId,
        String targetHost,
        long sequence,
        List<EventAttempt> attempts
    ) {
    }

    public record ControlStatus(
        String service,
        String purpose,
        FixtureRun fixture,
        List<AccountState> accounts
    ) {
    }

    public record MutationReceipt(
        String profile,
        String accountId,
        String vehicleId,
        String operation,
        long sourceVersion
    ) {
    }
}
