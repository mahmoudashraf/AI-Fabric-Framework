package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import org.springframework.stereotype.Component;

import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;

@Component
final class AdmissionOwnerContactResolver {
    private static final String REFERENCE_PREFIX = "platform-user:";
    private final PlatformUserRepository users;

    AdmissionOwnerContactResolver(PlatformUserRepository users) {
        this.users = users;
    }

    OwnerContact resolve(String customerId, String evidenceReference) {
        if (customerId == null || customerId.isBlank() || evidenceReference == null
            || !evidenceReference.startsWith(REFERENCE_PREFIX)) {
            throw unavailable("VERIFIED_OWNER_CONTACT_REQUIRED");
        }
        String userId = evidenceReference.substring(REFERENCE_PREFIX.length());
        if (userId.isBlank() || userId.length() > 64 || !evidenceReference.equals(REFERENCE_PREFIX + userId)) {
            throw unavailable("VERIFIED_OWNER_CONTACT_REQUIRED");
        }
        PlatformUserEntity user = users.findById(userId)
            .orElseThrow(() -> unavailable("VERIFIED_OWNER_CONTACT_REQUIRED"));
        if (!customerId.equals(user.getCustomerId()) || !"CUSTOMER_ADMIN".equalsIgnoreCase(user.getRole())
            || !"ACTIVE".equalsIgnoreCase(user.getStatus()) || user.getEmail() == null || user.getEmail().isBlank()) {
            throw unavailable("VERIFIED_OWNER_CONTACT_REQUIRED");
        }
        return new OwnerContact(user.getId(), user.getEmail().trim().toLowerCase(java.util.Locale.ROOT), evidenceReference);
    }

    private AdmissionFailure unavailable(String code) {
        return new AdmissionFailure(PRECONDITION_FAILED, code);
    }

    record OwnerContact(String userId, String email, String evidenceReference) { }
}
