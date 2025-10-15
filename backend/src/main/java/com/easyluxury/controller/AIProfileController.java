package com.easyluxury.controller;

import com.easyluxury.dto.AIProfileDto;
import com.easyluxury.dto.GenerateProfileRequest;
import com.easyluxury.entity.User;
import com.easyluxury.facade.AIProfileFacade;
import com.easyluxury.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/ai-profile")
@RequiredArgsConstructor
@Tag(name = "AI Profile", description = "AI-powered profile generation from CV")
@SecurityRequirement(name = "Bearer Authentication")
public class AIProfileController {
    
    private final AIProfileFacade aiProfileFacade;
    private final UserService userService;
    
    /**
     * Generate AI profile from CV content
     */
    @PostMapping("/generate")
    @Operation(
        summary = "Generate AI profile from CV content",
        description = "Uses OpenAI GPT to analyze CV content and generate structured profile data"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Profile generated successfully",
            content = @Content(schema = @Schema(implementation = AIProfileDto.class))
        ),
        @ApiResponse(responseCode = "400", description = "Invalid input data"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "429", description = "Rate limit exceeded"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<AIProfileDto> generateProfile(
        @Valid @RequestBody GenerateProfileRequest request,
        Authentication authentication
    ) {
        log.info("Generating AI profile from CV content for user: {}", authentication.getName());
        
        User user = userService.findByEmail(authentication.getName());
        AIProfileDto profile = aiProfileFacade.generateProfileFromCV(user, request);
        
        return ResponseEntity.ok(profile);
    }
    
    /**
     * Get AI profile by ID
     */
    @GetMapping("/{profileId}")
    @Operation(
        summary = "Get AI profile by ID",
        description = "Retrieve an AI profile by its unique identifier"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Profile retrieved successfully",
            content = @Content(schema = @Schema(implementation = AIProfileDto.class))
        ),
        @ApiResponse(responseCode = "404", description = "Profile not found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<AIProfileDto> getProfile(
        @PathVariable UUID profileId,
        Authentication authentication
    ) {
        log.info("Getting AI profile {} for user: {}", profileId, authentication.getName());
        
        AIProfileDto profile = aiProfileFacade.getProfileById(profileId);
        
        return ResponseEntity.ok(profile);
    }
    
    /**
     * Get latest AI profile for current user
     */
    @GetMapping("/latest")
    @Operation(
        summary = "Get latest AI profile for current user",
        description = "Retrieve the most recently created AI profile for the authenticated user"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Profile retrieved successfully",
            content = @Content(schema = @Schema(implementation = AIProfileDto.class))
        ),
        @ApiResponse(responseCode = "404", description = "No profile found"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<AIProfileDto> getLatestProfile(Authentication authentication) {
        log.info("Getting latest AI profile for user: {}", authentication.getName());
        
        User user = userService.findByEmail(authentication.getName());
        AIProfileDto profile = aiProfileFacade.getLatestProfileForUser(user);
        
        return ResponseEntity.ok(profile);
    }
    
    /**
     * Get all AI profiles for current user
     */
    @GetMapping("/all")
    @Operation(
        summary = "Get all AI profiles for current user",
        description = "Retrieve all AI profiles for the authenticated user"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Profiles retrieved successfully",
            content = @Content(schema = @Schema(implementation = AIProfileDto.class))
        ),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<List<AIProfileDto>> getAllProfiles(Authentication authentication) {
        log.info("Getting all AI profiles for user: {}", authentication.getName());
        
        User user = userService.findByEmail(authentication.getName());
        List<AIProfileDto> profiles = aiProfileFacade.getAllProfilesForUser(user);
        
        return ResponseEntity.ok(profiles);
    }
}
