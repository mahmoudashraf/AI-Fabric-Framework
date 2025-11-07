package com.ai.infrastructure.intent.action;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Registry that discovers {@link ActionHandler} beans and exposes utilities to look them up by action name.
 */
@Slf4j
@Service
public class ActionHandlerRegistry {

    private final List<ActionHandler> handlers;
    private Map<String, ActionHandler> handlerByActionName = Collections.emptyMap();
    private Map<String, AIActionMetaData> metadataByActionName = Collections.emptyMap();

    public ActionHandlerRegistry(List<ActionHandler> handlers) {
        this.handlers = handlers == null ? List.of() : List.copyOf(handlers);
    }

    @PostConstruct
    void initialize() {
        Map<String, ActionHandler> handlerMap = new LinkedHashMap<>();
        Map<String, AIActionMetaData> metadataMap = new LinkedHashMap<>();

        for (ActionHandler handler : handlers) {
            if (handler == null) {
                continue;
            }
            AIActionMetaData metaData = safeMetadata(handler);
            if (metaData == null || metaData.getName() == null || metaData.getName().isBlank()) {
                log.warn("Skipping ActionHandler {} because metadata is missing a name", handler.getClass().getName());
                continue;
            }

            String key = normalize(metaData.getName());
            if (handlerMap.containsKey(key)) {
                log.warn("Duplicate ActionHandler registration for action '{}'. Keeping {}", metaData.getName(),
                    handlerMap.get(key).getClass().getName());
                continue;
            }

            handlerMap.put(key, handler);
            metadataMap.put(key, metaData);
        }

        handlerByActionName = Collections.unmodifiableMap(handlerMap);
        metadataByActionName = Collections.unmodifiableMap(metadataMap);

        log.info("ActionHandlerRegistry initialized with {} action handler(s)", handlerByActionName.size());
    }

    public Optional<ActionHandler> findHandler(String actionName) {
        if (actionName == null || actionName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(handlerByActionName.get(normalize(actionName)));
    }

    public Optional<AIActionMetaData> findMetadata(String actionName) {
        if (actionName == null || actionName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(metadataByActionName.get(normalize(actionName)));
    }

    public List<AIActionMetaData> getAllMetadata() {
        return List.copyOf(metadataByActionName.values());
    }

    public Map<String, ActionHandler> getHandlerMap() {
        return handlerByActionName;
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private AIActionMetaData safeMetadata(ActionHandler handler) {
        try {
            AIActionMetaData metadata = handler.getActionMetadata();
            if (metadata == null) {
                return null;
            }
            AIActionMetaData.AIActionMetaDataBuilder builder = AIActionMetaData.builder()
                .name(metadata.getName())
                .description(metadata.getDescription())
                .category(metadata.getCategory());

            if (metadata.getParameters() != null) {
                builder.parameters(metadata.getParameters());
            }
            return builder.build();
        } catch (Exception ex) {
            log.error("ActionHandler {} threw an exception when gathering metadata", handler.getClass().getName(), ex);
            return null;
        }
    }
}
