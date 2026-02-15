package com.ai.fabric.realapps.chat.web;

import com.ai.infrastructure.intent.action.AIActionHandler;
import com.ai.infrastructure.intent.action.AIActionRegistry;
import com.ai.infrastructure.intent.action.ActionContext;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class ConnectorActionController {

    private final AIActionRegistry actionRegistry;

    @PostMapping({"/actions/execute", "/api/connector/actions/execute"})
    public ResponseEntity<ActionResult> executeAction(@Valid @RequestBody ActionExecuteRequest request) {
        AIActionHandler handler = actionRegistry.findHandler(request.getActionId()).orElse(null);
        if (handler == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ActionResult.builder()
                .success(false)
                .errorCode("ACTION_NOT_FOUND")
                .message("Action handler not found: " + request.getActionId())
                .build());
        }

        OrchestrationContext orchestrationContext = OrchestrationContext.builder()
            .conversationId(request.getTrace().getConversationId())
            .userId(request.getTrace().getUserId())
            .sessionId(request.getTrace().getSessionId())
            .build();

        ActionContext actionContext = new ActionContext(orchestrationContext, null);
        try {
            ActionResult result = handler.executeAction(request.getParams(), actionContext);
            return ResponseEntity.ok(result);
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ActionResult.builder()
                .success(false)
                .errorCode("ACTION_EXECUTION_FAILED")
                .message(ex.getMessage() == null ? "Action execution failed" : ex.getMessage())
                .build());
        }
    }

    @Data
    public static class ActionExecuteRequest {
        @NotBlank
        private String actionId;
        @NotNull
        private Map<String, Object> params;
        @NotNull
        private TraceContext trace;
        private String idempotencyKey;
    }

    @Data
    public static class TraceContext {
        @NotBlank
        private String requestId;
        private String conversationId;
        private String userId;
        @NotBlank
        private String sessionId;
    }
}
