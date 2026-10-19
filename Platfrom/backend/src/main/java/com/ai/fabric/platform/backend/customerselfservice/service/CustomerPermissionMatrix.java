package com.ai.fabric.platform.backend.customerselfservice.service;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;

import java.util.Arrays;
import java.util.Set;

public final class CustomerPermissionMatrix {
    private CustomerPermissionMatrix() { }

    public static PermissionRow row(Operation operation) {
        Set<Setting> settings = switch (operation) {
            case BOOTSTRAP_CONFIGURATION, BOOTSTRAP -> Set.of(Setting.DEPLOYMENT_NAME, Setting.AI_STATE_STORAGE_MODE);
            case DRAFT_EDIT -> Set.of(Setting.DEPLOYMENT_NAME, Setting.INTERACTION_PROFILE_ID);
            case VERSION_PUBLISH -> Set.of();
            case APP_CONFIGURE -> Set.of(Setting.APP_TITLE, Setting.LOGO_ASSET_ID, Setting.THEME_PROFILE_ID,
                Setting.INITIAL_VIEW, Setting.INTERACTION_PROFILE_ID);
            case DOMAIN_CONFIGURE -> Set.of(Setting.APP_DOMAIN);
            case AUTH_CONFIGURE -> Set.of(Setting.AUTH_PROFILE_ID);
            case SOURCE_INDEX, SOURCE_REFRESH -> Set.of(Setting.DOCUMENT_AUDIENCE);
            default -> Set.of();
        };
        StaffAccess staffAccess = switch (operation) {
            case READ, TEMPLATE_SELECT, SOURCE_DISCOVER, SOURCE_PREVIEW -> StaffAccess.VIEWER;
            case DRAFT_EDIT, VERSION_PUBLISH, APP_CONFIGURE, DOMAIN_CONFIGURE, AUTH_CONFIGURE -> StaffAccess.EDITOR;
            case BOOTSTRAP_CONFIGURATION, BINDING_REPLACE, BINDING_REVOKE,
                 INBOX_OPERATOR_ENROLL, INBOX_OPERATOR_ROTATE, INBOX_OPERATOR_DELIVER, INBOX_OPERATOR_REVOKE,
                 STAGING_RETAIN, STAGING_EXTEND, TARGET_RETIRE -> StaffAccess.ADMIN;
            default -> StaffAccess.OPERATOR;
        };
        Set<TargetIntent> intents = switch (operation) {
            case BOOTSTRAP_CONFIGURATION, BOOTSTRAP, STAGING_RETAIN, STAGING_EXTEND -> Set.of(TargetIntent.CUSTOMER_STAGING);
            case PROMOTION_PLAN, PROMOTION_APPLY -> Set.of(TargetIntent.CUSTOMER_PRODUCTION);
            default -> Set.of(TargetIntent.CUSTOMER_STAGING, TargetIntent.CUSTOMER_PRODUCTION);
        };
        boolean admission = switch (operation) {
            case BOOTSTRAP, APPLY, PROMOTION_PLAN, PROMOTION_APPLY, ACTIVATE, ROLLBACK, STAGING_RETAIN, STAGING_EXTEND -> true;
            default -> false;
        };
        boolean ownedBinding = operation == Operation.BINDING_REPLACE || operation == Operation.BINDING_REVOKE
            || operation == Operation.INBOX_OPERATOR_ENROLL || operation == Operation.INBOX_OPERATOR_ROTATE
            || operation == Operation.INBOX_OPERATOR_DELIVER || operation == Operation.INBOX_OPERATOR_REVOKE;
        return new PermissionRow(operation, staffAccess, intents, settings, ownedBinding, admission);
    }

    public static java.util.List<PermissionRow> rows(Set<Operation> operations) {
        return Arrays.stream(Operation.values()).filter(operations::contains).map(CustomerPermissionMatrix::row).toList();
    }
}
