package com.ai.infrastructure.connector.rest.controller;

import com.ai.infrastructure.connector.rest.api.ActionExecuteRequestDto;
import com.ai.infrastructure.connector.rest.api.ActionResultDto;
import com.ai.infrastructure.connector.rest.service.RestConnectorActionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RestActionsController {

    private final RestConnectorActionService actionService;

    public RestActionsController(RestConnectorActionService actionService) {
        this.actionService = actionService;
    }

    @PostMapping("/actions/execute")
    public ActionResultDto execute(@RequestBody ActionExecuteRequestDto request) {
        return actionService.execute(request);
    }
}

