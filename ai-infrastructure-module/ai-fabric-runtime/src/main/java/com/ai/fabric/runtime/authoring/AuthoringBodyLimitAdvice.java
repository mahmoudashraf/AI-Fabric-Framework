package com.ai.fabric.runtime.authoring;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = ConfigurationAuthoringController.class)
@ConditionalOnProperty(prefix = "loomai.authoring", name = "enabled", havingValue = "true")
public final class AuthoringBodyLimitAdvice extends RequestBodyAdviceAdapter {
    @Override
    public boolean supports(MethodParameter parameter, Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        return targetType == ConfigurationAuthoringRequest.class;
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage input, MethodParameter parameter, Type targetType,
                                           Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        // Bound bytes before Jackson materializes dynamic configuration, including chunked requests.
        byte[] bytes = input.getBody().readNBytes(AuthoringJsonContract.MAX_INPUT_BYTES + 1);
        if (bytes.length > AuthoringJsonContract.MAX_INPUT_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "AUTHORING_INPUT_LIMIT");
        }
        return new HttpInputMessage() {
            @Override public InputStream getBody() { return new ByteArrayInputStream(bytes); }
            @Override public HttpHeaders getHeaders() { return input.getHeaders(); }
        };
    }
}
