package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductServiceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import static com.ai.fabric.platform.backend.deployment.service.PlatformManagedServiceOwnershipJournal.denied;
import static com.ai.fabric.platform.backend.deployment.service.PlatformManagedServiceOwnershipJournal.required;

/** Authority for shared services uses the real registry identity, never a synthetic customer scope. */
@Service
public class PlatformManagedServiceOwnership {
    static final String RECEIPT = "serviceOwnershipReceiptId";
    record Context(PlatformManagedServiceOwnershipJournal.Owner owner, CoolifyConnection connection,
                   CoolifyResourceOwnership.Location placement, PlatformManagedServiceOwnershipJournal.Location location,
                   String targetFingerprint) { }
    record Binding(Context context, PlatformManagedServiceOwnershipJournal.Receipt receipt, String recordedUuid, String recordedReceipt) {
        String uuid() { return receipt.uuid(); }
    }
    private record Registry(String id, String ref, String kind, String status, String baseUrl, String privateUrl,
                            String railwayProject, String railwayEnvironment, String railwayService, JsonNode details) { }

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PlatformManagedServiceOwnershipJournal journal;
    private final CoolifyResourceOwnership readback;
    private final CoolifyApiClient api;
    private final TransactionTemplate reads;

    public PlatformManagedServiceOwnership(JdbcTemplate jdbc, ObjectMapper mapper,
            PlatformManagedServiceOwnershipJournal journal, CoolifyResourceOwnership readback,
            CoolifyApiClient api, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.journal = journal;
        this.readback = readback;
        this.api = api;
        reads = new TransactionTemplate(transactions);
        reads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    Context context(PlatformManagedProductServiceEntity service, DeploymentTargetProfileEntity profile,
                    CoolifyConnection connection, String name) {
        if (profile == null || profile.getProviderType() != DeploymentProviderType.COOLIFY
            || !profile.isActive() || !profile.isPlatformServicesAllowed()) throw denied();
        var owner = new PlatformManagedServiceOwnershipJournal.Owner(service.getId(),service.getServiceRef(),
            service.getServiceKind(),profile.getId());
        var placement = readback.location(connection,connection.config().projectUuid(),connection.config().environmentUuid());
        String fingerprint = profileFingerprint(profile);
        if (!fingerprint.equals(targetFingerprint(profile.getId()))) throw denied();
        var context = new Context(owner,connection,placement,new PlatformManagedServiceOwnershipJournal.Location(
            placement.endpoint(),readback.parent(placement),required(name,255)),fingerprint);
        registry(context);
        return context;
    }

    Binding existing(Context context) {
        Registry row = registry(context);
        String uuid = required(row.details().path("coolifyApplicationUuid").asText(null),128);
        String id = required(row.details().path(RECEIPT).asText(null),64);
        return new Binding(context,journal.require(id,context.owner(),context.location(),uuid,true),uuid,id);
    }

    Binding reconcile(Context context, String domains, Supplier<String> create) {
        Registry row = registry(context);
        var previous = journal.latest(context.owner().registryId());
        if (previous.isPresent() && !"DELETED".equals(previous.get().state())) {
            Binding binding = existing(context);
            observe(binding,false);
            return binding;
        }
        requireFresh(context,row,previous);
        selectTarget(context,previous);
        row = registry(context);
        parents(context);
        var application = journal.create(context.owner(),context.location(),() -> {
            requireFresh(context,registry(context),previous);
        },() -> {
            // Inventory collisions block creation; they never identify a resource to adopt or delete.
            for (var other : api.listApplications(context.connection())) {
                if (context.location().name().equalsIgnoreCase(other.name()) || domainsOverlap(domains,other.fqdn())) throw denied();
            }
            String uuid = required(create.get(),128);
            return live(context,uuid,false).orElseThrow(PlatformManagedServiceOwnershipJournal::denied);
        },CoolifyApplicationSummary::uuid);
        var receipt = journal.latest(context.owner().registryId()).orElseThrow(PlatformManagedServiceOwnershipJournal::denied);
        if (!application.uuid().equals(receipt.uuid()) || !"OWNED".equals(receipt.state())) throw denied();
        Registry expected = row;
        reads.executeWithoutResult(tx -> {
            jdbc.queryForObject("SELECT id FROM platform_managed_product_services WHERE id=? FOR UPDATE",String.class,context.owner().registryId());
            Registry current = registry(context);
            if (!current.equals(expected)) throw denied();
            var details = (com.fasterxml.jackson.databind.node.ObjectNode) current.details().deepCopy();
            details.put(RECEIPT,receipt.id());
            details.put("sharedOwnershipState","OWNED");
            details.put("coolifyApplicationUuid",receipt.uuid());
            details.put("coolifyProjectUuid",context.placement().project());
            details.put("coolifyEnvironmentUuid",context.placement().environment());
            jdbc.update("UPDATE platform_managed_product_services SET details_json=? WHERE id=?",details.toString(),current.id());
        });
        return new Binding(context,receipt,receipt.uuid(),receipt.id());
    }

    CoolifyApplicationSummary observe(Binding binding) { return observe(binding,false).orElseThrow(PlatformManagedServiceOwnershipJournal::denied); }

    Optional<CoolifyApplicationSummary> observe(Binding binding, boolean deleting) {
        requireBindingRegistry(binding);
        var receipt = journal.require(binding.receipt().id(),binding.context().owner(),binding.context().location(),binding.uuid(),deleting);
        boolean allowAbsent = deleting && List.of("DELETE_PENDING","DELETED").contains(receipt.state());
        var observed = live(binding.context(),binding.uuid(),allowAbsent);
        if ("DELETED".equals(receipt.state()) && observed.isPresent()) throw denied();
        return observed;
    }

    Binding beginDeletion(Binding binding) {
        observe(binding,true);
        journal.beginDeletion(binding.receipt(),() -> {
            requireBindingRegistry(binding);
            unreferenced(binding.context(),binding.uuid(),null);
            markPending(binding,"DELETE_PENDING");
        });
        return new Binding(binding.context(),journal.require(binding.receipt().id(),binding.context().owner(),
            binding.context().location(),binding.uuid(),true),binding.recordedUuid(),binding.recordedReceipt());
    }

    void deleted(Binding binding) {
        if (observe(binding,true).isPresent()) throw denied();
        journal.deleted(binding.receipt(),() -> {
            requireBindingRegistry(binding);
            unreferenced(binding.context(),binding.uuid(),null);
        });
    }

    <T> T mutate(Binding binding, Supplier<T> effect) {
        var application = observe(binding);
        return journal.mutate(binding.receipt(),() -> {
            requireBindingRegistry(binding);
            unreferenced(binding.context(),binding.uuid(),application.fqdn());
            markPending(binding,"MUTATION_PENDING");
        },() -> {
            T result = effect.get();
            // Read placement directly while the durable receipt is intentionally mutation-pending.
            requireBindingRegistry(binding);
            live(binding.context(),binding.uuid(),false);
            return result;
        });
    }

    CoolifyApplicationSummary observeDuringMutation(Binding binding) {
        requireBindingRegistry(binding);
        var current = journal.latest(binding.context().owner().registryId()).orElseThrow(PlatformManagedServiceOwnershipJournal::denied);
        if (!current.id().equals(binding.receipt().id()) || !"MUTATION_PENDING".equals(current.state())
            || !Objects.equals(current.uuid(),binding.uuid()) || !current.location().equals(binding.context().location())) throw denied();
        return live(binding.context(),binding.uuid(),false).orElseThrow(PlatformManagedServiceOwnershipJournal::denied);
    }

    void requireUnusedCredentialName(Binding binding, String name) {
        required(name,255);
        reads.executeWithoutResult(tx -> {
            requireBindingRegistry(binding);
            var current = journal.latest(binding.context().owner().registryId()).orElseThrow(PlatformManagedServiceOwnershipJournal::denied);
            if (!current.id().equals(binding.receipt().id()) || !"MUTATION_PENDING".equals(current.state())) throw denied();
            if (jdbc.queryForObject("SELECT secret_name FROM platform_managed_product_services WHERE id=?",
                String.class,binding.context().owner().registryId())!=null) throw denied();
            if (!jdbc.queryForList("SELECT id FROM platform_managed_product_services WHERE secret_name=? AND id<>?",
                String.class,name,binding.context().owner().registryId()).isEmpty()) throw denied();
            unreferenced(binding.context(),name,null);
        });
    }

    void recordGeneratedCredentialName(Binding binding, String name) {
        reads.executeWithoutResult(tx -> {
            requireUnusedCredentialName(binding,name);
            if (jdbc.update("""
                UPDATE platform_managed_service_ownership SET credential_secret_name=?
                 WHERE id=? AND registry_id=? AND state='MUTATION_PENDING' AND credential_secret_name IS NULL
                """,name,binding.receipt().id(),binding.context().owner().registryId())!=1) throw denied();
            if (jdbc.update("UPDATE platform_managed_product_services SET secret_name=? WHERE id=? AND secret_name IS NULL",
                name,binding.context().owner().registryId())!=1) throw denied();
        });
    }

    void requireCredentialMutation(Binding binding, String name) {
        required(name,255);
        reads.executeWithoutResult(tx -> {
            requireBindingRegistry(binding);
            if (!name.equals(jdbc.queryForObject("SELECT secret_name FROM platform_managed_product_services WHERE id=?",
                String.class,binding.context().owner().registryId()))) throw denied();
            var owner = binding.context().owner();
            var claims = jdbc.queryForList("""
                SELECT registry_id,service_ref,service_kind,target_profile_id,state
                  FROM platform_managed_service_ownership WHERE credential_secret_name=?
                """,name);
            if (claims.size()!=1) throw denied();
            var claim = claims.getFirst();
            if (!owner.registryId().equals(claim.get("registry_id")) || !owner.serviceRef().equals(claim.get("service_ref"))
                || !owner.serviceKind().equals(claim.get("service_kind")) || !owner.targetProfileId().equals(claim.get("target_profile_id"))
                || !List.of("OWNED","DELETED").contains(claim.get("state"))) throw denied();
            if (!jdbc.queryForList("SELECT id FROM platform_managed_product_services WHERE secret_name=? AND id<>?",
                String.class,name,owner.registryId()).isEmpty()) throw denied();
            unreferenced(binding.context(),name,null);
        });
    }

    private void requireBindingRegistry(Binding binding) {
        Registry row = registry(binding.context());
        if (!Objects.equals(binding.recordedUuid(),row.details().path("coolifyApplicationUuid").asText(null))
            || !Objects.equals(binding.recordedReceipt(),row.details().path(RECEIPT).asText(null))) throw denied();
    }

    private void markPending(Binding binding, String state) {
        Registry row = registry(binding.context());
        var details = (com.fasterxml.jackson.databind.node.ObjectNode) row.details().deepCopy();
        details.put("sharedOwnershipState",state);
        details.put("lastReconcileStatus","RECONCILIATION_REQUIRED");
        if ("DELETE_PENDING".equals(state)) details.put("lastDecommissionStatus","PENDING");
        jdbc.update("UPDATE platform_managed_product_services SET status='RECONCILIATION_REQUIRED',details_json=? WHERE id=?",
            details.toString(),row.id());
    }

    private Registry registry(Context context) {
        return reads.execute(tx -> {
            if (!context.targetFingerprint().equals(targetFingerprint(context.owner().targetProfileId()))) throw denied();
            var rows = jdbc.query("""
                SELECT id,service_ref,service_kind,status,base_url,private_network_url,railway_project_id,
                       railway_environment_id,railway_service_id,details_json
                  FROM platform_managed_product_services WHERE id=?
                """,(rs,n) -> new Registry(rs.getString("id"),rs.getString("service_ref"),rs.getString("service_kind"),
                rs.getString("status"),rs.getString("base_url"),rs.getString("private_network_url"),rs.getString("railway_project_id"),
                rs.getString("railway_environment_id"),rs.getString("railway_service_id"),document(rs.getString("details_json"))),
                context.owner().registryId());
            if (rows.size()!=1) throw denied();
            Registry row = rows.getFirst();
            if (!context.owner().serviceRef().equals(row.ref()) || !context.owner().serviceKind().equals(row.kind())
                || PlatformManagedProductServiceService.OWNED_DEPENDENCY_SERVICE.equalsIgnoreCase(row.kind())
                || text(row.railwayProject()) || text(row.railwayEnvironment()) || text(row.railwayService())) throw denied();
            JsonNode details = row.details();
            if (details.hasNonNull("targetProfileId") && !context.owner().targetProfileId().equals(details.path("targetProfileId").asText())) throw denied();
            if (details.hasNonNull("providerType") && !"COOLIFY".equalsIgnoreCase(details.path("providerType").asText())) throw denied();
            if (details.hasNonNull("coolifyProjectUuid") && !context.placement().project().equals(details.path("coolifyProjectUuid").asText())) throw denied();
            if (details.hasNonNull("coolifyEnvironmentUuid") && !context.placement().environment().equals(details.path("coolifyEnvironmentUuid").asText())) throw denied();
            return row;
        });
    }

    private void selectTarget(Context context, Optional<PlatformManagedServiceOwnershipJournal.Receipt> previous) {
        reads.executeWithoutResult(tx -> {
            jdbc.queryForObject("SELECT id FROM platform_managed_product_services WHERE id=? FOR UPDATE",String.class,context.owner().registryId());
            Registry row = registry(context);
            requireFresh(context,row,previous);
            var details = (com.fasterxml.jackson.databind.node.ObjectNode) row.details().deepCopy();
            details.put("targetProfileId",context.owner().targetProfileId());
            details.put("providerType","COOLIFY");
            details.put("sharedOwnershipState","CREATE_PENDING");
            details.put("lastReconcileStatus","RECONCILIATION_REQUIRED");
            jdbc.update("UPDATE platform_managed_product_services SET details_json=? WHERE id=?",details.toString(),row.id());
        });
    }

    private String targetFingerprint(String id) {
        return reads.execute(tx -> {
            var rows = jdbc.query("""
                SELECT provider_type,active,platform_services_allowed,provider_config_json,resource_defaults_json,
                       credential_ref_id,environment_name,source_strategy,network_policy_json
                  FROM deployment_target_profiles WHERE id=?
                """,(rs,n) -> targetFingerprint(rs.getString("provider_type"),rs.getBoolean("active"),
                rs.getBoolean("platform_services_allowed"),rs.getString("provider_config_json"),rs.getString("resource_defaults_json"),
                rs.getString("credential_ref_id"),rs.getString("environment_name"),rs.getString("source_strategy"),rs.getString("network_policy_json")),id);
            if (rows.size()!=1) throw denied();
            return rows.getFirst();
        });
    }

    private String profileFingerprint(DeploymentTargetProfileEntity profile) {
        return targetFingerprint(profile.getProviderType().name(),profile.isActive(),profile.isPlatformServicesAllowed(),
            profile.getProviderConfigJson(),profile.getResourceDefaultsJson(),profile.getCredentialRefId(),
            profile.getEnvironmentName(),profile.getSourceStrategy(),profile.getNetworkPolicyJson());
    }

    private String targetFingerprint(String provider, boolean active, boolean allowed, String... fields) {
        var values = new java.util.ArrayList<Object>(List.of(provider,active,allowed));
        for (String field : fields) values.add(field);
        return DeploymentEvidenceFingerprint.hash(mapper,mapper.valueToTree(values));
    }

    private void requireFresh(Context context, Registry row, Optional<PlatformManagedServiceOwnershipJournal.Receipt> previous) {
        if (previous.isPresent()) {
            var receipt = previous.get();
            if (!receipt.owner().equals(context.owner()) || !receipt.location().equals(context.location()) || !"DELETED".equals(receipt.state())) throw denied();
            String recorded = row.details().path("coolifyApplicationUuid").asText(null);
            if (text(recorded) && (!recorded.equals(receipt.uuid()) || !receipt.id().equals(row.details().path(RECEIPT).asText(null)))) throw denied();
        } else if (!"CREATED".equals(row.status()) || text(row.baseUrl()) || text(row.privateUrl())
            || row.details().hasNonNull("coolifyApplicationUuid") || row.details().hasNonNull(RECEIPT)
            || row.details().hasNonNull("coolifyProjectUuid") || row.details().hasNonNull("coolifyEnvironmentUuid")
            || row.details().hasNonNull("coolifyFqdn")) throw denied();
    }

    private record Parents(JsonNode project, JsonNode environment) { }
    private Parents parents(Context context) {
        var location = context.placement();
        var projects = api.listProjects(context.connection()).stream().filter(p -> location.project().equals(p.uuid())).toList();
        if (projects.size()!=1) throw denied();
        JsonNode project = projects.getFirst().raw();
        var environment = api.getEnvironment(context.connection(),location.project(),location.environment())
            .orElseThrow(PlatformManagedServiceOwnershipJournal::denied);
        if (!location.project().equals(environment.projectUuid()) || !location.environment().equals(environment.uuid())
            || project == null || !location.project().equals(project.path("uuid").asText())
            || !project.path("id").isIntegralNumber() || project.path("id").asLong() <= 0
            || environment.raw() == null || !location.environment().equals(environment.raw().path("uuid").asText())
            || !environment.raw().path("project_id").isIntegralNumber()
            || project.path("id").asLong()!=environment.raw().path("project_id").asLong()
            || !environment.raw().path("id").isIntegralNumber() || environment.raw().path("id").asLong() <= 0
            || !environment.raw().path("applications").isArray()) throw denied();
        JsonNode server = api.getServer(context.connection(),location.server());
        if (server == null || !location.server().equals(server.path("uuid").asText(null))) throw denied();
        var destination = api.resolveDestinationPlacement(context.connection(),location.server(),location.destination())
            .orElseThrow(PlatformManagedServiceOwnershipJournal::denied);
        if (!location.destination().equals(destination.destinationUuid()) || !location.server().equals(destination.serverUuid())) throw denied();
        return new Parents(project,environment.raw());
    }

    private Optional<CoolifyApplicationSummary> live(Context context, String uuid, boolean allowAbsent) {
        var parents = parents(context);
        var observed = api.getApplication(context.connection(),uuid);
        if (observed.isEmpty()) {
            if (!allowAbsent) throw denied();
            readback.requireAbsent(context.placement(),"APPLICATION",uuid,parents.project(),parents.environment());
            unreferenced(context,uuid,null);
            return Optional.empty();
        }
        var app = observed.get();
        readback.requireLive(context.placement(),"APPLICATION",context.location().name(),uuid,app.uuid(),app.name(),
            app.raw(),parents.project(),parents.environment());
        if (!Objects.equals("Managed product service " + context.owner().serviceRef(),app.raw().path("description").asText(null))) throw denied();
        // Shared application creates have no volume journal. Unknown mounts cannot become owned implicitly.
        if (!api.listApplicationStorages(context.connection(),uuid).isEmpty()) throw denied();
        unreferenced(context,uuid,app.fqdn());
        return observed;
    }

    private void unreferenced(Context context, String uuid, String fqdn) {
        reads.executeWithoutResult(tx -> {
            Registry own = registry(context);
            String recorded = own.details().path("coolifyFqdn").asText(null);
            String domains = text(fqdn) ? fqdn : text(recorded) ? recorded : own.baseUrl();
            for (var row : jdbc.queryForList("SELECT id,details_json,base_url,private_network_url FROM platform_managed_product_services")) {
                if (context.owner().registryId().equals(row.get("id"))) continue;
                if (references(document((String)row.get("details_json")),uuid,domains) || scalarReference(row.get("base_url"),uuid,domains)
                    || scalarReference(row.get("private_network_url"),uuid,domains)) throw denied();
            }
            for (String json : jdbc.queryForList("SELECT state_json FROM deployment_managed_dependency_graphs",String.class))
                if (references(document(json),uuid,domains)) throw denied();
            for (var row : jdbc.queryForList("SELECT provider_resource_uuid,fqdn,metadata_json FROM deployment_provider_resource_handles WHERE status IS NULL OR status<>'DELETED'"))
                if (uuid.equals(row.get("provider_resource_uuid")) || references(document((String)row.get("metadata_json")),uuid,domains)
                    || scalarReference(row.get("fqdn"),uuid,domains)) throw denied();
            for (String json : jdbc.queryForList("SELECT provisioning_details_json FROM platform_deployment_releases",String.class))
                if (references(document(json),uuid,domains)) throw denied();
            for (var row : jdbc.queryForList("SELECT resource_reference,endpoint,details_json FROM platform_deployment_managed_vector_resources"))
                if (scalarReference(row.get("resource_reference"),uuid,domains) || scalarReference(row.get("endpoint"),uuid,domains)
                    || references(document((String)row.get("details_json")),uuid,domains)) throw denied();
            for (var row : jdbc.queryForList("SELECT resource_identity FROM platform_managed_resource_ownership WHERE provider='coolify' AND endpoint=?",context.placement().endpoint())) {
                String identity = (String)row.get("resource_identity");
                if (uuid.equals(identity) || identity != null && identity.startsWith("{") && references(document(identity),uuid,domains)) throw denied();
            }
        });
    }

    private boolean scalarReference(Object value, String uuid, String fqdn) {
        return value instanceof String text && (uuid.equals(text) || domainsOverlap(text,fqdn));
    }
    private boolean domainsOverlap(String a, String b) {
        if (!text(a) || !text(b)) return false;
        for (String left : a.split(",")) for (String right : b.split(",")) {
            String origin = origin(left);
            if (origin != null && origin.equals(origin(right))) return true;
        }
        return false;
    }
    private String origin(String value) {
        try {
            var uri = java.net.URI.create(value.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(java.util.Locale.ROOT);
            if (!List.of("http","https").contains(scheme) || uri.getHost()==null) return null;
            int port = uri.getPort() < 0 ? ("https".equals(scheme) ? 443 : 80) : uri.getPort();
            return scheme + "://" + uri.getHost().toLowerCase(java.util.Locale.ROOT) + ":" + port;
        } catch (IllegalArgumentException malformed) { return null; }
    }
    private JsonNode document(String json) {
        if (!text(json)) return mapper.createObjectNode();
        try {
            JsonNode value = mapper.readTree(json);
            if (value==null || !value.isObject()) throw denied();
            return value;
        } catch (Exception ex) { throw denied(); }
    }
    private boolean references(JsonNode node, String uuid, String fqdn) {
        if (node.isTextual()) return scalarReference(node.textValue(),uuid,fqdn);
        if (node.isObject() && node.has(uuid)) return true;
        for (var child : node) if (references(child,uuid,fqdn)) return true;
        return false;
    }
    private boolean text(String value) { return value != null && !value.isBlank(); }
}
