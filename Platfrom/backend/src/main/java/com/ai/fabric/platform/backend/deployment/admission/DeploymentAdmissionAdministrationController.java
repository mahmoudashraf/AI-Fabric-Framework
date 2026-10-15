package com.ai.fabric.platform.backend.deployment.admission;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.CustomerGrant;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.FleetPolicy;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.Manifest;

/** Staff-only publication of independently reviewed admission records. */
@RestController
@RequestMapping("/api/platform/admission/reviews")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class DeploymentAdmissionAdministrationController {
    private final DeploymentAdmissionService admissions;

    public DeploymentAdmissionAdministrationController(DeploymentAdmissionService admissions) {
        this.admissions = admissions;
    }

    @PostMapping("/fleet-policies")
    public Map<String, String> approvePolicy(@RequestBody FleetPolicy policy) {
        admissions.approvePolicy(policy);
        return Map.of("policyId", policy.id(), "status", "REVIEWED");
    }

    @PostMapping("/customer-grants")
    public Map<String, String> approveGrant(@RequestBody CustomerGrant grant) {
        admissions.approveCustomerGrant(grant);
        return Map.of("customerId", grant.customerId(), "status", "REVIEWED");
    }

    @PostMapping("/manifests")
    public Map<String, String> approveManifest(@RequestBody Manifest manifest) {
        admissions.approveManifest(manifest);
        return Map.of("manifestId", manifest.id(), "status", "REVIEWED");
    }
}
