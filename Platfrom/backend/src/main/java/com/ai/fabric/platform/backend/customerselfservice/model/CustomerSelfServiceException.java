package com.ai.fabric.platform.backend.customerselfservice.model;

import org.springframework.http.HttpStatus;

public class CustomerSelfServiceException extends RuntimeException {
    public enum Code {
        ACTIVE_CUSTOMER_SCOPE_REQUIRED, STAFF_ADMIN_REQUIRED, OPERATION_FORBIDDEN,
        ENTITLEMENT_REQUIRED, ENTITLEMENT_EXPIRED, ENTITLEMENT_REVOKED, INVITE_OWNER_MISMATCH,
        RESOURCE_SCOPE_MISMATCH, UNENTITLED_TARGET, INVALID_CONTRACT, REVISION_CONFLICT,
        OPERATION_KEY_CONFLICT, OFFER_IMMUTABLE, APPROVED_POLICY_REQUIRED,
        ARTIFACT_REQUIRED, REVIEWED_COMPOSITION_CHANGED, TRUSTED_PLACEMENT_REQUIRED,
        PLACEMENT_DENIED, PREFLIGHT_REQUIRED, PLACEMENT_EVIDENCE_REQUIRED, PLACEMENT_EVIDENCE_STALE,
        PREREQUISITE_EVIDENCE_REQUIRED,
        STORAGE_REQUIRED, IDENTITY_REQUIRED, CAPACITY_REQUIRED, NOTIFICATIONS_REQUIRED,
        OPERATING_POLICY_REQUIRED, SETTING_FORBIDDEN, BINDING_SLOT_FORBIDDEN
    }

    private final Code code;
    private final HttpStatus status;

    public CustomerSelfServiceException(Code code, HttpStatus status) {
        super(code.name());
        this.code = code;
        this.status = status;
    }

    public Code code() { return code; }
    public HttpStatus status() { return status; }

    public static CustomerSelfServiceException deny(Code code) {
        return new CustomerSelfServiceException(code, HttpStatus.FORBIDDEN);
    }
    public static CustomerSelfServiceException conflict(Code code) {
        return new CustomerSelfServiceException(code, HttpStatus.CONFLICT);
    }
    public static CustomerSelfServiceException invalid() {
        return new CustomerSelfServiceException(Code.INVALID_CONTRACT, HttpStatus.BAD_REQUEST);
    }
}
