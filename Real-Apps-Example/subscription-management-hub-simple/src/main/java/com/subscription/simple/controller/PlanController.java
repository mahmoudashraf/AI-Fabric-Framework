package com.subscription.simple.controller;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.subscription.simple.entity.Plan;
import com.subscription.simple.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanController {
    
    private final PlanRepository planRepository;
    private final AICoreService aiCoreService;
    
    @GetMapping
    public ResponseEntity<List<Plan>> getAllPlans() {
        return ResponseEntity.ok(planRepository.findByActiveTrue());
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Plan> getPlan(@PathVariable UUID id) {
        return planRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/search")
    public ResponseEntity<List<Plan>> searchPlans(@RequestParam String query, @RequestParam(defaultValue = "10") int limit) {
        AISearchRequest searchRequest = AISearchRequest.builder()
            .query(query)
            .entityType("plan")
            .limit(limit)
            .build();
        
        AISearchResponse response = aiCoreService.performSearch(searchRequest);
        
        List<Plan> plans = response.getResults().stream()
            .map(result -> {
                String planId = result.get("id").toString();
                return planRepository.findById(UUID.fromString(planId)).orElse(null);
            })
            .filter(plan -> plan != null)
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(plans);
    }
}
