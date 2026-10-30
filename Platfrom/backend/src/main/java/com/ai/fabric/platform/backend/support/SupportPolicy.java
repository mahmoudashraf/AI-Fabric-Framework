package com.ai.fabric.platform.backend.support;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class SupportPolicy {
    private SupportPolicy() { }
    static ResponseStatusException denied() { return error(HttpStatus.FORBIDDEN, "SUPPORT_ACCESS_DENIED"); }
    static ResponseStatusException missing() { return error(HttpStatus.NOT_FOUND, "SUPPORT_CASE_NOT_FOUND"); }
    static ResponseStatusException conflict(String code) { return error(HttpStatus.CONFLICT, code); }
    static ResponseStatusException bad(String code) { return error(HttpStatus.BAD_REQUEST, code); }
    static ResponseStatusException unavailable() { return error(HttpStatus.SERVICE_UNAVAILABLE, "SUPPORT_NOT_CONFIGURED"); }
    static ResponseStatusException error(HttpStatus status, String code) { return new ResponseStatusException(status, code); }
    static String required(String value, int max, String field) {
        if (value == null || value.isBlank() || value.length() > max) throw bad("SUPPORT_INVALID_" + field);
        return value.trim();
    }
    static String optional(String value, int max, String field) {
        if (value == null || value.isBlank()) return null;
        if (value.length() > max) throw bad("SUPPORT_INVALID_" + field);
        return value.trim();
    }
}
