package com.ai.fabric.runtime.web;

import ai.fabric.chat.control.ConversationControlViolationException;
import ai.fabric.chat.exception.ChatSessionAccessDeniedException;
import ai.fabric.chat.exception.ChatSessionNotFoundException;
import com.ai.fabric.runtime.specialist.DeploymentKnowledgeSpecialistService.DeploymentKnowledgeInvocationException;
import com.ai.fabric.runtime.specialist.DeploymentKnowledgeSpecialistService;
import com.ai.fabric.runtime.documents.DocumentKnowledgeException;
import com.ai.fabric.runtime.web.dto.DeploymentKnowledgeQueryResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RestControllerAdvice
public class RuntimeControllerAdvice {

    private static final Set<String> CONVERSATION_NOT_FOUND_CODES = Set.of(
        "CONVERSATION_NOT_FOUND",
        "CONVERSATION_OWNER_MISMATCH"
    );

    @ExceptionHandler(DocumentKnowledgeException.class)
    public ResponseEntity<Map<String, Object>> handleDocumentKnowledge(DocumentKnowledgeException ex) {
        return ResponseEntity.status(ex.status()).body(Map.of(
            "success", false,
            "errorCode", ex.code(),
            "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(DeploymentKnowledgeInvocationException.class)
    public ResponseEntity<DeploymentKnowledgeQueryResponse>
        handleDeploymentKnowledgeInvocation(
            DeploymentKnowledgeInvocationException ex
        ) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            new DeploymentKnowledgeQueryResponse(
                "FAILED",
                "The specialist execution failed.",
                DeploymentKnowledgeSpecialistService.SPECIALIST_NAME,
                DeploymentKnowledgeSpecialistService.SPECIALIST_VERSION,
                ex.correlationId(),
                java.util.List.of(),
                "EXECUTION_INVOCATION_FAILED"
            )
        );
    }

    @ExceptionHandler(ChatSessionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ChatSessionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
            "error", "NOT_FOUND",
            "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(ChatSessionAccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(ChatSessionAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
            "error", "FORBIDDEN",
            "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(ConversationControlViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConversationControl(
        ConversationControlViolationException ex
    ) {
        if (CONVERSATION_NOT_FOUND_CODES.contains(ex.code())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "NOT_FOUND",
                "message", "Conversation is not available."
            ));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
            "error", ex.code(),
            "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
            "error", "BAD_REQUEST",
            "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEntityNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
            "error", "NOT_FOUND",
            "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err ->
            errors.put(err.getField(), err.getDefaultMessage())
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
            "error", "VALIDATION_FAILED",
            "message", "Request validation failed",
            "fields", errors
        ));
    }
}
