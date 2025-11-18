package com.ai.behavior.storage.impl;

import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.model.BehaviorQuery;
import com.ai.behavior.storage.BehaviorDataProvider;
import com.ai.behavior.storage.BehaviorSignalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DatabaseBehaviorProvider implements BehaviorDataProvider {

    private static final String PROVIDER_TYPE = "database";

    private final BehaviorSignalRepository repository;

    @Override
    public List<BehaviorSignal> query(BehaviorQuery query) {
        Specification<BehaviorSignal> specification = Specification.where(null);

        if (query.getUserId() != null) {
            specification = specification.and((root, cq, cb) -> cb.equal(root.get("userId"), query.getUserId()));
        }

        if (query.getSessionId() != null) {
            specification = specification.and((root, cq, cb) -> cb.equal(root.get("sessionId"), query.getSessionId()));
        }

        if (query.getSchemaId() != null) {
            specification = specification.and((root, cq, cb) -> cb.equal(root.get("schemaId"), query.getSchemaId()));
        }

        if (query.getEntityType() != null) {
            specification = specification.and((root, cq, cb) -> cb.equal(root.get("entityType"), query.getEntityType()));
        }

        if (query.getEntityId() != null) {
            specification = specification.and((root, cq, cb) -> cb.equal(root.get("entityId"), query.getEntityId()));
        }

        if (query.getStartTime() != null) {
            specification = specification.and((root, cq, cb) -> cb.greaterThanOrEqualTo(root.get("timestamp"), query.getStartTime()));
        }

        if (query.getEndTime() != null) {
            specification = specification.and((root, cq, cb) -> cb.lessThanOrEqualTo(root.get("timestamp"), query.getEndTime()));
        }

        if (!query.safeAttributeEquals().isEmpty()) {
            for (var entry : query.safeAttributeEquals().entrySet()) {
                specification = specification.and((root, cq, cb) -> cb.equal(
                    cb.function("jsonb_extract_path_text", String.class, root.get("attributes"), cb.literal(entry.getKey())),
                    entry.getValue() != null ? entry.getValue().toString() : null
                ));
            }
        }

        int limit = Math.max(1, query.getLimit());
        int offset = Math.max(0, query.getOffset());
        int page = offset / limit;
        Sort sort = query.isAscending()
            ? Sort.by("timestamp").ascending()
            : Sort.by("timestamp").descending();

        Pageable pageable = PageRequest.of(page, limit, sort);
        return repository.findAll(specification, pageable).getContent();
    }

    @Override
    public String getProviderType() {
        return PROVIDER_TYPE;
    }
}
