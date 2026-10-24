package com.loomai.operations.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;

final class IsoInstantDeserializer extends StdDeserializer<Instant> {

    IsoInstantDeserializer() {
        super(Instant.class);
    }

    @Override
    public Instant deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return context.reportInputMismatch(Instant.class, "An ISO-8601 instant string is required.");
        }
        try {
            return Instant.parse(parser.getText());
        } catch (DateTimeParseException exception) {
            return context.reportInputMismatch(Instant.class, "An ISO-8601 instant string is required.");
        }
    }
}
