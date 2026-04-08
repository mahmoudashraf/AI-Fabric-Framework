package com.ai.fabric.runtime.auth;

public enum RuntimeAuthCallerType {
    LEGACY_REQUEST,
    TRUSTED_BACKEND,
    PLATFORM_PROXY,
    PUBLIC_BROWSER,
    SYSTEM_PROCESS
}
