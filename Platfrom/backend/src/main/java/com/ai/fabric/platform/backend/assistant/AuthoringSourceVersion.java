package com.ai.fabric.platform.backend.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

final class AuthoringSourceVersion {
    private AuthoringSourceVersion() { }
    static String of(JsonNode source) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
}
