package com.loomai.inbox.web;

import static com.loomai.inbox.model.InboxContracts.*;

import com.loomai.inbox.service.BasicInboxService;
import com.loomai.inbox.service.InboxSecurityService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operator")
public class InboxOperatorController {

    private final InboxSecurityService security;
    private final BasicInboxService service;

    public InboxOperatorController(InboxSecurityService security, BasicInboxService service) {
        this.security = security;
        this.service = service;
    }

    @GetMapping("/me")
    public OperatorIdentity me(@RequestHeader("X-LoomAI-Operator-Key") String apiKey) {
        return security.requireOperator(apiKey);
    }

    @GetMapping("/conversations")
    public List<ConversationSummary> queue(
        @RequestHeader("X-LoomAI-Operator-Key") String apiKey,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "100") int limit
    ) {
        return service.queue(security.requireOperator(apiKey), status, limit);
    }

    @GetMapping("/conversations/{handoverId}")
    public ConversationDetail detail(
        @RequestHeader("X-LoomAI-Operator-Key") String apiKey,
        @PathVariable String handoverId
    ) {
        return service.detail(security.requireOperator(apiKey), handoverId);
    }

    @PostMapping("/conversations/{handoverId}/claim")
    public OperatorCommandResult claim(
        @RequestHeader("X-LoomAI-Operator-Key") String apiKey,
        @PathVariable String handoverId,
        @RequestBody OperatorCommandRequest request
    ) {
        return service.claim(security.requireOperator(apiKey), handoverId, request.idempotencyKey());
    }

    @PostMapping("/conversations/{handoverId}/messages")
    public OperatorCommandResult reply(
        @RequestHeader("X-LoomAI-Operator-Key") String apiKey,
        @PathVariable String handoverId,
        @RequestBody ReplyRequest request
    ) {
        return service.reply(security.requireOperator(apiKey), handoverId, request);
    }

    @PostMapping("/conversations/{handoverId}/return-to-ai")
    public OperatorCommandResult returnToAi(
        @RequestHeader("X-LoomAI-Operator-Key") String apiKey,
        @PathVariable String handoverId,
        @RequestBody OperatorCommandRequest request
    ) {
        return service.returnToAi(security.requireOperator(apiKey), handoverId, request.idempotencyKey());
    }

    @PostMapping("/conversations/{handoverId}/close")
    public OperatorCommandResult close(
        @RequestHeader("X-LoomAI-Operator-Key") String apiKey,
        @PathVariable String handoverId,
        @RequestBody OperatorCommandRequest request
    ) {
        return service.close(security.requireOperator(apiKey), handoverId, request.idempotencyKey());
    }

    @GetMapping("/operations/callbacks")
    public List<CallbackOperationView> callbacks(
        @RequestHeader("X-LoomAI-Operator-Key") String apiKey,
        @RequestParam(defaultValue = "100") int limit
    ) {
        return service.callbackOperations(security.requireOperator(apiKey), limit);
    }

    @PostMapping("/operations/callbacks/{callbackId}/retry")
    public CallbackRecoveryResult retryCallback(
        @RequestHeader("X-LoomAI-Operator-Key") String apiKey,
        @PathVariable String callbackId,
        @RequestBody OperatorCommandRequest request
    ) {
        return service.retryCallback(
            security.requireOperator(apiKey),
            callbackId,
            request.idempotencyKey()
        );
    }
}
