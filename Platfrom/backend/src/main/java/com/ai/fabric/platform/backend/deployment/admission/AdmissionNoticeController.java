package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;

@RestController
@RequestMapping("/api")
public class AdmissionNoticeController {
    private final ProductionAdmissionNoticeDeliverySource notices;
    private final AdmissionStore admissions;
    private final CustomerOfferEntitlementService entitlements;
    private final Clock clock;

    @Autowired
    public AdmissionNoticeController(ProductionAdmissionNoticeDeliverySource notices, AdmissionStore admissions,
                                     CustomerOfferEntitlementService entitlements) {
        this(notices, admissions, entitlements, Clock.systemUTC());
    }

    AdmissionNoticeController(ProductionAdmissionNoticeDeliverySource notices, AdmissionStore admissions,
                              CustomerOfferEntitlementService entitlements, Clock clock) {
        this.notices = notices;
        this.admissions = admissions;
        this.entitlements = entitlements;
        this.clock = clock;
    }

    @GetMapping("/customer-self-service/admission/notices")
    @PreAuthorize("hasRole('CUSTOMER_ADMIN')")
    public List<ProductionAdmissionNoticeDeliverySource.ConsoleNotice> customerNotices(
        @RequestParam(defaultValue = "50") int limit) {
        var actor = entitlements.requireCustomerActor();
        return notices.consoleNotices(actor.customerId(), "platform-user:" + actor.userId(), limit);
    }

    @PostMapping("/platform/admission/notices/{deliveryKey}/resolution")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
    public ProductionAdmissionNoticeDeliverySource.Resolution resolve(
        @PathVariable String deliveryKey, @RequestBody ResolutionRequest request) {
        if (request == null || request.delivered() == null) {
            throw new AdmissionFailure(org.springframework.http.HttpStatus.PRECONDITION_FAILED,
                "NOTICE_RESOLUTION_INVALID");
        }
        var resolution = notices.resolveUnknown(deliveryKey, request.delivered(), request.evidenceReference());
        admissions.resumeNoticeAfterResolution(resolution.reservationId(), resolution.noticeCycle(),
            PlatformSecurityContext.actorIdOrSystem(), clock.instant());
        return resolution;
    }

    public record ResolutionRequest(Boolean delivered, String evidenceReference) { }
}
