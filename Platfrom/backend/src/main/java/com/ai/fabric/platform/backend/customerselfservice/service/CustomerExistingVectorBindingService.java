package com.ai.fabric.platform.backend.customerselfservice.service;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.ExistingVectorBindingContracts.Request;
import com.ai.fabric.platform.backend.deployment.model.ExistingVectorBindingContracts.Summary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentExistingVectorBindingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.*;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;

/** Customer ownership facade only; the existing binder owns provider identity, probes and retention. */
@Service
public class CustomerExistingVectorBindingService {
    private final CustomerOfferEntitlementService customers;
    private final DeploymentRepository deployments;
    private final CustomerSelfServiceOperationBoundary authorization;
    private final DeploymentExistingVectorBindingService bindings;
    private final CustomerOfferCodec codec;

    public CustomerExistingVectorBindingService(CustomerOfferEntitlementService customers, DeploymentRepository deployments,
        CustomerSelfServiceOperationBoundary authorization, DeploymentExistingVectorBindingService bindings, CustomerOfferCodec codec) {
        this.customers = customers; this.deployments = deployments; this.authorization = authorization;
        this.bindings = bindings; this.codec = codec;
    }

    @Transactional
    public Summary bind(String deploymentId, String targetId, Request request) {
        if (request == null || request.approvalReference() != null) throw invalid();
        var actor = customers.requireCustomerActor();
        var deployment = deployments.findByIdForUpdate(deploymentId)
            .filter(value -> actor.customerId().equals(value.getCustomerId()))
            .orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        authorize(deployment, targetId, Operation.BINDING_REPLACE);
        if (request.versionId() == null || request.versionId().isBlank()) throw invalid();
        String approval = "customer-existing-vector:" + codec.hash(Map.of("customerId", actor.customerId(),
            "deploymentId", deploymentId, "targetProfileId", targetId, "versionId", request.versionId()));
        return bindings.bind(deploymentId, targetId, new Request(request.versionId(), request.clusterId(), request.projectId(),
            request.regionId(), request.expectedDeploymentOption(), request.databaseName(), request.username(), request.password(),
            approval, request.authType(), request.token()));
    }

    @Transactional
    public Summary get(String deploymentId, String targetId) {
        var actor = customers.requireCustomerActor();
        var deployment = deployments.findById(deploymentId)
            .filter(value -> actor.customerId().equals(value.getCustomerId()))
            .orElseThrow(() -> deny(RESOURCE_SCOPE_MISMATCH));
        authorize(deployment, targetId, Operation.READ);
        return bindings.get(deploymentId, targetId);
    }

    private void authorize(DeploymentEntity deployment, String targetId, Operation operation) {
        if (targetId == null || targetId.isBlank()) throw invalid();
        authorization.require(deployment, targetId, null, operation, null, Map.of(), null, null, null);
    }
}
