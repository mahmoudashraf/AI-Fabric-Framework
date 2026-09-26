package com.ai.fabric.runtime.documents;

import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

final class DocumentConnectorSupport {

    private DocumentConnectorSupport() {
    }

    static String normalizeReference(String value) {
        if (!StringUtils.hasText(value)) {
            throw invalid("Document object reference is required.");
        }
        String normalized = value.trim().replace('\\', '/');
        if (normalized.length() > 1024
            || normalized.startsWith("/")
            || normalized.contains("://")
            || normalized.chars().anyMatch(Character::isISOControl)) {
            throw invalid("Document object reference is invalid.");
        }
        StringBuilder safe = new StringBuilder();
        for (String segment : normalized.split("/", -1)) {
            if (!StringUtils.hasText(segment) || ".".equals(segment) || "..".equals(segment)) {
                throw invalid("Document object reference escapes the configured source scope.");
            }
            if (!safe.isEmpty()) {
                safe.append('/');
            }
            safe.append(segment);
        }
        return safe.toString();
    }

    static String extension(String objectReference) {
        String name = displayName(objectReference).toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : "";
    }

    static String displayName(String objectReference) {
        int slash = objectReference.lastIndexOf('/');
        String name = slash >= 0 ? objectReference.substring(slash + 1) : objectReference;
        return name.length() <= 255 ? name : name.substring(name.length() - 255);
    }

    static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static DocumentKnowledgeException invalid(String message) {
        return new DocumentKnowledgeException("INVALID_DOCUMENT_SOURCE", HttpStatus.BAD_REQUEST, message);
    }
}
