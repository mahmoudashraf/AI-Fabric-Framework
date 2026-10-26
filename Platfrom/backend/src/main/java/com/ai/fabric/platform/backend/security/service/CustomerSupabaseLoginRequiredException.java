package com.ai.fabric.platform.backend.security.service;

import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

public class CustomerSupabaseLoginRequiredException extends ResponseStatusException {
    public CustomerSupabaseLoginRequiredException() {
        super(UNAUTHORIZED, "Sign in using your Supabase customer account.");
    }
}
