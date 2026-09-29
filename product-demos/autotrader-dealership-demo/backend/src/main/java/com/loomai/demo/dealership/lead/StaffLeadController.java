package com.loomai.demo.dealership.lead;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/staff/leads")
public class StaffLeadController {

    private final LeadService leads;

    public StaffLeadController(LeadService leads) {
        this.leads = leads;
    }

    @GetMapping
    public Map<String, Object> latest(@RequestParam(defaultValue = "50") int limit) {
        return Map.of("success", true, "items", leads.latest(limit));
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable String id) {
        return Map.of("success", true, "item", leads.detail(id));
    }

    @PatchMapping("/{id}/status")
    public Map<String, Object> status(@PathVariable String id, @Valid @RequestBody StatusRequest request) {
        return Map.of("success", true, "item", leads.updateStatus(id, request.status()));
    }

    public record StatusRequest(@NotBlank String status) { }
}
