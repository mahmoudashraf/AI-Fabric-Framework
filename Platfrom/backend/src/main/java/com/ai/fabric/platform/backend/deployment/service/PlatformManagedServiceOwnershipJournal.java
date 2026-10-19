package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.springframework.http.HttpStatus.CONFLICT;

/** Service registry ownership is independent of customer deployment/tenant ownership. */
@Service
public class PlatformManagedServiceOwnershipJournal {
    record Owner(String registryId, String serviceRef, String serviceKind, String targetProfileId) {
        Owner { required(registryId,64); required(serviceRef,128); required(serviceKind,128); required(targetProfileId,128); }
    }
    record Location(String endpoint, String parent, String name) {
        Location { required(endpoint,2048); required(parent,1024); required(name,255); }
    }
    record Receipt(String id, Owner owner, Location location, long generation, String uuid, String state) { }

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate durable;

    public PlatformManagedServiceOwnershipJournal(JdbcTemplate jdbc, ObjectMapper mapper, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        durable = new TransactionTemplate(transactions);
        durable.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    Optional<Receipt> latest(String registryId) {
        required(registryId,64);
        return durable.execute(tx -> jdbc.query("""
            SELECT * FROM platform_managed_service_ownership WHERE registry_id=? ORDER BY generation DESC
            """, (rs,n) -> receipt(rs), registryId).stream().findFirst());
    }

    Receipt require(String id, Owner owner, Location location, String uuid, boolean deleting) {
        return durable.execute(tx -> {
            Receipt receipt = get(id);
            if (!receipt.owner().equals(owner) || !receipt.location().equals(location) || !Objects.equals(receipt.uuid(),uuid)
                || !("OWNED".equals(receipt.state()) || deleting && List.of("DELETE_PENDING","DELETED").contains(receipt.state()))) throw denied();
            return receipt;
        });
    }

    <T> T create(Owner owner, Location location, Runnable guard, Supplier<T> effect, Function<T,String> identity) {
        Receipt pending = durable.execute(tx -> {
            lock();
            guard.run();
            var latest = latestInTransaction(owner.registryId());
            long generation = 1;
            if (latest.isPresent()) {
                var previous = latest.get();
                if (!previous.owner().equals(owner) || !previous.location().equals(location) || !"DELETED".equals(previous.state())) throw denied();
                generation = Math.addExact(previous.generation(),1);
            }
            String key = key(List.of("coolify",location.endpoint(),location.parent(),location.name()));
            if (!jdbc.queryForList("SELECT id FROM platform_managed_service_ownership WHERE active_location_key=? OR active_registry_id=?",
                String.class,key,owner.registryId()).isEmpty()) throw denied();
            String id = "sown-" + UUID.randomUUID();
            jdbc.update("""
                INSERT INTO platform_managed_service_ownership
                  (id,registry_id,service_ref,service_kind,target_profile_id,generation,provider,endpoint,parent_identity,
                   resource_name,state,active_registry_id,active_location_key,created_at,updated_at)
                VALUES (?,?,?,?,?,?,'coolify',?,?,?,'CREATE_PENDING',?,?,?,?)
                """,id,owner.registryId(),owner.serviceRef(),owner.serviceKind(),owner.targetProfileId(),generation,
                location.endpoint(),location.parent(),location.name(),owner.registryId(),key,now(),now());
            return get(id);
        });
        // Neither a failed effect nor a failed readback can make this intent adoptable or retryable.
        T result = effect.get();
        String uuid = required(identity.apply(result),128);
        durable.executeWithoutResult(tx -> {
            lock();
            guard.run();
            if (!get(pending.id()).equals(pending)) throw denied();
            String identityKey = key(List.of("coolify",location.endpoint(),uuid));
            if (!jdbc.queryForList("SELECT id FROM platform_managed_service_ownership WHERE identity_key=?",String.class,identityKey).isEmpty()) throw denied();
            jdbc.update("""
                UPDATE platform_managed_service_ownership SET resource_uuid=?,identity_key=?,state='OWNED',updated_at=? WHERE id=?
                """,uuid,identityKey,now(),pending.id());
        });
        return result;
    }

    void beginDeletion(Receipt expected, Runnable guard) {
        durable.executeWithoutResult(tx -> {
            lock();
            if (!get(expected.id()).equals(expected) || !List.of("OWNED","DELETE_PENDING","DELETED").contains(expected.state())) throw denied();
            guard.run();
            if (!"DELETED".equals(expected.state())) jdbc.update("""
                UPDATE platform_managed_service_ownership SET state='DELETE_PENDING',updated_at=? WHERE id=?
                """,now(),expected.id());
        });
    }

    <T> T mutate(Receipt expected, Runnable guard, Supplier<T> effect) {
        durable.executeWithoutResult(tx -> {
            lock();
            if (!"OWNED".equals(expected.state()) || !get(expected.id()).equals(expected)) throw denied();
            guard.run();
            jdbc.update("UPDATE platform_managed_service_ownership SET state='MUTATION_PENDING',updated_at=? WHERE id=?",now(),expected.id());
        });
        T result = effect.get();
        durable.executeWithoutResult(tx -> {
            lock();
            Receipt current = get(expected.id());
            if (!"MUTATION_PENDING".equals(current.state()) || !current.owner().equals(expected.owner())
                || !current.location().equals(expected.location()) || !Objects.equals(current.uuid(),expected.uuid())) throw denied();
            guard.run();
            jdbc.update("UPDATE platform_managed_service_ownership SET state='OWNED',updated_at=? WHERE id=?",now(),expected.id());
        });
        return result;
    }

    void deleted(Receipt expected, Runnable guard) {
        durable.executeWithoutResult(tx -> {
            lock();
            Receipt current = get(expected.id());
            if (!current.equals(expected) || !List.of("DELETE_PENDING","DELETED").contains(current.state())) throw denied();
            guard.run();
            jdbc.update("""
                UPDATE platform_managed_service_ownership SET state='DELETED',active_registry_id=NULL,
                  active_location_key=NULL,updated_at=? WHERE id=?
                """,now(),current.id());
        });
    }

    private Optional<Receipt> latestInTransaction(String id) {
        return jdbc.query("SELECT * FROM platform_managed_service_ownership WHERE registry_id=? ORDER BY generation DESC",
            (rs,n) -> receipt(rs),id).stream().findFirst();
    }

    private Receipt get(String id) {
        required(id,64);
        var rows = jdbc.query("SELECT * FROM platform_managed_service_ownership WHERE id=?",(rs,n) -> receipt(rs),id);
        if (rows.size()!=1) throw denied();
        return rows.getFirst();
    }

    private Receipt receipt(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Receipt(rs.getString("id"),new Owner(rs.getString("registry_id"),rs.getString("service_ref"),
            rs.getString("service_kind"),rs.getString("target_profile_id")),
            new Location(rs.getString("endpoint"),rs.getString("parent_identity"),rs.getString("resource_name")),
            rs.getLong("generation"),rs.getString("resource_uuid"),rs.getString("state"));
    }
    private String key(List<String> values) { return DeploymentEvidenceFingerprint.hash(mapper,mapper.valueToTree(values)); }
    private void lock() { jdbc.queryForObject("SELECT id FROM platform_managed_service_ownership_mutex WHERE id=1 FOR UPDATE",Integer.class); }
    private static Timestamp now() { return Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MICROS)); }
    static String required(String value,int max) {
        if (value==null || value.isBlank() || value.length()>max || !value.equals(value.trim())
            || value.chars().anyMatch(Character::isISOControl)) throw denied();
        return value;
    }
    static ResponseStatusException denied() { return new ResponseStatusException(CONFLICT,"PLATFORM_SERVICE_RECONCILIATION_REQUIRED"); }
}
