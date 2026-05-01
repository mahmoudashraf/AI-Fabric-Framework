package com.ai.fabric.platform.backend.partner.web;

import com.ai.fabric.platform.backend.partner.model.PartnerMemberUpdateRequest;
import com.ai.fabric.platform.backend.partner.model.PlatformPartnerMemberSummary;
import com.ai.fabric.platform.backend.partner.service.PartnerEnablementService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/platform/partners")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformPartnerAdminController {

    private final PartnerEnablementService service;

    public PlatformPartnerAdminController(PartnerEnablementService service) {
        this.service = service;
    }

    @GetMapping("/members")
    public List<PlatformPartnerMemberSummary> listMembers() {
        return service.listPlatformPartnerMembers();
    }

    @PatchMapping("/members/{memberId}")
    public PlatformPartnerMemberSummary updateMember(@PathVariable String memberId,
                                                     @Valid @RequestBody PartnerMemberUpdateRequest request) {
        return service.updatePlatformPartnerMember(memberId, request);
    }
}
