package com.ai.fabric.platform.backend.experiencekit;

import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.ai.fabric.platform.backend.experiencekit.ExperienceKitContracts.Definition;

@RestController
@RequestMapping("/api/public/experience-kits")
public class PublicExperienceKitController {
    private final ExperienceKitService kits;
    public PublicExperienceKitController(ExperienceKitService kits) { this.kits=kits; }
    @GetMapping public List<Definition> catalogue() { return kits.publicCatalogue(); }
}
