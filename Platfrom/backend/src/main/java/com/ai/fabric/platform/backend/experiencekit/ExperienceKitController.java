package com.ai.fabric.platform.backend.experiencekit;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.ai.fabric.platform.backend.experiencekit.ExperienceKitContracts.*;

@RestController
@RequestMapping("/api/customer-experience-kits")
@PreAuthorize("hasRole('CUSTOMER_ADMIN')")
public class ExperienceKitController {
    private final ExperienceKitService kits;
    public ExperienceKitController(ExperienceKitService kits) { this.kits=kits; }
    @GetMapping("/catalogue") public List<CatalogueEntry> catalogue() { return kits.customerCatalogue(); }
    @GetMapping public List<Summary> list() { return kits.list(); }
    @GetMapping("/{id}") public Summary get(@PathVariable String id) { return kits.get(id); }
    @PostMapping public Summary create(@Valid @RequestBody CreateRequest request) { return kits.create(request); }
    @PutMapping("/{id}") public Summary update(@PathVariable String id,@Valid @RequestBody UpdateRequest request) { return kits.update(id,request); }
}
