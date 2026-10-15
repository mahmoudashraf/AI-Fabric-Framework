package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.deployment.service.DeploymentRuntimeJsonTransport;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpecValidator.fail;

/** Exact-binding lifecycle verification for LoomAI-owned HTTP dependencies. */
@Service
public class HttpManagedDependencyInitializationVerifier implements ManagedDependencyInitializationVerifier {
    static final String VERSION = "loomai-managed-dependency-lifecycle/v1";
    static final String PATH = "/internal/loomai-managed-dependency/v1/lifecycle";
    static final String HEADER = "X-LoomAI-Managed-Dependency-Key";
    private static final Set<String> CONTRACTS = Set.of(
        "scoped-seed-sync-canary/v1", "inbox-callback-canary/v1");

    private final DeploymentRuntimeJsonTransport transport;
    private final PlatformSecretService secrets;
    private final ObjectMapper mapper;

    public HttpManagedDependencyInitializationVerifier(DeploymentRuntimeJsonTransport transport,
        PlatformSecretService secrets, ObjectMapper mapper) {
        this.transport = transport;
        this.secrets = secrets;
        this.mapper = mapper;
    }

    @Override
    public boolean supports(String initializationContract) {
        return CONTRACTS.contains(initializationContract);
    }

    @Override
    public void initializeAndVerify(ManagedDependencyPrerequisites.Scope scope,
        String initializationContract, ManagedDependencyPrerequisites.Binding binding) {
        execute(scope, initializationContract, binding, "INITIALIZE", "READY", operationKey(scope, binding, "initialize"));
    }

    @Override
    public void disableAndVerify(ManagedDependencyPrerequisites.Scope scope,
        String initializationContract, ManagedDependencyPrerequisites.Binding binding) {
        execute(scope, initializationContract, binding, "DISABLE", "DISABLED", operationKey(scope, binding, "disable"));
    }

    @Override
    public void requireUnused(ManagedDependencyPrerequisites.Scope scope,
        String initializationContract, ManagedDependencyPrerequisites.Binding binding,
        String retirementOperationKey) {
        required(retirementOperationKey, "DEPENDENCY_RETIREMENT_OPERATION_REQUIRED");
        execute(scope, initializationContract, binding, "VERIFY_UNUSED", "UNUSED",
            retirementOperationKey + ":" + binding.slot());
    }

    private void execute(ManagedDependencyPrerequisites.Scope scope, String contract,
        ManagedDependencyPrerequisites.Binding binding, String command, String expectedState,
        String operationKey) {
        if (!supports(contract) || scope == null || binding == null) fail("DEPENDENCY_INITIALIZATION_CONTRACT_UNSUPPORTED");
        String secretReference = binding.secretReferences() == null
            ? null : binding.secretReferences().get("management");
        String managementKey = secretReference == null ? null : secrets.resolveSecret(secretReference);
        if (managementKey == null || managementKey.length() < 32) fail("DEPENDENCY_MANAGEMENT_SECRET_MISSING");
        required(binding.baseUrl(), "DEPENDENCY_BASE_URL_MISSING");
        ManagedRequest request = new ManagedRequest(VERSION, command, contract, scope.customerId(),
            scope.tenantId(), scope.deploymentId(), scope.targetProfileId(), scope.versionId(),
            binding.slot(), binding.serviceRef(), binding.accountId(), binding.bindingId(), operationKey);
        var response = transport.send(binding.baseUrl(), "POST", PATH, Map.of(HEADER, managementKey),
            mapper.valueToTree(request), operationKey);
        if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null
            || !response.body().isObject()) fail("DEPENDENCY_INITIALIZATION_REJECTED");
        final ManagedReadback readback;
        try { readback = mapper.treeToValue(response.body(), ManagedReadback.class); }
        catch (Exception error) { fail("DEPENDENCY_INITIALIZATION_READBACK_INVALID"); return; }
        if (readback == null || !VERSION.equals(readback.contractVersion())
            || !expectedState.equals(readback.state()) || !scope.customerId().equals(readback.customerId())
            || !scope.tenantId().equals(readback.tenantId())
            || !scope.deploymentId().equals(readback.deploymentId())
            || !scope.targetProfileId().equals(readback.targetProfileId())
            || !scope.versionId().equals(readback.versionId())
            || !binding.slot().equals(readback.slot()) || !binding.serviceRef().equals(readback.serviceRef())
            || !binding.accountId().equals(readback.accountId()) || !binding.bindingId().equals(readback.bindingId())
            || !operationKey.equals(readback.operationKey()) || readback.observedAt() == null
            || readback.observedAt().isAfter(Instant.now()) || readback.evidenceReference() == null
            || readback.evidenceReference().isBlank() || readback.activeWork() < 0
            || "UNUSED".equals(expectedState) && readback.activeWork() != 0) {
            fail("DEPENDENCY_INITIALIZATION_READBACK_INVALID");
        }
    }

    private String operationKey(ManagedDependencyPrerequisites.Scope scope,
        ManagedDependencyPrerequisites.Binding binding, String operation) {
        return scope.graphId() + ":" + binding.slot() + ":" + operation;
    }

    private String required(String value, String code) {
        if (value == null || value.isBlank()) fail(code);
        return value;
    }

    public record ManagedRequest(String contractVersion, String command, String initializationContract,
                                 String customerId, String tenantId, String deploymentId,
                                 String targetProfileId, String versionId, String slot,
                                 String serviceRef, String accountId, String bindingId,
                                 String operationKey) { }

    public record ManagedReadback(String contractVersion, String state, String customerId,
                                  String tenantId, String deploymentId, String targetProfileId,
                                  String versionId, String slot, String serviceRef, String accountId,
                                  String bindingId, String operationKey, long activeWork,
                                  Instant observedAt, String evidenceReference, boolean replay) { }
}
