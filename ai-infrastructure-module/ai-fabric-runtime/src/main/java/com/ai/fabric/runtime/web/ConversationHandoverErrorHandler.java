package com.ai.fabric.runtime.web;

import com.ai.fabric.runtime.handover.ConversationHandoverException;
import com.ai.fabric.runtime.web.admin.ConversationHandoverAdminController;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
    ChatRuntimeController.class,
    ConversationHandoverController.class,
    ConversationChannelPrivateController.class,
    ConversationHandoverAdminController.class
})
@ConditionalOnExpression("${loomai.conversation-control.enabled:false} or ${loomai.conversation-handover.enabled:false}")
public class ConversationHandoverErrorHandler {

    @ExceptionHandler(ConversationHandoverException.class)
    ResponseEntity<Map<String, Object>> handle(ConversationHandoverException exception) {
        return ResponseEntity.status(exception.status()).body(Map.of(
            "success", false,
            "code", exception.code(),
            "message", exception.getMessage()
        ));
    }
}
