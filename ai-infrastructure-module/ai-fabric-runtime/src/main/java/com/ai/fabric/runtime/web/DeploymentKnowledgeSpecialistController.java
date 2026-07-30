package com.ai.fabric.runtime.web;

import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.specialist.DeploymentKnowledgeQuestion;
import com.ai.fabric.runtime.specialist.DeploymentKnowledgeSpecialistService;
import com.ai.fabric.runtime.web.dto.DeploymentKnowledgeQueryRequest;
import com.ai.fabric.runtime.web.dto.DeploymentKnowledgeQueryResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/specialists/deployment-knowledge")
@ConditionalOnProperty(
    prefix = "app.specialists.deployment-knowledge",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class DeploymentKnowledgeSpecialistController {

    private static final String SURFACE =
        "deployment knowledge specialist";

    private final RuntimeRequestAuthResolver authResolver;
    private final DeploymentKnowledgeSpecialistService specialistService;

    public DeploymentKnowledgeSpecialistController(
        RuntimeRequestAuthResolver authResolver,
        DeploymentKnowledgeSpecialistService specialistService
    ) {
        this.authResolver = authResolver;
        this.specialistService = specialistService;
    }

    @PostMapping("/query")
    public ResponseEntity<DeploymentKnowledgeQueryResponse> query(
        @Valid @RequestBody DeploymentKnowledgeQueryRequest request,
        HttpServletRequest servletRequest
    ) {
        RuntimeResolvedIdentity identity =
            authResolver.resolveVerifiedPrivateContext(
                servletRequest,
                SURFACE
            );
        authResolver.requireScope(
            identity,
            DeploymentKnowledgeSpecialistService.SPECIALIST_SCOPE,
            SURFACE
        );
        authResolver.requireScope(
            identity,
            DeploymentKnowledgeSpecialistService.VECTOR_SCOPE,
            SURFACE
        );

        DeploymentKnowledgeSpecialistService.QueryResult result =
            specialistService.query(
                identity,
                new DeploymentKnowledgeQuestion(request.getQuestion().trim())
            );
        return ResponseEntity
            .status(result.status())
            .body(result.response());
    }
}
