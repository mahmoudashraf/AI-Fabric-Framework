package com.ai.behavior.service;

import com.ai.behavior.exception.BehaviorAnalysisException;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.model.BehaviorQuery;
import com.ai.behavior.storage.BehaviorDataProvider;
import com.ai.behavior.storage.BehaviorSignalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BehaviorQueryService {

    private final BehaviorSignalRepository eventRepository;
    private final BehaviorDataProvider dataProvider;

    @Transactional(readOnly = true)
    public BehaviorSignal getSignal(UUID id) {
        return eventRepository.findById(id)
            .orElseThrow(() -> new BehaviorAnalysisException("Behavior signal not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<BehaviorSignal> recentEvents(UUID userId, int limit) {
        return dataProvider.getRecentEvents(userId, limit);
    }

    @Transactional(readOnly = true)
    public List<BehaviorSignal> query(UUID userId, BehaviorQuery query) {
        BehaviorQuery effectiveQuery = query.toBuilder()
            .userId(userId)
            .build();
        return dataProvider.query(effectiveQuery);
    }
}
