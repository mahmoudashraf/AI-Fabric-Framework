package com.loomai.operations.telemetry;

public class TelemetryException extends RuntimeException {
    private final int status;
    private final String code;

    public TelemetryException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }

    static TelemetryException invalid() {
        return new TelemetryException(400, "TELEMETRY_SAMPLE_INVALID", "Telemetry JSON or fields are invalid.");
    }
}
