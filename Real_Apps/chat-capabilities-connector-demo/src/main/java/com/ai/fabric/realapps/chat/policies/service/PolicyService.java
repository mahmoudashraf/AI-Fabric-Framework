package com.ai.fabric.realapps.chat.policies.service;

import com.ai.fabric.realapps.chat.policies.domain.Policy;
import com.ai.fabric.realapps.chat.policies.repo.PolicyRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PolicyService {

    private final PolicyRepository policyRepository;

    @Transactional(readOnly = true)
    public List<Policy> list(int limit) {
        int effectiveLimit = limit <= 0 ? 50 : Math.min(limit, 500);
        return policyRepository.findAll().stream().limit(effectiveLimit).toList();
    }

    @Transactional(readOnly = true)
    public Policy get(long id) {
        return policyRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Policy not found: " + id));
    }

    @Transactional
    public Policy createPolicy(String title, String text, String classification) {
        if (!StringUtils.hasText(title)) {
            throw new IllegalArgumentException("title is required");
        }
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("text is required");
        }
        if (!StringUtils.hasText(classification)) {
            throw new IllegalArgumentException("classification is required");
        }

        Policy policy = new Policy();
        policy.setTitle(title.trim());
        policy.setText(text.trim());
        policy.setClassification(classification.trim());
        return policyRepository.save(policy);
    }

    @Transactional
    public Policy updatePolicy(long id, String title, String text, String classification) {
        Policy policy = get(id);

        if (StringUtils.hasText(title)) {
            policy.setTitle(title.trim());
        }
        if (StringUtils.hasText(text)) {
            policy.setText(text.trim());
        }
        if (classification != null) {
            policy.setClassification(StringUtils.hasText(classification) ? classification.trim() : null);
        }

        if (!StringUtils.hasText(policy.getClassification())) {
            throw new IllegalArgumentException("classification is required");
        }

        return policyRepository.save(policy);
    }

    @Transactional
    public Policy deletePolicy(long id) {
        Policy policy = get(id);
        policyRepository.delete(policy);
        return policy;
    }

    @Transactional(readOnly = true)
    public long count() {
        return policyRepository.count();
    }

    @Transactional(readOnly = true)
    public List<Policy> search(String query, int limit, double threshold) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        int effectiveLimit = limit <= 0 ? 10 : Math.min(limit, 50);

        String normalized = query.trim().toLowerCase();
        return policyRepository.findAll().stream()
            .filter(p -> matches(p, normalized))
            .sorted(Comparator.comparing(Policy::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Policy::getId, Comparator.nullsLast(Comparator.naturalOrder())))
            .limit(effectiveLimit)
            .toList();
    }

    private boolean matches(Policy policy, String normalizedLowerQuery) {
        if (policy == null || !StringUtils.hasText(normalizedLowerQuery)) {
            return false;
        }
        return containsIgnoreCase(policy.getTitle(), normalizedLowerQuery)
            || containsIgnoreCase(policy.getClassification(), normalizedLowerQuery)
            || containsIgnoreCase(policy.getText(), normalizedLowerQuery);
    }

    private boolean containsIgnoreCase(String haystack, String needleLower) {
        if (!StringUtils.hasText(haystack) || !StringUtils.hasText(needleLower)) {
            return false;
        }
        return haystack.toLowerCase().contains(needleLower);
    }
}
