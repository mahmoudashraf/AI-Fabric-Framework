package com.ai.fabric.platform.backend.deployment.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ai.fabric.platform.backend.deployment.service.ManagedResourceOwnershipFixture.SCOPE;
import static org.assertj.core.api.Assertions.*;

class ManagedResourceOwnershipServiceTest {
    private final ManagedResourceOwnershipFixture fixture=new ManagedResourceOwnershipFixture();

    @Test void exactScopeAndLocationAreRequiredForReuse() {
        fixture.own("qdrant","COLLECTION","https://vector.example","catalog","catalog","https://vector.example");
        assertThat(fixture.ownership.requireOwnedAt(SCOPE,"qdrant","COLLECTION","https://vector.example","catalog","catalog","https://vector.example").state()).isEqualTo("OWNED");
        var other=new ManagedResourceOwnershipService.Scope("customer-b","tenant-a","dep-123","target-stage");
        assertThatThrownBy(()->fixture.ownership.requireOwned(other,"qdrant","COLLECTION","catalog","https://vector.example")).hasMessageContaining("OWNERSHIP_CHANGED");
        assertThatThrownBy(()->fixture.ownership.requireOwnedAt(SCOPE,"qdrant","COLLECTION","https://other.example","catalog","catalog","https://vector.example")).hasMessageContaining("LOCATION_CHANGED");
    }

    @Test void failedCreateIntentSurvivesCallerRollbackAndCannotBeAdoptedOrRetried() {
        fixture.transaction().executeWithoutResult(tx->{
            assertThatThrownBy(()->fixture.ownership.create(SCOPE,"railway","SERVICE","project","runtime",()->{
                throw new IllegalStateException("ambiguous synthetic create");
            },value->new ManagedResourceOwnershipService.Identity("service",null))).hasMessageContaining("ambiguous");
            tx.setRollbackOnly();
        });
        assertThat(fixture.jdbc.queryForObject("SELECT state FROM platform_managed_resource_ownership",String.class)).isEqualTo("CREATE_PENDING");
        AtomicBoolean effect=new AtomicBoolean();
        assertThatThrownBy(()->fixture.ownership.create(SCOPE,"railway","SERVICE","project","runtime",()->{
            effect.set(true); return "service";
        },value->new ManagedResourceOwnershipService.Identity(value,null))).hasMessageContaining("RECONCILIATION_REQUIRED");
        assertThat(effect).isFalse();
        assertThatThrownBy(()->fixture.ownership.requireOwned(SCOPE,"railway","SERVICE","service",null)).hasMessageContaining("OWNERSHIP_REQUIRED");
    }

    @Test void successEvidenceSurvivesCallerRollbackButDeletionDisallowsReuse() {
        fixture.transaction().executeWithoutResult(tx->{
            fixture.own("railway","SERVICE","project","runtime","service",null); tx.setRollbackOnly();
        });
        var receipt=fixture.ownership.requireOwned(SCOPE,"railway","SERVICE","service",null);
        fixture.ownership.beginDeletion(receipt);
        assertThatThrownBy(()->fixture.ownership.requireOwned(SCOPE,"railway","SERVICE","service",null)).hasMessageContaining("OWNERSHIP_CHANGED");
        fixture.ownership.deleted(receipt.id());
        assertThat(fixture.ownership.receipt(receipt.id()).state()).isEqualTo("DELETED");
    }

    @Test void rootAuthorityDoesNotAuthorizeChildDeletion() {
        var root=fixture.own("qdrant","CLUSTER","account","cluster","cluster-id","https://vector.example");
        fixture.own("qdrant","COLLECTION","https://vector.example","catalog","catalog","https://vector.example");
        assertThatThrownBy(()->fixture.ownership.beginDeletion(root)).hasMessageContaining("CHILDREN_RETAINED");
    }

    @Test void collectionCredentialsCannotBeRetiredAfterCollectionDeletionFailed() {
        String endpoint="https://vector.example";
        var key=fixture.own("qdrant","DATABASE_API_KEY",endpoint,"key","key-id",endpoint);
        var collection=fixture.own("qdrant","COLLECTION",endpoint,"catalog","catalog",endpoint);
        fixture.ownership.beginDeletion(collection);
        assertThatThrownBy(()->fixture.ownership.beginDeletion(key)).hasMessageContaining("CHILDREN_RETAINED");
        fixture.ownership.deleted(collection.id());
        fixture.ownership.beginDeletion(key);
        assertThat(fixture.ownership.receipt(key.id()).state()).isEqualTo("DELETE_PENDING");
    }

    @Test void retainedExternalEndpointReferenceBlocksRootDeletionEvenWithDifferentProviderId() {
        var root=fixture.own("zilliz","CLUSTER","project","cluster","cluster-id","https://vector.example");
        fixture.jdbc.update("INSERT INTO deployment_provider_resource_handles(provider_resource_uuid,status,fqdn,resource_kind) VALUES(?,?,?,?)",
            "different-id","ACTIVE","https://vector.example/","EXTERNAL_VECTOR_STORAGE_BINDING");
        assertThatThrownBy(()->fixture.ownership.beginDeletion(root)).hasMessageContaining("REFERENCED");
        assertThat(fixture.ownership.receipt(root.id()).state()).isEqualTo("OWNED");
    }

    @Test void knownDeletedParentPreventsCreatingNewChildren() {
        var root=fixture.own("qdrant","CLUSTER","account","cluster","cluster-id","https://vector.example");
        fixture.ownership.beginDeletion(root);
        fixture.ownership.deleted(root.id());
        AtomicBoolean called=new AtomicBoolean();
        assertThatThrownBy(()->fixture.ownership.create(SCOPE,"qdrant","COLLECTION","https://vector.example","new",()->{
            called.set(true); return "new";
        },value->new ManagedResourceOwnershipService.Identity(value,"https://vector.example")))
            .hasMessageContaining("PARENT_UNAVAILABLE");
        assertThat(called).isFalse();
    }

    @Test void duplicateProviderIdentityCannotBecomeOwnedAtAnotherLocation() {
        fixture.own("railway","SERVICE","project","runtime","service",null);
        assertThatThrownBy(()->fixture.ownership.create(SCOPE,"railway","SERVICE","other-project","runtime",()->"service",
            value->new ManagedResourceOwnershipService.Identity(value,null))).hasMessageContaining("ALREADY_CLAIMED");
        assertThat(fixture.jdbc.queryForList("SELECT state FROM platform_managed_resource_ownership ORDER BY state",String.class))
            .containsExactly("CREATE_PENDING","OWNED");
    }

    @Test void malformedForeignReferenceCannotSilentlyPermitCleanup() {
        var receipt=fixture.own("railway","SERVICE","project","runtime","service",null);
        fixture.release("malformed",new ManagedResourceOwnershipService.Scope("c","t","other","other"),"not-json");
        assertThatThrownBy(()->fixture.ownership.beginDeletion(receipt)).hasMessageContaining("REFERENCE_RECONCILIATION_REQUIRED");
    }

    @Test void databaseRejectsOwnedStateWithoutCreationIdentity() {
        assertThatThrownBy(()->fixture.jdbc.update("""
            INSERT INTO platform_managed_resource_ownership
              (id,location_key,provider,resource_kind,parent_identity,resource_name,customer_id,tenant_id,
               deployment_id,target_profile_id,state,created_at,updated_at)
            VALUES ('invalid','invalid','railway','SERVICE','project','runtime','c','t','d','p','OWNED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
            """)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void otherTargetReferencesAndPreservedExternalHandlesBlockDeletion() {
        var receipt=fixture.own("qdrant","COLLECTION","https://vector.example","catalog","catalog","https://vector.example");
        var other=new ManagedResourceOwnershipService.Scope("customer-a","tenant-a","dep-123","target-production");
        fixture.release("rel-other",other,"{}");
        fixture.jdbc.update("INSERT INTO platform_deployment_managed_vector_resources VALUES(?,?,?,?,?)","qdrant","https://vector.example/collections/catalog","https://vector.example","dep-123","rel-other");
        assertThatThrownBy(()->fixture.ownership.beginDeletion(receipt)).hasMessageContaining("REFERENCED");
        fixture.jdbc.update("DELETE FROM platform_deployment_managed_vector_resources");
        fixture.jdbc.update("INSERT INTO deployment_provider_resource_handles(provider_resource_uuid,status,deployment_id,target_profile_id,resource_kind) VALUES(?,?,?,?,?)","catalog","ACTIVE","dep-123","target-stage","EXTERNAL_VECTOR_STORAGE_BINDING");
        assertThatThrownBy(()->fixture.ownership.beginDeletion(receipt)).hasMessageContaining("REFERENCED");
    }

    @Test void unrelatedCollectionsOnSharedEndpointDoNotGrantOrBlockAuthority() {
        var receipt=fixture.own("qdrant","COLLECTION","https://vector.example","catalog","catalog","https://vector.example");
        fixture.release("rel-other",new ManagedResourceOwnershipService.Scope("c","t","other","other"),"{}");
        fixture.jdbc.update("INSERT INTO platform_deployment_managed_vector_resources VALUES(?,?,?,?,?)","qdrant","https://vector.example/collections/other","https://vector.example","other","rel-other");
        fixture.ownership.beginDeletion(receipt);
        assertThat(fixture.ownership.receipt(receipt.id()).state()).isEqualTo("DELETE_PENDING");
    }

    @Test void railwayHistoricalForeignReleaseReferenceBlocksServiceDeletion() {
        var receipt=fixture.own("railway","SERVICE","project","runtime","service",null);
        fixture.release("rel-other",new ManagedResourceOwnershipService.Scope("c","t","other","other"),
            "{\"railway\":{\"services\":{\"runtime\":{\"serviceId\":\"service\"}}}}");
        assertThatThrownBy(()->fixture.ownership.beginDeletion(receipt)).hasMessageContaining("REFERENCED");
    }

    @Test void registryTransactionAndDeletionAreSerializedWithoutMissingNewReference() throws Exception {
        var receipt=fixture.own("qdrant","COLLECTION","https://vector.example","catalog","catalog","https://vector.example");
        fixture.release("rel-other",new ManagedResourceOwnershipService.Scope("c","t","other","other"),"{}");
        CountDownLatch locked=new CountDownLatch(1), release=new CountDownLatch(1), deleting=new CountDownLatch(1);
        try (var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var registration=executor.submit(()->fixture.transaction().executeWithoutResult(tx->{
                fixture.ownership.requireOwned(SCOPE,"qdrant","COLLECTION","catalog","https://vector.example");
                locked.countDown();
                try { if (!release.await(3,TimeUnit.SECONDS)) throw new AssertionError("registration timeout"); }
                catch (InterruptedException ex) { throw new RuntimeException(ex); }
                fixture.jdbc.update("INSERT INTO platform_deployment_managed_vector_resources VALUES(?,?,?,?,?)","qdrant","https://vector.example/collections/catalog","https://vector.example","other","rel-other");
            }));
            assertThat(locked.await(3,TimeUnit.SECONDS)).isTrue();
            var deletion=executor.submit(()->{ deleting.countDown(); assertThatThrownBy(()->fixture.ownership.beginDeletion(receipt)).hasMessageContaining("REFERENCED"); });
            assertThat(deleting.await(3,TimeUnit.SECONDS)).isTrue();
            release.countDown(); registration.get(5,TimeUnit.SECONDS); deletion.get(5,TimeUnit.SECONDS);
        }
        assertThat(fixture.ownership.receipt(receipt.id()).state()).isEqualTo("OWNED");
    }

    @Test void endpointsCannotStoreCredentialsQueriesOrFragments() {
        for (String endpoint:new String[]{"https://user:secret@vector.example","https://vector.example?token=synthetic","https://vector.example#secret"})
            assertThatThrownBy(()->new ManagedResourceOwnershipService.Identity("id",endpoint)).hasMessageContaining("ENDPOINT_INVALID");
    }
}
