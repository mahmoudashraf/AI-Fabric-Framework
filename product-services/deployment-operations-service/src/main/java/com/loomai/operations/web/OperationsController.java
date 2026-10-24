package com.loomai.operations.web;

import com.loomai.operations.model.OperationsContracts.AssessmentRequest;
import com.loomai.operations.model.OperationsContracts.AssessmentResponse;
import com.loomai.operations.model.OperationsContracts.OutcomeRequest;
import com.loomai.operations.service.DeploymentOperationsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/deployment-operations")
public class OperationsController {

    private final DeploymentOperationsService service;

    public OperationsController(DeploymentOperationsService service) {
        this.service = service;
    }

    @PostMapping(value = "/assess", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public AssessmentResponse assess(@Valid @RequestBody AssessmentRequest request) {
        return service.assess(request);
    }

    @PostMapping(value = "/outcomes", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void outcome(@Valid @RequestBody OutcomeRequest request) {
        service.outcome(request);
    }
}
