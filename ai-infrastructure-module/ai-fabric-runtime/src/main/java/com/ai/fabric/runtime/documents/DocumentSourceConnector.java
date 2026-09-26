package com.ai.fabric.runtime.documents;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public interface DocumentSourceConnector extends AutoCloseable {

    DocumentKnowledgeProperties.ConnectorType type();

    ConnectorStatus status();

    DiscoveryPage discover(String cursor, int requestedLimit);

    SourceObject stat(String objectReference);

    MaterializedDocument materialize(SourceObject object);

    @Override
    default void close() {
    }

    record ConnectorStatus(
        boolean ready,
        String connectorType,
        String bindingRef,
        String scopeDigest,
        boolean objectVersioningAvailable,
        String errorCode,
        String checkedAt
    ) { }

    record DiscoveryPage(List<SourceObject> objects, String nextCursor) {
        public DiscoveryPage {
            objects = objects == null ? List.of() : List.copyOf(objects);
        }
    }

    record SourceObject(
        String objectReference,
        String displayName,
        String mediaType,
        long contentLength,
        String providerVersionId,
        String etag,
        Instant lastModified,
        String providerRevisionFingerprint
    ) { }

    record MaterializedDocument(
        Path path,
        Path trustedRoot,
        SourceObject sourceObject,
        String contentFingerprint,
        Runnable cleanup
    ) implements AutoCloseable {
        @Override
        public void close() {
            if (cleanup != null) {
                cleanup.run();
            }
        }
    }
}
