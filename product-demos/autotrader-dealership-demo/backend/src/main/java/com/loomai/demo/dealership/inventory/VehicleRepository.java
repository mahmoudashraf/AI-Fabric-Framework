package com.loomai.demo.dealership.inventory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class VehicleRepository {

    private static final String SELECT = """
        SELECT id, stock_id, slug, make_name, model_name, derivative,
               registration_year, price_minor, currency, mileage, fuel_type,
               transmission, body_type, exterior_colour, doors, seats,
               electric_range_miles, location_name, lifecycle_state, summary,
               features_json, image_path, source_label, source_updated_at,
               source_version
          FROM dealership_vehicle
        """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RowMapper<Vehicle> mapper = (rs, rowNum) -> new Vehicle(
        rs.getString("id"),
        rs.getString("stock_id"),
        rs.getString("slug"),
        rs.getString("make_name"),
        rs.getString("model_name"),
        rs.getString("derivative"),
        rs.getInt("registration_year"),
        rs.getLong("price_minor"),
        rs.getString("currency"),
        rs.getInt("mileage"),
        rs.getString("fuel_type"),
        rs.getString("transmission"),
        rs.getString("body_type"),
        rs.getString("exterior_colour"),
        rs.getInt("doors"),
        rs.getInt("seats"),
        (Integer) rs.getObject("electric_range_miles"),
        rs.getString("location_name"),
        rs.getString("lifecycle_state"),
        rs.getString("summary"),
        readFeatures(rs.getString("features_json")),
        rs.getString("image_path"),
        rs.getString("source_label"),
        rs.getTimestamp("source_updated_at").toInstant(),
        rs.getLong("source_version")
    );

    public VehicleRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public long count() {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM dealership_vehicle", Long.class);
        return value == null ? 0 : value;
    }

    public void insert(Vehicle vehicle) {
        jdbc.update("""
            INSERT INTO dealership_vehicle (
                id, stock_id, slug, make_name, model_name, derivative,
                registration_year, price_minor, currency, mileage, fuel_type,
                transmission, body_type, exterior_colour, doors, seats,
                electric_range_miles, location_name, lifecycle_state, summary,
                features_json, image_path, source_label, source_updated_at,
                source_version, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            vehicle.id(), vehicle.stockId(), vehicle.slug(), vehicle.make(), vehicle.model(), vehicle.derivative(),
            vehicle.registrationYear(), vehicle.priceMinor(), vehicle.currency(), vehicle.mileage(), vehicle.fuelType(),
            vehicle.transmission(), vehicle.bodyType(), vehicle.exteriorColour(), vehicle.doors(), vehicle.seats(),
            vehicle.electricRangeMiles(), vehicle.location(), vehicle.lifecycleState(), vehicle.summary(),
            writeFeatures(vehicle.features()), vehicle.imagePath(), vehicle.sourceLabel(),
            Timestamp.from(vehicle.sourceUpdatedAt()), vehicle.sourceVersion(), Timestamp.from(Instant.now()), Timestamp.from(Instant.now())
        );
    }

    public Optional<Vehicle> findActiveBySlug(String slug) {
        List<Vehicle> results = jdbc.query(
            SELECT + " WHERE slug = ? AND lifecycle_state = 'ACTIVE'",
            mapper,
            slug
        );
        return results.stream().findFirst();
    }

    public Optional<Vehicle> findActiveById(String id) {
        List<Vehicle> results = jdbc.query(
            SELECT + " WHERE id = ? AND lifecycle_state = 'ACTIVE'",
            mapper,
            id
        );
        return results.stream().findFirst();
    }

    public List<Vehicle> findActiveByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        return jdbc.query(
            SELECT + " WHERE lifecycle_state = 'ACTIVE' AND id IN (" + placeholders + ") ORDER BY registration_year DESC, price_minor ASC",
            mapper,
            ids.toArray()
        );
    }

    public List<Vehicle> findAllActive() {
        return jdbc.query(SELECT + " WHERE lifecycle_state = 'ACTIVE' ORDER BY stock_id", mapper);
    }

    public List<Vehicle> findAll() {
        return jdbc.query(SELECT + " ORDER BY stock_id", mapper);
    }

    public SearchResult search(VehicleSearchCriteria criteria) {
        StringBuilder where = new StringBuilder(" WHERE lifecycle_state = 'ACTIVE'");
        List<Object> parameters = new ArrayList<>();
        if (StringUtils.hasText(criteria.query())) {
            where.append(" AND (LOWER(make_name) LIKE ? OR LOWER(model_name) LIKE ? OR LOWER(derivative) LIKE ? OR LOWER(summary) LIKE ? OR LOWER(features_json) LIKE ?)");
            String query = "%" + criteria.query().trim().toLowerCase() + "%";
            for (int i = 0; i < 5; i++) {
                parameters.add(query);
            }
        }
        exact(where, parameters, "make_name", criteria.make());
        exact(where, parameters, "fuel_type", criteria.fuelType());
        exact(where, parameters, "body_type", criteria.bodyType());
        if (criteria.minimumPriceMinor() != null) {
            where.append(" AND price_minor >= ?");
            parameters.add(criteria.minimumPriceMinor());
        }
        if (criteria.maximumPriceMinor() != null) {
            where.append(" AND price_minor <= ?");
            parameters.add(criteria.maximumPriceMinor());
        }
        if (criteria.maximumMileage() != null) {
            where.append(" AND mileage <= ?");
            parameters.add(criteria.maximumMileage());
        }

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM dealership_vehicle" + where, Long.class, parameters.toArray());
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(criteria.limit());
        pageParameters.add(criteria.offset());
        List<Vehicle> items = jdbc.query(
            SELECT + where + orderBy(criteria.sort()) + " LIMIT ? OFFSET ?",
            mapper,
            pageParameters.toArray()
        );
        return new SearchResult(items, total == null ? 0 : total);
    }

    public List<FacetValue> facets(String column) {
        if (!List.of("make_name", "fuel_type", "body_type").contains(column)) {
            throw new IllegalArgumentException("Unsupported facet column.");
        }
        return jdbc.query(
            "SELECT " + column + " AS facet_value, COUNT(*) AS item_count FROM dealership_vehicle WHERE lifecycle_state = 'ACTIVE' GROUP BY " + column + " ORDER BY " + column,
            (rs, rowNum) -> new FacetValue(rs.getString("facet_value"), rs.getInt("item_count"))
        );
    }

    public Optional<Instant> latestSourceUpdate() {
        Timestamp value = jdbc.queryForObject(
            "SELECT MAX(source_updated_at) FROM dealership_vehicle WHERE lifecycle_state = 'ACTIVE'",
            Timestamp.class
        );
        return Optional.ofNullable(value).map(Timestamp::toInstant);
    }

    private void exact(StringBuilder where, List<Object> parameters, String column, String value) {
        if (StringUtils.hasText(value)) {
            where.append(" AND LOWER(").append(column).append(") = ?");
            parameters.add(value.trim().toLowerCase());
        }
    }

    private String orderBy(String sort) {
        return switch (sort == null ? "recommended" : sort) {
            case "price-asc" -> " ORDER BY price_minor ASC, registration_year DESC";
            case "price-desc" -> " ORDER BY price_minor DESC, registration_year DESC";
            case "mileage-asc" -> " ORDER BY mileage ASC, registration_year DESC";
            case "newest" -> " ORDER BY registration_year DESC, mileage ASC";
            default -> " ORDER BY source_updated_at DESC, registration_year DESC, price_minor ASC";
        };
    }

    private String writeFeatures(List<String> features) {
        try {
            return objectMapper.writeValueAsString(features == null ? List.of() : features);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize vehicle features.", ex);
        }
    }

    private List<String> readFeatures(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (Exception ex) {
            throw new IllegalStateException("Could not read vehicle features.", ex);
        }
    }

    public record SearchResult(List<Vehicle> items, long total) { }
    public record FacetValue(String value, int count) { }
}
