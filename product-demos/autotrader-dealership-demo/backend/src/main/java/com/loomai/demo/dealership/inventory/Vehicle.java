package com.loomai.demo.dealership.inventory;

import java.time.Instant;
import java.util.List;

public record Vehicle(
    String id,
    String stockId,
    String slug,
    String make,
    String model,
    String derivative,
    int registrationYear,
    long priceMinor,
    String currency,
    int mileage,
    String fuelType,
    String transmission,
    String bodyType,
    String exteriorColour,
    int doors,
    int seats,
    Integer electricRangeMiles,
    String location,
    String lifecycleState,
    String summary,
    List<String> features,
    String imagePath,
    String sourceLabel,
    Instant sourceUpdatedAt,
    long sourceVersion
) {
    public boolean isActive() {
        return "ACTIVE".equals(lifecycleState);
    }

    public String displayName() {
        return registrationYear + " " + make + " " + model;
    }
}
