package com.ai.infrastructure.storage.strategy.impl;

import com.ai.infrastructure.entity.AISearchableEntity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
class PerTypeRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final String entityType;
    private final String tableName;

    void save(AISearchableEntity entity) {
        if (entity == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (!StringUtils.hasText(entity.getId())) {
            entity.setId(UUID.randomUUID().toString());
        }
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);
        entity.setEntityType(entityType);

        MapSqlParameterSource params = baseParams(entity);

        int updated = jdbcTemplate.update(
            """
                UPDATE %s SET searchable_content=:searchableContent,
                    vector_id=:vectorId,
                    vector_updated_at=:vectorUpdatedAt,
                    metadata=:metadata,
                    ai_analysis=:aiAnalysis,
                    updated_at=:updatedAt
                WHERE entity_id=:entityId
                """.formatted(tableName),
            params
        );

        if (updated == 0) {
            jdbcTemplate.update(
                """
                    INSERT INTO %s (id, entity_type, entity_id, searchable_content, vector_id,
                        vector_updated_at, metadata, ai_analysis, created_at, updated_at)
                    VALUES (:id, :entityType, :entityId, :searchableContent, :vectorId,
                        :vectorUpdatedAt, :metadata, :aiAnalysis, :createdAt, :updatedAt)
                    """.formatted(tableName),
                params
            );
        }
    }

    Optional<AISearchableEntity> findByEntityId(String entityId) {
        if (!StringUtils.hasText(entityId)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT * FROM %s WHERE entity_id=:entityId".formatted(tableName),
                Map.of("entityId", entityId),
                mapper()
            ));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    List<AISearchableEntity> findAll() {
        return jdbcTemplate.query("SELECT * FROM %s".formatted(tableName), mapper());
    }

    Optional<AISearchableEntity> findByVectorId(String vectorId) {
        if (!StringUtils.hasText(vectorId)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT * FROM %s WHERE vector_id=:vectorId".formatted(tableName),
                Map.of("vectorId", vectorId),
                mapper()
            ));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    void delete(AISearchableEntity entity) {
        if (entity == null || !StringUtils.hasText(entity.getEntityId())) {
            return;
        }
        deleteByEntityId(entity.getEntityId());
    }

    void deleteByEntityId(String entityId) {
        if (!StringUtils.hasText(entityId)) {
            return;
        }
        jdbcTemplate.update(
            "DELETE FROM %s WHERE entity_id=:entityId".formatted(tableName),
            Map.of("entityId", entityId)
        );
    }

    int deleteByVectorId(String vectorId) {
        if (!StringUtils.hasText(vectorId)) {
            return 0;
        }
        return jdbcTemplate.update(
            "DELETE FROM %s WHERE vector_id=:vectorId".formatted(tableName),
            Map.of("vectorId", vectorId)
        );
    }

    void deleteAll() {
        jdbcTemplate.update("DELETE FROM %s".formatted(tableName), Collections.emptyMap());
    }

    void deleteByVectorIdIsNull() {
        jdbcTemplate.update("DELETE FROM %s WHERE vector_id IS NULL".formatted(tableName), Collections.emptyMap());
    }

    List<AISearchableEntity> findByVectorIdIsNotNull() {
        return jdbcTemplate.query(
            "SELECT * FROM %s WHERE vector_id IS NOT NULL".formatted(tableName),
            mapper()
        );
    }

    List<AISearchableEntity> findByVectorIdIsNull() {
        return jdbcTemplate.query(
            "SELECT * FROM %s WHERE vector_id IS NULL".formatted(tableName),
            mapper()
        );
    }

    List<AISearchableEntity> findByMetadataContainingSnippet(String snippet) {
        if (!StringUtils.hasText(snippet)) {
            return List.of();
        }
        return jdbcTemplate.query(
            "SELECT * FROM %s WHERE metadata IS NOT NULL AND metadata LIKE :pattern".formatted(tableName),
            Map.of("pattern", "%" + snippet + "%"),
            mapper()
        );
    }

    long count() {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM %s".formatted(tableName),
            Collections.emptyMap(),
            Long.class
        );
        return count != null ? count : 0L;
    }

    long countWithVectors() {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM %s WHERE vector_id IS NOT NULL".formatted(tableName),
            Collections.emptyMap(),
            Long.class
        );
        return count != null ? count : 0L;
    }

    Optional<AISearchableEntity> findLatestWithVector() {
        List<AISearchableEntity> results = jdbcTemplate.query(
            "SELECT * FROM %s WHERE vector_updated_at IS NOT NULL ORDER BY vector_updated_at DESC".formatted(tableName),
            mapper()
        );
        return results.stream().findFirst();
    }

    private MapSqlParameterSource baseParams(AISearchableEntity entity) {
        return new MapSqlParameterSource()
            .addValue("id", entity.getId())
            .addValue("entityType", entityType)
            .addValue("entityId", entity.getEntityId())
            .addValue("searchableContent", entity.getSearchableContent())
            .addValue("vectorId", entity.getVectorId())
            .addValue("vectorUpdatedAt", entity.getVectorUpdatedAt())
            .addValue("metadata", entity.getMetadata())
            .addValue("aiAnalysis", entity.getAiAnalysis())
            .addValue("createdAt", entity.getCreatedAt())
            .addValue("updatedAt", entity.getUpdatedAt());
    }

    private RowMapper<AISearchableEntity> mapper() {
        return (ResultSet rs, int rowNum) -> {
            AISearchableEntity entity = new AISearchableEntity();
            entity.setId(rs.getString("id"));
            entity.setEntityType(rs.getString("entity_type"));
            entity.setEntityId(rs.getString("entity_id"));
            entity.setSearchableContent(rs.getString("searchable_content"));
            entity.setVectorId(rs.getString("vector_id"));
            entity.setVectorUpdatedAt(readTimestamp(rs, "vector_updated_at"));
            entity.setMetadata(rs.getString("metadata"));
            entity.setAiAnalysis(rs.getString("ai_analysis"));
            entity.setCreatedAt(readTimestamp(rs, "created_at"));
            entity.setUpdatedAt(readTimestamp(rs, "updated_at"));
            return entity;
        };
    }

    private LocalDateTime readTimestamp(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }
}
