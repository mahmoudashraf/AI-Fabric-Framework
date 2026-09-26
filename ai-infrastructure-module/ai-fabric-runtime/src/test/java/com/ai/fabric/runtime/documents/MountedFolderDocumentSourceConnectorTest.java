package com.ai.fabric.runtime.documents;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MountedFolderDocumentSourceConnectorTest {

    @TempDir
    Path root;

    @Test
    void discoversAndMaterializesOnlyApprovedFilesBelowTheTrustedRoot() throws Exception {
        Files.createDirectories(root.resolve("team"));
        Files.writeString(root.resolve("team/handbook.txt"), "Approved handbook content");
        Files.writeString(root.resolve("team/ignored.pdf"), "not supported");

        var connector = connector(1024);

        var page = connector.discover(null, 20);
        assertThat(page.objects())
            .extracting(DocumentSourceConnector.SourceObject::objectReference)
            .containsExactly("team/handbook.txt");

        var source = connector.stat("team/handbook.txt");
        try (var materialized = connector.materialize(source)) {
            assertThat(Files.readString(materialized.path())).isEqualTo("Approved handbook content");
            assertThat(materialized.path()).startsWith(materialized.trustedRoot());
            assertThat(Files.isSameFile(materialized.path(), root.resolve("team/handbook.txt"))).isFalse();
            assertThat(materialized.contentFingerprint()).hasSize(64);
        }
        assertThat(Files.exists(root.resolve("team/handbook.txt"))).isTrue();
    }

    @Test
    void rejectsTraversalAndSymbolicLinks() throws Exception {
        Path outside = Files.createTempFile(root.getParent(), "outside-document-", ".txt");
        Files.writeString(outside, "outside");
        Path link = root.resolve("linked.txt");
        Files.createSymbolicLink(link, outside);
        var connector = connector(1024);

        assertThatThrownBy(() -> connector.stat("../" + outside.getFileName()))
            .isInstanceOf(DocumentKnowledgeException.class)
            .extracting(exception -> ((DocumentKnowledgeException) exception).code())
            .isEqualTo("INVALID_DOCUMENT_SOURCE");
        assertThatThrownBy(() -> connector.stat("linked.txt"))
            .isInstanceOf(DocumentKnowledgeException.class)
            .extracting(exception -> ((DocumentKnowledgeException) exception).code())
            .isEqualTo("INVALID_DOCUMENT_SOURCE");
    }

    @Test
    void rejectsFilesLargerThanTheConfiguredBoundary() throws Exception {
        Files.writeString(root.resolve("large.txt"), "123456789");

        assertThatThrownBy(() -> connector(8).stat("large.txt"))
            .isInstanceOf(DocumentKnowledgeException.class)
            .extracting(exception -> ((DocumentKnowledgeException) exception).code())
            .isEqualTo("DOCUMENT_LIMIT_EXCEEDED");
    }

    @Test
    void discoverySkipsOversizedFilesAndMaterializationRejectsAChangedRevision() throws Exception {
        Files.writeString(root.resolve("large.txt"), "123456789");
        Files.writeString(root.resolve("stable.txt"), "1234");
        var connector = connector(8);

        assertThat(connector.discover(null, 20).objects())
            .extracting(DocumentSourceConnector.SourceObject::objectReference)
            .containsExactly("stable.txt");

        var observed = connector.stat("stable.txt");
        Files.writeString(root.resolve("stable.txt"), "changed");
        assertThatThrownBy(() -> connector.materialize(observed))
            .isInstanceOf(DocumentKnowledgeException.class)
            .extracting(exception -> ((DocumentKnowledgeException) exception).code())
            .isEqualTo("DOCUMENT_SOURCE_CHANGED");
    }

    @Test
    void contentFingerprintDetectsSameSizeChangesWhenTheTimestampIsPreserved() throws Exception {
        Path sourcePath = root.resolve("stable.txt");
        Files.writeString(sourcePath, "1234");
        FileTime originalModified = Files.getLastModifiedTime(sourcePath);
        var connector = connector(8);
        var observed = connector.stat("stable.txt");

        Files.writeString(sourcePath, "5678");
        Files.setLastModifiedTime(sourcePath, originalModified);
        var changed = connector.stat("stable.txt");

        assertThat(changed.providerRevisionFingerprint())
            .isNotEqualTo(observed.providerRevisionFingerprint());
        assertThatThrownBy(() -> connector.materialize(observed))
            .isInstanceOf(DocumentKnowledgeException.class)
            .extracting(exception -> ((DocumentKnowledgeException) exception).code())
            .isEqualTo("DOCUMENT_SOURCE_CHANGED");
    }

    private MountedFolderDocumentSourceConnector connector(long maxBytes) {
        DocumentKnowledgeProperties.Policy policy = new DocumentKnowledgeProperties.Policy();
        policy.setMaxSourceBytes(maxBytes);
        policy.setAllowedExtensions(java.util.List.of(".txt", ".json"));
        DocumentTemporaryFileManager temporaryFiles = new DocumentTemporaryFileManager(
            root.resolve(".runtime-temp"),
            Duration.ofHours(1),
            Clock.systemUTC()
        );
        temporaryFiles.initialize();
        return new MountedFolderDocumentSourceConnector(root, "demo-mount", policy, temporaryFiles);
    }
}
