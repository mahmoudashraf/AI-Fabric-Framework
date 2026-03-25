package com.ai.infrastructure.intent.action.connector;

import com.ai.infrastructure.intent.action.AIActionHandler;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.AIActionRegistryContributor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Registers connector-backed actions defined in a file-based action catalog.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectorActionsRegistryContributor implements AIActionRegistryContributor {

    private final AIActionCatalogProperties catalogProperties;
    private final AIActionConnectorProperties connectorProperties;
    private final ConnectorActionCatalogLoader catalogLoader;
    private final ActionConnectorExecutor executor;

    @Override
    public List<AIActionHandler> getHandlers() {
        if (catalogProperties == null
            || catalogProperties.getSources() == null
            || catalogProperties.getSources().isEmpty()) {
            return List.of();
        }

        List<ConnectorActionDefinition> definitions = catalogLoader != null
            ? catalogLoader.loadActions(catalogProperties.getSources())
            : List.of();

        if (definitions.isEmpty()) {
            return List.of();
        }

        if (connectorProperties == null || !StringUtils.hasText(connectorProperties.getBaseUrl())) {
            throw new IllegalStateException("Connector actions are configured, but ai.actions.connector.baseUrl is missing.");
        }

        List<AIActionHandler> out = new ArrayList<>();
        for (ConnectorActionDefinition def : definitions) {
            if (def == null) {
                continue;
            }
            AIActionMetaData meta = ConnectorActionMetadataMapper.toMetadata(def);
            Set<String> sensitive = ConnectorActionMetadataMapper.extractSensitiveParams(def);
            out.add(new ConnectorAIActionHandler(
                meta,
                def.requiresConfirmation(),
                def.confirmationMessage(),
                sensitive,
                executor
            ));
        }

        log.info("Loaded {} connector action(s) from {} source(s).", out.size(), catalogProperties.getSources().size());
        return List.copyOf(out);
    }

    @Override
    public String getSourceName() {
        return "connector-catalog";
    }
}

