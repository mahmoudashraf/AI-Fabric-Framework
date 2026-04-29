package com.ai.fabric.platform.backend.thinker.web;

import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.PartnerThinkerEscalationRequest;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.ThinkerIssueSessionDetail;
import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.ThinkerIssueSessionSummary;
import com.ai.fabric.platform.backend.thinker.service.ThinkerResolverService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/partners")
public class ThinkerResolverPartnerController {

    private final ThinkerResolverService service;

    public ThinkerResolverPartnerController(ThinkerResolverService service) {
        this.service = service;
    }

    @GetMapping("/stores/{storeId}/thinker-sessions")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public List<ThinkerIssueSessionSummary> listStoreSessions(@PathVariable String storeId) {
        return service.listPartnerStoreSessions(storeId);
    }

    @GetMapping("/thinker-sessions/{sessionId}")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_DEVELOPER','PARTNER_SUPPORT')")
    public ThinkerIssueSessionDetail getSession(@PathVariable String sessionId) {
        return service.getPartnerSession(sessionId);
    }

    @PostMapping("/thinker-sessions/{sessionId}/escalations")
    @PreAuthorize("hasAnyRole('PARTNER_ADMIN','PARTNER_IMPLEMENTER','PARTNER_SUPPORT')")
    public Map<String, Object> createEscalation(@PathVariable String sessionId,
                                                @RequestBody(required = false) PartnerThinkerEscalationRequest request) {
        return service.createPartnerEscalation(sessionId, request);
    }
}
