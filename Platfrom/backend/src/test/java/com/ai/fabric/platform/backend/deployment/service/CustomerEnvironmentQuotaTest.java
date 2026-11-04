package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerEnvironmentQuotaTest {
    protected JdbcTemplate jdbc;
    protected CustomerEnvironmentQuotaService service;
    protected TransactionTemplate transactions;
    protected PlatformAuditService audit;

    protected DataSource dataSource() {
        return new DriverManagerDataSource("jdbc:h2:mem:quota" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
    }

    @BeforeEach void setup() {
        var source = dataSource(); jdbc = new JdbcTemplate(source);
        for (String table : new String[]{"deployment_managed_dependency_graphs", "platform_deployment_releases",
            "deployment_provider_resource_handles", "deployment_target_profiles", "platform_deployments", "platform_customers"}) {
            jdbc.execute("DROP TABLE IF EXISTS " + table);
        }
        jdbc.execute("CREATE TABLE platform_customers(id varchar(64) PRIMARY KEY, infrastructure_environment_limit integer DEFAULT 2 NOT NULL, updated_at timestamp)");
        jdbc.execute("CREATE TABLE platform_deployments(id varchar(64) PRIMARY KEY, customer_id varchar(64), name varchar(255), archived_at timestamp)");
        jdbc.execute("CREATE TABLE deployment_target_profiles(id varchar(64) PRIMARY KEY, environment_name varchar(64))");
        jdbc.execute("CREATE TABLE deployment_provider_resource_handles(id varchar(64) PRIMARY KEY, deployment_id varchar(64), target_profile_id varchar(64), resource_kind varchar(64), status varchar(64))");
        jdbc.execute("CREATE TABLE platform_deployment_releases(id varchar(64) PRIMARY KEY, deployment_id varchar(64), target_profile_id varchar(64), status varchar(64), provisioning_status varchar(64), created_at timestamp)");
        jdbc.execute("CREATE TABLE deployment_managed_dependency_graphs(id varchar(64) PRIMARY KEY, deployment_id varchar(64), target_profile_id varchar(64), status varchar(64), worker_token varchar(64), state_json text)");
        jdbc.update("INSERT INTO platform_customers(id) VALUES ('owner'),('other')");
        for (String id : new String[]{"a","b","c","d"}) jdbc.update("INSERT INTO platform_deployments(id,customer_id,name) VALUES (?,?,?)", id, id.equals("d") ? "other" : "owner", id);
        jdbc.update("INSERT INTO deployment_target_profiles VALUES ('stage','staging'),('prod','production')");
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        audit = mock(PlatformAuditService.class);
        service = new CustomerEnvironmentQuotaService(jdbc, new ObjectMapper(), audit);
    }

    @Test void draftsAndUnrelatedCustomersDoNotConsumeSlots() {
        resource("foreign", "d", "stage", "APPLICATION", "ACTIVE");
        assertThat(service.summary("owner").used()).isZero();
        assertThat(service.summary("owner").available()).isEqualTo(2);
        reserve("a", "stage");
    }

    @Test void runtimeAndDependenciesCountOnceAndBothTargetsCountSeparately() {
        resource("app", "a", "stage", "APPLICATION", "ACTIVE");
        resource("connector", "a", "stage", "CONNECTOR_APPLICATION", "ACTIVE");
        resource("db", "a", "stage", "RUNTIME_POSTGRES_DATABASE", "ACTIVE");
        graph("a", "stage", "READY", null, "{\"nodes\":[{\"databaseUuid\":\"database\"}]}");
        resource("prod", "a", "prod", "APPLICATION", "ACTIVE");
        assertThat(service.summary("owner").used()).isEqualTo(2);
        assertThatThrownBy(() -> reserve("b", "stage")).hasMessageContaining("CUSTOMER_ENVIRONMENT_LIMIT_REACHED");
    }

    @Test void updatesReuseTheirSlotEvenIfOperatorReducesTheLimit() {
        resource("app", "a", "stage", "APPLICATION", "STOPPED");
        resource("prod", "a", "prod", "APPLICATION", "FAILED_DIAGNOSTIC_HOLD");
        service.updateLimit("owner", 1, "Reduce new infrastructure");
        reserve("a", "stage"); reserve("a", "prod");
        assertThatThrownBy(() -> reserve("b", "stage")).hasMessageContaining("CUSTOMER_ENVIRONMENT_LIMIT_REACHED");
        verify(audit).record(eq("CUSTOMER_ENVIRONMENT_LIMIT_CHANGED"), eq("PLATFORM_CUSTOMER"), eq("owner"), anyMap());
    }

    @Test void stoppingOrArchivingDoesNotFreeInfrastructureButDeletionDoes() {
        resource("app", "a", "stage", "APPLICATION", "EXITED_UNHEALTHY");
        jdbc.update("UPDATE platform_deployments SET archived_at=CURRENT_TIMESTAMP WHERE id='a'");
        release("old", "a", "stage", "APPLIED_VERIFIED", "ACTIVE");
        assertThat(service.summary("owner").used()).isEqualTo(1);
        jdbc.update("UPDATE deployment_provider_resource_handles SET status='DELETED'");
        assertThat(service.summary("owner").used()).isZero();
    }

    @Test void preservedExternalStorageAndReleasedAuditRecordsDoNotConsumeInfrastructure() {
        resource("docs", "a", "stage", "EXTERNAL_DOCUMENT_STORAGE_BINDING", "CONFIGURED");
        resource("vectors", "a", "stage", "EXTERNAL_VECTOR_STORAGE_BINDING", "RELEASED_UNUSED_TRANSFER");
        resource("state", "a", "stage", "EXTERNAL_AI_STATE_STORAGE_BINDING", "ACTIVE");
        release("retired", "a", "stage", "RETIRED", "DELETED");
        graph("a", "stage", "RETIRED", null, "{\"nodes\":[{\"databaseUuid\":\"old-db\"}]}");
        assertThat(service.summary("owner").used()).isZero();
    }

    @Test void queuedReleaseReservesSlotUntilPreProvisioningFailure() {
        release("queued", "a", "stage", "APPLY_REQUESTED", "QUEUED");
        assertThat(service.summary("owner").used()).isEqualTo(1);
        jdbc.update("UPDATE platform_deployment_releases SET status='FAILED', provisioning_status='FAILED'");
        assertThat(service.summary("owner").used()).isZero();
    }

    @Test void dependencyWorkerAndUncertainCreateIntentsHoldOneSlotUntilRetired() {
        graph("a", "stage", "RUNNING", "worker", "{\"nodes\":[]}");
        assertThat(service.summary("owner").used()).isEqualTo(1);
        jdbc.update("UPDATE deployment_managed_dependency_graphs SET worker_token=null, status='FAILED',state_json=?", "{\"nodes\":[{\"applicationCreateIntent\":true}]}");
        assertThat(service.summary("owner").used()).isEqualTo(1);
        jdbc.update("UPDATE deployment_managed_dependency_graphs SET status='RETIRED'");
        assertThat(service.summary("owner").used()).isZero();
    }

    @Test void legacyLiveReleaseWithoutHandlesIsStillCountedButOldHistoryIsNot() {
        release("old", "a", "stage", "APPLIED_VERIFIED", "ACTIVE");
        assertThat(service.summary("owner").used()).isEqualTo(1);
        release("new", "a", "stage", "RETIRED", "DELETED");
        jdbc.update("UPDATE platform_deployment_releases SET created_at=TIMESTAMP '2030-01-01 00:00:00' WHERE id='new'");
        assertThat(service.summary("owner").used()).isZero();
    }

    @Test void concurrentAppliesCannotBothTakeTheLastSlot() throws Exception {
        resource("app", "a", "stage", "APPLICATION", "ACTIVE");
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = java.util.List.of("b", "c").stream().map(id -> executor.submit(() -> {
                ready.countDown(); start.await();
                try {
                    transactions.executeWithoutResult(tx -> { service.requireAvailable(deployment(id), "stage");
                        release("release-"+id, id, "stage", "APPLY_REQUESTED", "QUEUED"); });
                    return true;
                } catch (ResponseStatusException error) { assertThat(error.getReason()).startsWith("CUSTOMER_ENVIRONMENT_LIMIT_REACHED"); return false; }
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); start.countDown();
            int accepted=0; for (var future:futures) if(future.get(10,TimeUnit.SECONDS)) accepted++;
            assertThat(accepted).isEqualTo(1);
        }
        assertThat(service.summary("owner").used()).isEqualTo(2);
    }

    @Test void rollbackOfFailedSubmissionDoesNotConsumeASlot() {
        assertThatThrownBy(() -> transactions.executeWithoutResult(tx -> {
            service.requireAvailable(deployment("a"), "stage");
            release("rollback", "a", "stage", "APPLY_REQUESTED", "QUEUED");
            throw new IllegalStateException("submission rejected");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(service.summary("owner").used()).isZero();
    }

    @Test void badLimitAndMissingCustomerAreRejected() {
        assertThatThrownBy(() -> service.updateLimit("owner", -1, "reason")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.updateLimit("owner", 3, " ")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.summary("missing")).isInstanceOf(ResponseStatusException.class);
    }

    private void reserve(String id, String target) { transactions.executeWithoutResult(tx -> service.requireAvailable(deployment(id), target)); }
    private DeploymentEntity deployment(String id) { var d=new DeploymentEntity(); d.setId(id); d.setCustomerId(id.equals("d") ? "other":"owner"); return d; }
    protected void resource(String id,String dep,String target,String kind,String status) { jdbc.update("INSERT INTO deployment_provider_resource_handles VALUES (?,?,?,?,?)",id,dep,target,kind,status); }
    protected void release(String id,String dep,String target,String status,String provisioning) { jdbc.update("INSERT INTO platform_deployment_releases VALUES (?,?,?,?,?,CURRENT_TIMESTAMP)",id,dep,target,status,provisioning); }
    private void graph(String dep,String target,String status,String worker,String state) { jdbc.update("INSERT INTO deployment_managed_dependency_graphs VALUES (?,?,?,?,?,?)","graph-"+dep+target,dep,target,status,worker,state); }
}
