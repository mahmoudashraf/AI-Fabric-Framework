package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.springframework.http.HttpStatus.CONFLICT;

/** Creation evidence, not an adoption API. Pending/unknown historical outcomes never grant ownership. */
@Service
public class ManagedResourceOwnershipService {
    public record Scope(String customerId, String tenantId, String deploymentId, String targetProfileId) {
        public Scope { bounded(customerId,64); bounded(tenantId,64); bounded(deploymentId,64); bounded(targetProfileId,64); }
    }
    public record Identity(String resourceId, String endpoint) {
        public Identity {
            bounded(resourceId,2048);
            if (endpoint != null) {
                bounded(endpoint,2048);
                URI uri;
                try { uri=URI.create(endpoint); } catch (IllegalArgumentException ex) { throw denied("MANAGED_RESOURCE_ENDPOINT_INVALID"); }
                if (!List.of("https","http").contains(uri.getScheme()) || uri.getHost()==null
                    || uri.getRawUserInfo()!=null || uri.getRawQuery()!=null || uri.getRawFragment()!=null)
                    throw denied("MANAGED_RESOURCE_ENDPOINT_INVALID");
            }
        }
    }
    public record Receipt(String id, Scope scope, String provider, String kind, String parent,
                          String name, String state, String resourceId, String endpoint) { }

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate durable;
    private final TransactionTemplate participating;

    public ManagedResourceOwnershipService(JdbcTemplate jdbc, ObjectMapper mapper, PlatformTransactionManager transactions) {
        this.jdbc = jdbc; this.mapper = mapper;
        durable = new TransactionTemplate(transactions);
        durable.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        participating = new TransactionTemplate(transactions);
    }

    public Scope scope(DeploymentEntity deployment, String targetId) {
        if (deployment == null) throw denied("MANAGED_RESOURCE_SCOPE_REQUIRED");
        return new Scope(deployment.getCustomerId(), deployment.getTenantId(), deployment.getId(), targetId);
    }

    public <T> T create(Scope scope, String provider, String kind, String parent, String name,
                        Supplier<T> effect, Function<T,Identity> identity) {
        bounded(provider,32); bounded(kind,32); bounded(parent,1024); bounded(name,255);
        if (parent.startsWith("http:") || parent.startsWith("https:")) new Identity("parent",parent);
        String key = DeploymentEvidenceFingerprint.hash(mapper, mapper.valueToTree(
            List.of(provider,kind,parent,name)));
        String id = durable.execute(tx -> {
            lock();
            if (!jdbc.queryForList("SELECT id FROM platform_managed_resource_ownership WHERE location_key=?", String.class,key).isEmpty())
                throw denied("MANAGED_RESOURCE_CREATE_RECONCILIATION_REQUIRED");
            requireParentAvailable(provider,parent);
            String created = "own-" + UUID.randomUUID();
            var now = Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MICROS));
            jdbc.update("""
                INSERT INTO platform_managed_resource_ownership
                  (id,location_key,provider,resource_kind,parent_identity,resource_name,customer_id,tenant_id,
                   deployment_id,target_profile_id,state,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,'CREATE_PENDING',?,?)
                """,created,key,provider,kind,parent,name,scope.customerId(),scope.tenantId(),scope.deploymentId(),scope.targetProfileId(),now,now);
            return created;
        });
        // The intent commits before remote work; a crash or ambiguous response leaves it blocked, not adoptable.
        T result = effect.get();
        Identity observed = identity.apply(result);
        durable.executeWithoutResult(tx -> {
            lock();
            var current = get(id);
            if (!current.scope().equals(scope) || !"CREATE_PENDING".equals(current.state())) throw denied("MANAGED_RESOURCE_CREATION_CHANGED");
            if (!jdbc.queryForList("""
                SELECT id FROM platform_managed_resource_ownership WHERE provider=? AND resource_kind=?
                 AND resource_identity=? AND COALESCE(endpoint,'')=? AND id<>?
                """,String.class,provider,kind,observed.resourceId(),orEmpty(observed.endpoint()),id).isEmpty())
                throw denied("MANAGED_RESOURCE_ALREADY_CLAIMED");
            jdbc.update("""
                UPDATE platform_managed_resource_ownership SET state='OWNED',resource_identity=?,endpoint=?,updated_at=? WHERE id=?
                """,observed.resourceId(),observed.endpoint(),Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MICROS)),id);
        });
        return result;
    }

    public Receipt requireOwned(Scope scope, String provider, String kind, String resourceId, String endpoint) {
        return participating.execute(tx -> {
            lock();
            var rows = jdbc.queryForList("""
                SELECT id FROM platform_managed_resource_ownership WHERE provider=? AND resource_kind=?
                 AND resource_identity=? AND COALESCE(endpoint,'')=?
                """,String.class,provider,kind,resourceId,orEmpty(endpoint));
            if (rows.size()!=1) throw denied("MANAGED_RESOURCE_OWNERSHIP_REQUIRED");
            Receipt receipt = get(rows.getFirst());
            if (!receipt.scope().equals(scope) || !"OWNED".equals(receipt.state())) throw denied("MANAGED_RESOURCE_OWNERSHIP_CHANGED");
            return receipt;
        });
    }

    public Receipt requireOwnedAt(Scope scope, String provider, String kind, String parent, String name,
                                  String resourceId, String endpoint) {
        Receipt receipt=requireOwned(scope,provider,kind,resourceId,endpoint);
        if (!receipt.parent().equals(parent) || !receipt.name().equals(name))
            throw denied("MANAGED_RESOURCE_LOCATION_CHANGED");
        return receipt;
    }

    public Receipt requireReceipt(Scope scope, String id, String provider, String kind, String resourceId, String endpoint,
                                  boolean deleting) {
        bounded(id,64);
        bounded(resourceId,2048);
        return participating.execute(tx -> {
            lock();
            Receipt r=get(id);
            if (!r.scope().equals(scope) || !r.provider().equals(provider) || !r.kind().equals(kind)
                || !resourceId.equals(r.resourceId()) || !orEmpty(endpoint).equals(orEmpty(r.endpoint()))
                || !("OWNED".equals(r.state()) || deleting && List.of("DELETE_PENDING","DELETED").contains(r.state())))
                throw denied("MANAGED_RESOURCE_OWNERSHIP_CHANGED");
            return r;
        });
    }

    public Receipt receipt(String id) { return get(id); }

    public String releaseTarget(String releaseId) {
        bounded(releaseId,64);
        var values=jdbc.queryForList("SELECT target_profile_id FROM platform_deployment_releases WHERE id=?",String.class,releaseId);
        if (values.size()!=1 || values.getFirst()==null) throw denied("MANAGED_RESOURCE_RELEASE_SCOPE_REQUIRED");
        return values.getFirst();
    }

    public void beginDeletion(Receipt expected) {
        beginDeletion(expected, null);
    }

    public void beginDeletion(Receipt expected, DeploymentProviderResourceHandleEntity deletingHandle) {
        durable.executeWithoutResult(tx -> {
            lock();
            Receipt r=get(expected.id());
            if (!r.equals(expected) || !List.of("OWNED","DELETE_PENDING","DELETED").contains(r.state()))
                throw denied("MANAGED_RESOURCE_OWNERSHIP_CHANGED");
            String excludedHandleId = deletingHandle == null ? null : requireDeletingHandle(r, deletingHandle);
            requireUnreferenced(r, excludedHandleId);
            if (!"DELETED".equals(r.state())) jdbc.update("UPDATE platform_managed_resource_ownership SET state='DELETE_PENDING',updated_at=? WHERE id=?",
                Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MICROS)),r.id());
        });
    }

    public void deleted(String id) {
        durable.executeWithoutResult(tx -> {
            lock();
            Receipt r=get(id);
            if (!List.of("DELETE_PENDING","DELETED").contains(r.state())) throw denied("MANAGED_RESOURCE_DELETE_NOT_CLAIMED");
            jdbc.update("UPDATE platform_managed_resource_ownership SET state='DELETED',updated_at=? WHERE id=?",
                Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MICROS)),id);
        });
    }

    private String requireDeletingHandle(Receipt receipt, DeploymentProviderResourceHandleEntity expected) {
        bounded(expected.getId(),64);
        if (!"coolify".equals(receipt.provider()) || expected.getProviderType()!=DeploymentProviderType.COOLIFY
            || !List.of("APPLICATION","CONNECTOR_APPLICATION","VECTORIZATION_RUNNER_APPLICATION","RUNTIME_POSTGRES_DATABASE")
                .contains(receipt.kind())) throw denied("MANAGED_RESOURCE_DELETING_HANDLE_RECONCILIATION_REQUIRED");
        var rows=jdbc.queryForList("""
            SELECT h.*,d.customer_id,d.tenant_id FROM deployment_provider_resource_handles h
             JOIN platform_deployments d ON d.id=h.deployment_id WHERE h.id=? FOR UPDATE
            """,expected.getId());
        if (rows.size()!=1) throw denied("MANAGED_RESOURCE_DELETING_HANDLE_RECONCILIATION_REQUIRED");
        var stored=rows.getFirst();
        if (!Objects.equals(receipt.scope().customerId(),stored.get("customer_id"))
            || !Objects.equals(receipt.scope().tenantId(),stored.get("tenant_id"))
            || !Objects.equals(receipt.scope().deploymentId(),stored.get("deployment_id"))
            || !Objects.equals(receipt.scope().targetProfileId(),stored.get("target_profile_id"))
            || !Objects.equals("COOLIFY",stored.get("provider_type"))
            || !Objects.equals(receipt.kind(),stored.get("resource_kind"))
            || !Objects.equals(receipt.resourceId(),stored.get("provider_resource_uuid"))
            || !Objects.equals(expected.getDeploymentId(),stored.get("deployment_id"))
            || !Objects.equals(expected.getTargetProfileId(),stored.get("target_profile_id"))
            || !Objects.equals(expected.getResourceKind(),stored.get("resource_kind"))
            || !Objects.equals(expected.getProviderResourceUuid(),stored.get("provider_resource_uuid"))
            || !Objects.equals(expected.getProviderProjectUuid(),stored.get("provider_project_uuid"))
            || !Objects.equals(expected.getProviderEnvironmentUuid(),stored.get("provider_environment_uuid"))
            || !Objects.equals(expected.getProviderServerUuid(),stored.get("provider_server_uuid"))
            || !Objects.equals(expected.getStatus(),stored.get("status")))
            throw denied("MANAGED_RESOURCE_DELETING_HANDLE_RECONCILIATION_REQUIRED");
        try {
            JsonNode metadata=mapper.readTree((String)stored.get("metadata_json"));
            JsonNode placement=mapper.readTree(receipt.parent());
            String nameKey="RUNTIME_POSTGRES_DATABASE".equals(receipt.kind())?"databaseName":"applicationName";
            if (metadata==null || !metadata.isObject()
                || !metadata.path("ownershipReceiptId").isTextual()
                || !receipt.id().equals(metadata.path("ownershipReceiptId").textValue())
                || !receipt.name().equals(metadata.path(nameKey).asText(null))
                || !metadata.equals(mapper.readTree(expected.getMetadataJson()))
                || placement==null || !placement.isArray() || placement.size()!=5
                || !receipt.endpoint().equals(placement.path(0).asText(null))
                || !Objects.equals(stored.get("provider_project_uuid"),placement.path(1).asText(null))
                || !Objects.equals(stored.get("provider_environment_uuid"),placement.path(2).asText(null))
                || !Objects.equals(stored.get("provider_server_uuid"),placement.path(3).asText(null)))
                throw denied("MANAGED_RESOURCE_DELETING_HANDLE_RECONCILIATION_REQUIRED");
        } catch (Exception ex) {
            throw denied("MANAGED_RESOURCE_DELETING_HANDLE_RECONCILIATION_REQUIRED");
        }
        return expected.getId();
    }

    private void requireUnreferenced(Receipt r, String excludedHandleId) {
        if (r.endpoint()!=null && List.of("CLUSTER","INDEX").contains(r.kind())) {
            Long children=jdbc.queryForObject("""
                SELECT COUNT(*) FROM platform_managed_resource_ownership WHERE provider=? AND parent_identity=? AND state<>'DELETED' AND id<>?
                """,Long.class,r.provider(),r.endpoint(),r.id());
            if (children==null || children!=0) throw denied("MANAGED_RESOURCE_CHILDREN_RETAINED");
        }
        if ("DATABASE_API_KEY".equals(r.kind())) {
            Long collections=jdbc.queryForObject("""
                SELECT COUNT(*) FROM platform_managed_resource_ownership WHERE provider=? AND parent_identity=?
                 AND resource_kind='COLLECTION' AND state<>'DELETED'
                """,Long.class,r.provider(),r.endpoint());
            if (collections==null || collections!=0) throw denied("MANAGED_RESOURCE_CHILDREN_RETAINED");
        }
        boolean root=List.of("CLUSTER","INDEX","DATABASE_API_KEY").contains(r.kind());
        String reference="COLLECTION".equals(r.kind())?r.endpoint()+"/collections/"+r.resourceId():r.resourceId();
        Long refs=jdbc.queryForObject("""
            SELECT COUNT(*) FROM platform_deployment_managed_vector_resources v
             LEFT JOIN platform_deployment_releases rel ON rel.id=v.deployment_release_id
             WHERE v.vendor=? AND (v.resource_reference=? OR (?=TRUE AND v.endpoint=?))
               AND (v.deployment_id<>? OR rel.target_profile_id IS NULL OR rel.target_profile_id<>?)
            """,Long.class,r.provider(),reference,root,r.endpoint(),r.scope().deploymentId(),r.scope().targetProfileId());
        if (refs==null || refs!=0) throw denied("MANAGED_RESOURCE_REFERENCED");
        String handleReferences="""
            SELECT COUNT(*) FROM deployment_provider_resource_handles
             WHERE status<>'DELETED' AND (provider_resource_uuid=? OR (?=TRUE AND (fqdn=? OR fqdn=?)))
            """;
        Long handles=excludedHandleId==null
            ? jdbc.queryForObject(handleReferences,Long.class,r.resourceId(),root,r.endpoint(),r.endpoint()==null?null:r.endpoint()+"/")
            : jdbc.queryForObject(handleReferences+" AND id<>?",Long.class,r.resourceId(),root,r.endpoint(),r.endpoint()==null?null:r.endpoint()+"/",excludedHandleId);
        if (handles==null || handles!=0) throw denied("MANAGED_RESOURCE_REFERENCED");
        if ("railway".equals(r.provider())) requireNoForeignRailwayRelease(r);
    }

    public boolean retainedBindingReferencesSecret(String secretName) {
        bounded(secretName,255);
        for (String json:jdbc.queryForList("SELECT metadata_json FROM deployment_provider_resource_handles WHERE status<>'DELETED'",String.class)) {
            if (json==null || json.isBlank()) continue;
            try {
                if (containsExactValue(mapper.readTree(json),secretName)) return true;
            } catch (Exception ex) {
                throw denied("MANAGED_RESOURCE_REFERENCE_RECONCILIATION_REQUIRED");
            }
        }
        return false;
    }

    private boolean containsExactValue(com.fasterxml.jackson.databind.JsonNode node,String value) {
        if (node==null) return false;
        if (node.isTextual()) return value.equals(node.textValue());
        for (var child:node) if (containsExactValue(child,value)) return true;
        return false;
    }

    private void requireNoForeignRailwayRelease(Receipt receipt) {
        var releases=jdbc.queryForList("""
            SELECT provisioning_details_json FROM platform_deployment_releases
             WHERE deployment_id<>? OR target_profile_id IS NULL OR target_profile_id<>?
            """,String.class,receipt.scope().deploymentId(),receipt.scope().targetProfileId());
        for (String json:releases) {
            if (json==null || json.isBlank()) continue;
            com.fasterxml.jackson.databind.JsonNode details;
            try { details=mapper.readTree(json); }
            catch (Exception ex) { throw denied("MANAGED_RESOURCE_REFERENCE_RECONCILIATION_REQUIRED"); }
            var railway=details.path("railway");
            boolean referenced=receipt.resourceId().equals(railway.path("projectId").asText())
                || receipt.resourceId().equals(railway.path("environmentId").asText());
            for (var service:railway.path("services"))
                referenced |= receipt.resourceId().equals(service.path("serviceId").asText());
            if (referenced) throw denied("MANAGED_RESOURCE_REFERENCED");
        }
    }

    private void requireParentAvailable(String provider,String parent) {
        Long blocked=jdbc.queryForObject("""
            SELECT COUNT(*) FROM platform_managed_resource_ownership WHERE provider=? AND endpoint=?
             AND resource_kind IN ('CLUSTER','INDEX') AND state<>'OWNED'
            """,Long.class,provider,parent);
        if (blocked==null || blocked!=0) throw denied("MANAGED_RESOURCE_PARENT_UNAVAILABLE");
    }

    private Receipt get(String id) {
        var rows=jdbc.query("SELECT * FROM platform_managed_resource_ownership WHERE id=?",(rs,n)->new Receipt(rs.getString("id"),
            new Scope(rs.getString("customer_id"),rs.getString("tenant_id"),rs.getString("deployment_id"),rs.getString("target_profile_id")),
            rs.getString("provider"),rs.getString("resource_kind"),rs.getString("parent_identity"),rs.getString("resource_name"),
            rs.getString("state"),rs.getString("resource_identity"),rs.getString("endpoint")),id);
        if (rows.size()!=1) throw denied("MANAGED_RESOURCE_OWNERSHIP_REQUIRED");
        return rows.getFirst();
    }
    private void lock() { jdbc.queryForObject("SELECT id FROM platform_managed_resource_ownership_mutex WHERE id=1 FOR UPDATE",Integer.class); }
    private static String orEmpty(String value) { return value==null?"":value; }
    private static void bounded(String value,int max) {
        if (value==null || value.isBlank() || value.length()>max || !value.equals(value.trim()) || value.chars().anyMatch(Character::isISOControl))
            throw denied("MANAGED_RESOURCE_SCOPE_REQUIRED");
    }
    private static ResponseStatusException denied(String code) { return new ResponseStatusException(CONFLICT,code); }
}
