package com.loomai.verification.vehicleprovider.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.loomai.verification.vehicleprovider.model.SimulatorContracts.VehicleRecord;

import java.util.List;

final class JsonProjection {

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
