package com.loomai.verification.vehicleprovider.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.VehicleRecord;

import java.math.BigDecimal;
import java.util.List;

public final class JsonProjection {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonProjection() {
    }

    static ObjectNode object() {
        return MAPPER.createObjectNode();
    }

    static ArrayNode profileA(List<VehicleRecord> records) {
        ArrayNode result = MAPPER.createArrayNode();
        records.forEach(record -> result.add(profileA(record)));
        return result;
    }

    static ObjectNode profileA(VehicleRecord record) {
        return common(record)
            .put("accountId", record.accountId())
            .put("title", record.year() + " " + record.make() + " " + record.model() + " " + record.derivative());
    }

    static ArrayNode profileB(List<VehicleRecord> records) {
        ArrayNode result = MAPPER.createArrayNode();
        records.forEach(record -> result.add(profileB(record)));
        return result;
    }

    static ObjectNode profileB(VehicleRecord record) {
        ObjectNode result = common(record)
            .put("ownerRef", record.accountId())
            .put("displayName", record.make() + " " + record.model());
        ObjectNode specification = object()
            .put("fuel", record.fuelType())
            .put("body", record.bodyStyle())
            .put("gearbox", record.transmission());
        result.set("specification", specification);
        return result;
    }

    public static ArrayNode autoTrader(List<VehicleRecord> records) {
        ArrayNode result = MAPPER.createArrayNode();
        records.forEach(record -> result.add(autoTrader(record)));
        return result;
    }

    public static ObjectNode autoTrader(VehicleRecord record) {
        ObjectNode result = object();
        ObjectNode vehicle = object()
            .put("ownershipCondition", "Used")
            .put("registration", registration(record))
            .put("vin", "SIMULATED" + compactId(record.id()).toUpperCase())
            .put("make", record.make())
            .put("model", record.model())
            .put("derivative", record.derivative())
            .put("vehicleType", "Car")
            .put("bodyType", record.bodyStyle())
            .put("fuelType", record.fuelType())
            .put("transmissionType", record.transmission())
            .put("odometerReadingMiles", record.mileage())
            .put("firstRegistrationDate", record.year() + "-01-01")
            .put("yearOfManufacture", Integer.toString(record.year()));
        vehicle.set("standard", object()
            .put("make", record.make())
            .put("model", record.model())
            .put("derivative", record.derivative())
            .put("bodyType", record.bodyStyle())
            .put("fuelType", record.fuelType())
            .put("transmissionType", record.transmission()));
        result.set("vehicle", vehicle);
        result.set("advertiser", object().put("advertiserId", record.accountId()));

        BigDecimal pounds = BigDecimal.valueOf(record.priceMinor(), 2);
        ObjectNode retailAdverts = object();
        retailAdverts.set("suppliedPrice", object().put("amountGBP", pounds));
        retailAdverts.set("totalPrice", object().put("amountGBP", pounds));
        retailAdverts.put("attentionGrabber", record.derivative());
        retailAdverts.put("description", record.year() + " " + record.make() + " " + record.model()
            + " " + record.derivative() + " synthetic demonstration stock record.");
        retailAdverts.set("advertiserAdvert", object().put("status", publicationStatus(record)));
        ObjectNode adverts = object();
        if ("reserved".equalsIgnoreCase(record.state())) {
            adverts.put("reservationStatus", "Reserved");
        } else {
            adverts.putNull("reservationStatus");
        }
        adverts.set("retailAdverts", retailAdverts);
        result.set("adverts", adverts);

        ObjectNode metadata = object()
            .put("stockId", record.id())
            .put("searchId", searchId(record))
            .put("lastUpdated", record.updatedAt().toString())
            .put("lastUpdatedByAdvertiser", record.updatedAt().toString())
            .put("versionNumber", record.version())
            .put("lifecycleState", lifecycleState(record))
            .put("dateOnForecourt", record.updatedAt().toString().substring(0, 10));
        metadata.putNull("externalStockId");
        metadata.putNull("externalStockReference");
        result.set("metadata", metadata);

        ArrayNode features = MAPPER.createArrayNode();
        features.add(object().put("name", "Synthetic provider contract fixture").put("type", "Standard"));
        features.add(object().put("name", record.transmission() + " transmission").put("type", "Standard"));
        result.set("features", features);
        ObjectNode media = object();
        media.set("images", MAPPER.createArrayNode());
        media.set("video", object().putNull("href"));
        media.set("spin", object().putNull("href"));
        result.set("media", media);
        return result;
    }

    public static String lifecycleState(VehicleRecord record) {
        return switch (record.state().toLowerCase()) {
            case "sold" -> "SOLD";
            case "deleted" -> "DELETED";
            default -> "FORECOURT";
        };
    }

    public static String publicationStatus(VehicleRecord record) {
        return switch (record.state().toLowerCase()) {
            case "sold", "deleted" -> "NOT_PUBLISHED";
            default -> "PUBLISHED";
        };
    }

    public static String searchId(VehicleRecord record) {
        return "SIM" + Integer.toUnsignedString(record.id().hashCode());
    }

    private static String registration(VehicleRecord record) {
        int value = record.id().hashCode();
        StringBuilder suffix = new StringBuilder(3);
        for (int index = 0; index < 3; index++) {
            suffix.append((char) ('A' + Integer.remainderUnsigned(value + index * 17, 26)));
        }
        return "NF" + Math.floorMod(record.year(), 100) + suffix;
    }

    private static String compactId(String value) {
        String compact = value == null ? "STOCK" : value.replaceAll("[^A-Za-z0-9]", "");
        return compact.length() <= 20 ? compact : compact.substring(compact.length() - 20);
    }

    private static ObjectNode common(VehicleRecord record) {
        return object()
            .put("id", record.id())
            .put("make", record.make())
            .put("model", record.model())
            .put("derivative", record.derivative())
            .put("year", record.year())
            .put("priceMinor", record.priceMinor())
            .put("currency", record.currency())
            .put("fuelType", record.fuelType())
            .put("bodyStyle", record.bodyStyle())
            .put("transmission", record.transmission())
            .put("mileage", record.mileage())
            .put("state", record.state())
            .put("version", record.version())
            .put("updatedAt", record.updatedAt().toString());
    }
}
