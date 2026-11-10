package com.ai.fabric.runtime.authoring;

import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestController
@ConditionalOnProperty(prefix = "loomai.authoring", name = "enabled", havingValue = "true")
public final class ConfigurationAuthoringController {
    public static final String PATH = "/api/private/configuration-authoring/generate";
    private final RuntimeRequestAuthResolver auth;
    private final ConfigurationAuthoringService service;

    public ConfigurationAuthoringController(RuntimeRequestAuthResolver auth, ConfigurationAuthoringService service) {
        this.auth = auth;
        this.service = service;
    }

    @PostMapping(PATH)
    public ResponseEntity<ObjectNode> generate(@Valid @RequestBody ConfigurationAuthoringRequest request,
                                               HttpServletRequest servletRequest) {
        var identity = auth.resolveVerifiedPrivateContext(servletRequest, "configuration authoring");
        auth.requireScope(identity, "configuration:author", "configuration authoring");
        var context = identity.getAuthContext();
        // A present session claim is authoritative; a matching subject cannot excuse a conflicting session.
        String binding = StringUtils.hasText(context.getSessionId()) ? context.getSessionId() : context.getSubjectId();
        if (!request.correlation().equals(binding)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "AUTHORING_CORRELATION_MISMATCH");
        }
        return service.generate(request, identity);
    }

    @ExceptionHandler(AuthoringFailure.class)
    ResponseEntity<ObjectNode> invalidInput(AuthoringFailure failure) {
        var body = JsonNodeFactory.instance.objectNode().put("contractVersion", ConfigurationAuthoringRequest.CONTRACT_VERSION);
        body.putObject("error").put("code", failure.code).put("message", failure.getMessage());
        return ResponseEntity.status(failure.status).header("Cache-Control", "no-store").body(body);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ObjectNode> invalidRequest() {
        // Default MVC validation logging includes rejected values, which may contain imported secrets.
        return invalidInput(new AuthoringFailure(HttpStatus.BAD_REQUEST, "AUTHORING_INVALID_REQUEST", "Authoring request fields are invalid."));
    }
}
