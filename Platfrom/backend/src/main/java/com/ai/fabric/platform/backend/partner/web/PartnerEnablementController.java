package com.ai.fabric.platform.backend.partner.web;

import com.ai.fabric.platform.backend.deployment.model.DeploymentPocAuthPath;
import com.ai.fabric.platform.backend.partner.model.PartnerCatalogEntrySummary;
import com.ai.fabric.platform.backend.partner.model.PartnerActivityEventSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerClientImplementationRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerClientImplementationSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerEligibleStoreSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerEvidenceBundleCreateRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerEvidenceBundleSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerManualVerificationStepRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerMemberSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerMemberUpdateRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerPackageTrialActivationRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerPackageTrialDeactivationRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerProductControlSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerProfileUpdateRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerSessionSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerSignupCompleteRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerStoreAccessLinkSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerStoreNoteRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerStoreNoteSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerStoreSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerSupportEscalationCreateRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerSupportEscalationSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerSupportReplyRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerSupportReplySummary;
import com.ai.fabric.platform.backend.partner.model.PartnerSupportThreadSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerTemplateApplicationRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerTemplateApplicationSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerTemplateSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerVerificationPackSummary;
import com.ai.fabric.platform.backend.partner.model.PartnerVerificationRunRequest;
import com.ai.fabric.platform.backend.partner.model.PartnerVerificationRunSummary;
import com.ai.fabric.platform.backend.partner.service.PartnerEnablementService;
import com.ai.fabric.platform.backend.shopify.model.UpdateShopifyStoreSourceSettingsRequest;
import com.ai.fabric.platform.backend.shopify.model.UpdateShopifyStoreSupportProfileRequest;
import com.ai.fabric.platform.backend.shopify.model.UpdateShopifyStoreWidgetSettingsRequest;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/partners")
public class PartnerEnablementController {

    private final PartnerEnablementService service;

    public PartnerEnablementController(PartnerEnablementService service) {
        this.service = service;
    }

    @GetMapping("/session")
    @PreAuthorize("hasAnyRole('PARTNER_AUTHENTICATED','PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public PartnerSessionSummary session() {
        return service.session();
    }

    @PostMapping("/signup/complete")
    @PreAuthorize("hasAnyRole('PARTNER_AUTHENTICATED','PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public PartnerSessionSummary completeSignup(@Valid @RequestBody PartnerSignupCompleteRequest request) {
        return service.completeSignup(request);
    }

    @GetMapping("/stores")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public List<PartnerStoreSummary> listStores() {
        return service.listStores();
    }

    @GetMapping("/stores/{storeId}")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public PartnerStoreSummary getStore(@PathVariable String storeId) {
        return service.getStore(storeId);
    }

    @GetMapping("/stores/{storeId}/product-controls")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public PartnerProductControlSummary getProductControls(@PathVariable String storeId) {
        return service.getProductControls(storeId);
    }

    @PostMapping("/stores/{storeId}/product-controls/widget-settings")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER')")
    public PartnerProductControlSummary updateProductWidgetSettings(@PathVariable String storeId,
                                                                    @RequestBody UpdateShopifyStoreWidgetSettingsRequest request) {
        return service.updateProductWidgetSettings(storeId, request);
    }

    @PostMapping("/stores/{storeId}/product-controls/source-settings")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER')")
    public PartnerProductControlSummary updateProductSourceSettings(@PathVariable String storeId,
                                                                    @RequestBody UpdateShopifyStoreSourceSettingsRequest request) {
        return service.updateProductSourceSettings(storeId, request);
    }

    @PostMapping("/stores/{storeId}/product-controls/support-profile")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public PartnerProductControlSummary updateProductSupportProfile(@PathVariable String storeId,
                                                                    @RequestBody UpdateShopifyStoreSupportProfileRequest request) {
        return service.updateProductSupportProfile(storeId, request);
    }

    @PostMapping("/stores/{storeId}/package-trials")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER')")
    public PartnerProductControlSummary activatePackageTrial(@PathVariable String storeId,
                                                             @Valid @RequestBody PartnerPackageTrialActivationRequest request) {
        return service.activatePackageTrial(storeId, request);
    }

    @PostMapping("/stores/{storeId}/package-trials/{trialId}/deactivate")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER')")
    public PartnerProductControlSummary deactivatePackageTrial(@PathVariable String storeId,
                                                               @PathVariable String trialId,
                                                               @Valid @RequestBody(required = false) PartnerPackageTrialDeactivationRequest request) {
        return service.deactivatePackageTrial(storeId, trialId, request);
    }

    @PostMapping("/stores/{storeId}/max-widget/chat/me/query")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public JsonNode queryPartnerMaxWidget(@PathVariable String storeId,
                                          @RequestBody(required = false) JsonNode request,
                                          @RequestParam(required = false) DeploymentPocAuthPath authPath) {
        return service.queryPartnerMaxWidget(storeId, request, authPath);
    }

    @PostMapping("/stores/{storeId}/max-widget/chat/me/suggestions")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public JsonNode suggestPartnerMaxWidget(@PathVariable String storeId,
                                            @RequestBody(required = false) JsonNode request,
                                            @RequestParam(required = false) DeploymentPocAuthPath authPath) {
        return service.suggestPartnerMaxWidget(storeId, request, authPath);
    }

    @GetMapping("/stores/{storeId}/max-widget/chat/me/auth-context")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public JsonNode getPartnerMaxWidgetRuntimeAuthContext(@PathVariable String storeId,
                                                          @RequestParam(required = false) DeploymentPocAuthPath authPath) {
        return service.getPartnerMaxWidgetRuntimeAuthContext(storeId, authPath);
    }

    @GetMapping("/stores/{storeId}/max-widget/chat/me/shell-config")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public JsonNode getPartnerMaxWidgetShellConfig(@PathVariable String storeId,
                                                   @RequestParam(required = false) DeploymentPocAuthPath authPath) {
        return service.getPartnerMaxWidgetShellConfig(storeId, authPath);
    }

    @GetMapping("/stores/{storeId}/max-widget/chat/me/conversations")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public JsonNode listPartnerMaxWidgetConversations(@PathVariable String storeId,
                                                      @RequestParam(required = false) DeploymentPocAuthPath authPath) {
        return service.listPartnerMaxWidgetConversations(storeId, authPath);
    }

    @GetMapping("/stores/{storeId}/max-widget/chat/me/conversations/{conversationId}")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public JsonNode getPartnerMaxWidgetConversation(@PathVariable String storeId,
                                                    @PathVariable String conversationId,
                                                    @RequestParam(required = false) DeploymentPocAuthPath authPath) {
        return service.getPartnerMaxWidgetConversation(storeId, conversationId, authPath);
    }

    @DeleteMapping("/stores/{storeId}/max-widget/chat/me/conversations/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public void deletePartnerMaxWidgetConversation(@PathVariable String storeId,
                                                   @PathVariable String conversationId,
                                                   @RequestParam(required = false) DeploymentPocAuthPath authPath) {
        service.deletePartnerMaxWidgetConversation(storeId, conversationId, authPath);
    }

    @GetMapping("/activity")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public List<PartnerActivityEventSummary> listActivity() {
        return service.listActivity();
    }

    @GetMapping("/eligible-stores")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER')")
    public List<PartnerEligibleStoreSummary> listEligibleStores(@RequestParam(name = "query", required = false) String query) {
        return service.listEligibleStores(query);
    }

    @PostMapping("/client-implementations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER')")
    public PartnerClientImplementationSummary createImplementation(@Valid @RequestBody PartnerClientImplementationRequest request) {
        return service.createImplementation(request);
    }

    @GetMapping("/client-implementations")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public List<PartnerClientImplementationSummary> listImplementations() {
        return service.listImplementations();
    }

    @GetMapping("/client-implementations/{requestId}")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public PartnerClientImplementationSummary getImplementation(@PathVariable String requestId) {
        return service.getImplementation(requestId);
    }

    @PostMapping("/client-implementations/{requestId}/store-access-links")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER')")
    public PartnerStoreAccessLinkSummary createStoreAccessLink(@PathVariable String requestId) {
        return service.createStoreAccessLink(requestId);
    }

    @GetMapping("/catalog")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public List<PartnerCatalogEntrySummary> listCatalog() {
        return service.listCatalog();
    }

    @GetMapping("/verification-packs")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public List<PartnerVerificationPackSummary> listVerificationPacks() {
        return service.listVerificationPacks();
    }

    @GetMapping("/stores/{storeId}/verification-pack")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public PartnerVerificationPackSummary getStoreVerificationPack(@PathVariable String storeId) {
        return service.getStoreVerificationPack(storeId);
    }

    @PostMapping("/stores/{storeId}/verification-runs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER')")
    public PartnerVerificationRunSummary runStoreVerification(@PathVariable String storeId,
                                                              @Valid @RequestBody PartnerVerificationRunRequest request) {
        return service.runStoreVerification(storeId, request);
    }

    @GetMapping("/verification-runs")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public List<PartnerVerificationRunSummary> listVerificationRuns() {
        return service.listVerificationRuns();
    }

    @GetMapping("/stores/{storeId}/verification-runs")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public List<PartnerVerificationRunSummary> listStoreVerificationRuns(@PathVariable String storeId) {
        return service.listStoreVerificationRuns(storeId);
    }

    @GetMapping("/verification-runs/{runId}")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public PartnerVerificationRunSummary getVerificationRun(@PathVariable String runId) {
        return service.getVerificationRun(runId);
    }

    @PostMapping("/stores/{storeId}/verification-steps/{stepId}/complete")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER')")
    public PartnerVerificationRunSummary completeManualVerificationStep(@PathVariable String storeId,
                                                                        @PathVariable String stepId,
                                                                        @Valid @RequestBody PartnerManualVerificationStepRequest request) {
        return service.completeManualVerificationStep(storeId, stepId, request);
    }

    @GetMapping("/evidence-bundles")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public List<PartnerEvidenceBundleSummary> listEvidenceBundles() {
        return service.listEvidenceBundles();
    }

    @GetMapping("/stores/{storeId}/evidence-bundles")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public List<PartnerEvidenceBundleSummary> listStoreEvidenceBundles(@PathVariable String storeId) {
        return service.listStoreEvidenceBundles(storeId);
    }

    @PostMapping("/stores/{storeId}/evidence-bundles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER')")
    public PartnerEvidenceBundleSummary createStoreEvidenceBundle(@PathVariable String storeId,
                                                                  @Valid @RequestBody PartnerEvidenceBundleCreateRequest request) {
        return service.createStoreEvidenceBundle(storeId, request);
    }

    @GetMapping("/evidence-bundles/{bundleId}")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public PartnerEvidenceBundleSummary getEvidenceBundle(@PathVariable String bundleId) {
        return service.getEvidenceBundle(bundleId);
    }

    @GetMapping(value = "/evidence-bundles/{bundleId}/export", produces = "application/zip")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public ResponseEntity<byte[]> exportEvidenceBundle(@PathVariable String bundleId) {
        byte[] body = service.exportEvidenceBundle(bundleId);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/zip"))
            .header("Content-Disposition", ContentDisposition.attachment().filename(bundleId + ".zip").build().toString())
            .body(body);
    }

    @GetMapping("/templates")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public List<PartnerTemplateSummary> listTemplates() {
        return service.listTemplates();
    }

    @GetMapping("/templates/{templateId}")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public PartnerTemplateSummary getTemplate(@PathVariable String templateId) {
        return service.getTemplate(templateId);
    }

    @PostMapping("/templates/{templateId}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER')")
    public PartnerTemplateApplicationSummary applyTemplate(@PathVariable String templateId,
                                                           @RequestBody PartnerTemplateApplicationRequest request) {
        return service.applyTemplate(templateId, request);
    }

    @GetMapping("/template-applications")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public List<PartnerTemplateApplicationSummary> listTemplateApplications() {
        return service.listTemplateApplications();
    }

    @GetMapping("/stores/{storeId}/notes")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public List<PartnerStoreNoteSummary> listStoreNotes(@PathVariable String storeId) {
        return service.listStoreNotes(storeId);
    }

    @PostMapping("/stores/{storeId}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public PartnerStoreNoteSummary createStoreNote(@PathVariable String storeId,
                                                   @Valid @RequestBody PartnerStoreNoteRequest request) {
        return service.createStoreNote(storeId, request);
    }

    @GetMapping("/members")
    @PreAuthorize("hasRole('PARTNER_ADMIN')")
    public List<PartnerMemberSummary> listMembers() {
        return service.listMembers();
    }

    @PatchMapping("/profile")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public PartnerMemberSummary updateProfile(@Valid @RequestBody PartnerProfileUpdateRequest request) {
        return service.updateProfile(request);
    }

    @PatchMapping("/members/{memberId}")
    @PreAuthorize("hasRole('PARTNER_ADMIN')")
    public PartnerMemberSummary updateMember(@PathVariable String memberId,
                                             @Valid @RequestBody PartnerMemberUpdateRequest request) {
        return service.updateMember(memberId, request);
    }

    @GetMapping("/support/escalations")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_SUPPORT')")
    public List<PartnerSupportEscalationSummary> listEscalations() {
        return service.listEscalations();
    }

    @PostMapping("/stores/{storeId}/escalations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_SUPPORT')")
    public PartnerSupportEscalationSummary createEscalation(@PathVariable String storeId,
                                                            @Valid @RequestBody PartnerSupportEscalationCreateRequest request) {
        return service.createEscalation(storeId, request);
    }

    @GetMapping("/escalations/{escalationId}/thread")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_SUPPORT')")
    public PartnerSupportThreadSummary getEscalationThread(@PathVariable String escalationId) {
        return service.getEscalationThread(escalationId);
    }

    @PostMapping("/escalations/{escalationId}/replies")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_SUPPORT')")
    public PartnerSupportReplySummary addEscalationReply(@PathVariable String escalationId,
                                                         @Valid @RequestBody PartnerSupportReplyRequest request) {
        return service.addEscalationReply(escalationId, request);
    }
}
