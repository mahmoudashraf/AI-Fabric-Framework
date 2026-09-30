package com.loomai.demo.dealership.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.loomai.demo.dealership.config.DealershipDemoProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/public/vehicles")
public class InventoryController {

    private final VehicleRepository repository;
    private final DealershipDemoProperties properties;
    private final ObjectMapper objectMapper;

    public InventoryController(VehicleRepository repository,
                               DealershipDemoProperties properties,
                               ObjectMapper objectMapper) {
        this.repository = repository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public Map<String, Object> search(
        @RequestParam String dealershipId,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String make,
        @RequestParam(required = false) String fuelType,
        @RequestParam(required = false) String bodyType,
        @RequestParam(required = false) Long minPriceGbp,
        @RequestParam(required = false) Long maxPriceGbp,
        @RequestParam(required = false) Integer maxMileage,
        @RequestParam(defaultValue = "recommended") String sort,
        @RequestParam(defaultValue = "24") int limit,
        @RequestParam(defaultValue = "0") int offset
    ) {
        if (!properties.getId().equals(dealershipId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dealership inventory was not found.");
        }
        VehicleRepository.SearchResult result = repository.search(new VehicleSearchCriteria(
            q, make, fuelType, bodyType, poundsToMinor(minPriceGbp), poundsToMinor(maxPriceGbp),
            maxMileage, sort, limit, offset
        ));
        Map<String, Object> facets = new LinkedHashMap<>();
        facets.put("makes", repository.facets("make_name"));
        facets.put("fuelTypes", repository.facets("fuel_type"));
        facets.put("bodyTypes", repository.facets("body_type"));
        return Map.of(
            "success", true,
            "dealership", Map.of("id", properties.getId(), "name", properties.getName()),
            "items", result.items().stream().map(this::publicVehicle).toList(),
            "total", result.total(),
            "facets", facets,
            "source", sourceSummary(),
            "dataNotice", "Fictional demonstration inventory. No live Auto Trader data is used."
        );
    }

    private Long poundsToMinor(Long pounds) {
        if (pounds == null) {
            return null;
        }
        if (pounds < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price filters cannot be negative.");
        }
        try {
            return Math.multiplyExact(pounds, 100L);
        } catch (ArithmeticException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price filter is outside the supported range.");
        }
    }

    @GetMapping("/{slug}")
    public Map<String, Object> detail(@PathVariable String slug) {
        Vehicle vehicle = repository.findActiveBySlug(slug)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle was not found or is no longer active."));
        return Map.of(
            "success", true,
            "vehicle", publicVehicle(vehicle),
            "source", sourceSummary(),
            "dataNotice", "Fictional demonstration inventory. Confirm current availability with the dealership."
        );
    }

    @GetMapping("/resolve")
    public Map<String, Object> resolve(
        @RequestParam String dealershipId,
        @RequestParam String reference
    ) {
        if (!properties.getId().equals(dealershipId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dealership inventory was not found.");
        }
        List<Vehicle> matches = repository.resolveActiveReference(reference);
        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No active vehicle matches that reference.");
        }
        if (matches.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The vehicle reference is ambiguous.");
        }
        Vehicle vehicle = matches.getFirst();
        return Map.of(
            "success", true,
            "message", "Vehicle reference resolved to one active dealership vehicle.",
            "vehicleId", vehicle.id(),
            "slug", vehicle.slug(),
            "vehicle", vehicle.displayName(),
            "dealershipId", properties.getId(),
            "lifecycleState", vehicle.lifecycleState(),
            "source", sourceSummary()
        );
    }

    @GetMapping("/compare")
    public Map<String, Object> compare(@RequestParam String ids) {
        List<String> requested = Arrays.stream(ids.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .distinct()
            .limit(4)
            .toList();
        if (requested.size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose at least two vehicles to compare.");
        }
        List<Vehicle> vehicles = repository.findActiveByIds(requested);
        if (vehicles.size() != requested.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "One or more selected vehicles are no longer active.");
        }
        return Map.of(
            "success", true,
            "vehicles", vehicles.stream().map(this::publicVehicle).toList(),
            "source", sourceSummary()
        );
    }

    private Map<String, Object> sourceSummary() {
        Instant refreshedAt = repository.latestSourceUpdate().orElse(Instant.EPOCH);
        return Map.of(
            "label", properties.getSourceLabel(),
            "refreshedAt", refreshedAt,
            "authoritativeFor", List.of("price", "mileage", "availability", "vehicle details")
        );
    }

    private ObjectNode publicVehicle(Vehicle vehicle) {
        ObjectNode payload = objectMapper.valueToTree(vehicle);
        payload.remove("priceMinor");
        BigDecimal priceGbp = BigDecimal.valueOf(vehicle.priceMinor(), 2);
        payload.put("priceGbp", priceGbp);
        payload.put("priceFormatted", NumberFormat.getCurrencyInstance(Locale.UK).format(priceGbp));
        payload.put("dealershipId", properties.getId());
        return payload;
    }
}
