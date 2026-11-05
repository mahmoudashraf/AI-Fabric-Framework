package com.ai.fabric.platform.backend.infrastructure.monitoring;

final class MonitoringUnavailableException extends RuntimeException {
    MonitoringUnavailableException(String code) { super(code); }
}
