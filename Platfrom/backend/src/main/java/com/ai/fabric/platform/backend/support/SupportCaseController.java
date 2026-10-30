package com.ai.fabric.platform.backend.support;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.ai.fabric.platform.backend.support.SupportContracts.*;

@RestController
@RequestMapping("/api/support/cases")
@PreAuthorize("hasRole('CUSTOMER_ADMIN')")
public class SupportCaseController {
    private final SupportCaseService cases;
    public SupportCaseController(SupportCaseService cases) { this.cases = cases; }

    @PostMapping
    public CaseView create(@Valid @RequestBody CreateCase request, @RequestHeader("Idempotency-Key") String key) {
        return cases.create(request, key);
    }
    @GetMapping
    public List<CaseView> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return cases.list(page, size);
    }
    @GetMapping("/{caseId}")
    public CaseView get(@PathVariable String caseId) { return cases.get(caseId); }
    @PatchMapping("/{caseId}")
    public CaseView update(@PathVariable String caseId, @Valid @RequestBody UpdateCase request) { return cases.update(caseId, request); }
    @PutMapping("/{caseId}/consent")
    public CaseView consent(@PathVariable String caseId, @Valid @RequestBody ConsentChange request) { return cases.consent(caseId, request); }
    @PostMapping("/{caseId}/resolve")
    public CaseView resolve(@PathVariable String caseId, @Valid @RequestBody StatusChange request) { return cases.resolve(caseId, request.expectedRevision()); }
    @PostMapping("/{caseId}/cancel")
    public CaseView cancel(@PathVariable String caseId, @Valid @RequestBody StatusChange request) { return cases.cancel(caseId, request.expectedRevision()); }
    @PostMapping("/{caseId}/reopen")
    public CaseView reopen(@PathVariable String caseId, @Valid @RequestBody StatusChange request) { return cases.reopen(caseId, request.expectedRevision()); }
}
