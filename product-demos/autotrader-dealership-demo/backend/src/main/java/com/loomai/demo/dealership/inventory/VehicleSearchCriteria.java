package com.loomai.demo.dealership.inventory;

public record VehicleSearchCriteria(
    String query,
    String make,
    String fuelType,
    String bodyType,
    Long minimumPriceMinor,
    Long maximumPriceMinor,
    Integer maximumMileage,
    String sort,
    int limit,
    int offset
) {
    public VehicleSearchCriteria {
        limit = Math.max(1, Math.min(limit, 48));
        offset = Math.max(0, offset);
    }
}
