package com.ai.behavior.storage;

import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.model.BehaviorQuery;
import com.ai.behavior.repository.BehaviorEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Primary
@RequiredArgsConstructor
public class DatabaseBehaviorProvider implements BehaviorDataProvider {

    private final BehaviorEventRepository repository;

    @Override
    public List<BehaviorEvent> query(BehaviorQuery query) {
        var specification = query.toSpecification();
        if (query.getLimit() != null) {
            return repository.findAll(specification, PageRequest.of(query.getOffset() != null ? query.getOffset() : 0, query.getLimit())).getContent();
        }
        return repository.findAll(specification);
    }

    @Override
    public String getProviderType() {
        return "database";
    }
}
