package com.ai.fabric.realapps.itsupport.action;

import java.util.Map;

abstract class BaseActionHandler {

    long requireTicketNumber(Map<String, Object> params) {
        Object raw = params != null ? params.get("ticketNumber") : null;
        if (raw instanceof Number n) {
            return n.longValue();
        }
        if (raw != null) {
            try {
                return Long.parseLong(raw.toString());
            } catch (NumberFormatException ignored) {
                // fallthrough
            }
        }
        throw new IllegalArgumentException("ticketNumber is required");
    }

    String stringParam(Map<String, Object> params, String key) {
        Object raw = params != null ? params.get(key) : null;
        return raw != null ? raw.toString() : null;
    }
}

