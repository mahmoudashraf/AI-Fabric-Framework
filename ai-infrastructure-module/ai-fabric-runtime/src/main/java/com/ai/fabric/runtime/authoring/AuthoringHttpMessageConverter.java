package com.ai.fabric.runtime.authoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Type;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

/** Boot 4 defaults to Jackson 3; released framework JsonNodes require the existing Jackson 2 API. */
@SuppressWarnings("removal")
final class AuthoringHttpMessageConverter extends MappingJackson2HttpMessageConverter {
    AuthoringHttpMessageConverter(ObjectMapper mapper) {
        super(mapper.copy().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION));
    }

    @Override public boolean canRead(Type type, Class<?> context, MediaType mediaType) {
        return type == ConfigurationAuthoringRequest.class && super.canRead(type, context, mediaType);
    }
    @Override public boolean canRead(Class<?> type, MediaType mediaType) {
        return type == ConfigurationAuthoringRequest.class && super.canRead(type, mediaType);
    }
    @Override public boolean canWrite(Class<?> type, MediaType mediaType) {
        return type == ObjectNode.class && super.canWrite(type, mediaType);
    }
}
