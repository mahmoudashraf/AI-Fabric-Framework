package com.ai.fabric.platform.backend.deployment.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.CONFLICT;

/** Deployment locks precede this cluster lock; it also covers a cluster with no handle rows yet. */
@Component
public class ExternalVectorClusterLocks {
    public static final String RELEASED = "RELEASED_UNUSED_TRANSFER";
    private final JdbcTemplate jdbc;
    public ExternalVectorClusterLocks(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(String clusterId) {
        Boolean postgres = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection ->
            connection.getMetaData().getDatabaseProductName().equals("PostgreSQL"));
        if (Boolean.TRUE.equals(postgres)) {
            jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,205))", rs -> { }, "external-vector:" + clusterId);
        } else {
            jdbc.query("SELECT id FROM deployment_provider_resource_handles WHERE provider_resource_uuid=? FOR UPDATE",
                rs -> { }, clusterId);
        }
    }
    public void requireNotReleased(String clusterId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM deployment_provider_resource_handles WHERE resource_kind='EXTERNAL_VECTOR_STORAGE_BINDING' AND provider_resource_uuid=? AND status=?",
            Integer.class, clusterId, RELEASED);
        if (count == null || count != 0) throw new ResponseStatusException(CONFLICT, "EXTERNAL_VECTOR_CLUSTER_RELEASED_FOR_TRANSFER");
    }
}
