package com.ai.fabric.runtime.documents;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3DocumentSourceConnectorTest {

    @TempDir
    Path temp;

    @Test
    void discoveryIsConfinedToTheConfiguredPrefixAndReturnsVersionEvidence() {
        S3Client client = mock(S3Client.class);
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
            ListObjectsV2Response.builder()
                .contents(
                    S3Object.builder().key("approved/handbook.txt").size(12L).build(),
                    S3Object.builder().key("outside/secret.txt").size(10L).build(),
                    S3Object.builder().key("approved/ignored.pdf").size(10L).build()
                )
                .isTruncated(false)
                .build()
        );
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(
            HeadObjectResponse.builder()
                .contentLength(12L)
                .contentType("text/plain")
                .versionId("version-7")
                .eTag("etag-7")
                .lastModified(Instant.parse("2026-09-26T00:00:00Z"))
                .build()
        );

        var connector = connector(client);
        var page = connector.discover(null, 25);

        assertThat(page.objects()).singleElement().satisfies(source -> {
            assertThat(source.objectReference()).isEqualTo("handbook.txt");
            assertThat(source.providerVersionId()).isEqualTo("version-7");
            assertThat(source.providerRevisionFingerprint()).hasSize(64);
        });
        ArgumentCaptor<ListObjectsV2Request> listRequest = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(client).listObjectsV2(listRequest.capture());
        assertThat(listRequest.getValue().bucket()).isEqualTo("customer-bucket");
        assertThat(listRequest.getValue().prefix()).isEqualTo("approved/");
        ArgumentCaptor<HeadObjectRequest> headRequest = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(client).headObject(headRequest.capture());
        assertThat(headRequest.getValue().key()).isEqualTo("approved/handbook.txt");
    }

    @Test
    void rejectsCallerControlledAbsoluteOrTraversalReferencesBeforeS3Access() {
        S3Client client = mock(S3Client.class);
        var connector = connector(client);

        assertThatThrownBy(() -> connector.stat("../secret.txt"))
            .isInstanceOf(DocumentKnowledgeException.class)
            .extracting(exception -> ((DocumentKnowledgeException) exception).code())
            .isEqualTo("INVALID_DOCUMENT_SOURCE");
        assertThatThrownBy(() -> connector.stat("https://attacker.example/secret.txt"))
            .isInstanceOf(DocumentKnowledgeException.class)
            .extracting(exception -> ((DocumentKnowledgeException) exception).code())
            .isEqualTo("INVALID_DOCUMENT_SOURCE");
    }

    @Test
    void discoverySkipsOversizedObjectsWithoutHidingEligibleFiles() {
        S3Client client = mock(S3Client.class);
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
            ListObjectsV2Response.builder()
                .contents(
                    S3Object.builder().key("approved/too-large.txt").size(2048L).build(),
                    S3Object.builder().key("approved/handbook.txt").size(12L).build()
                )
                .isTruncated(false)
                .build()
        );
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(
            HeadObjectResponse.builder()
                .contentLength(12L)
                .contentType("text/plain")
                .eTag("etag-1")
                .lastModified(Instant.parse("2026-09-26T00:00:00Z"))
                .build()
        );

        assertThat(connector(client).discover(null, 25).objects())
            .extracting(DocumentSourceConnector.SourceObject::objectReference)
            .containsExactly("handbook.txt");
    }

    @Test
    void refusesToMaterializeARevisionThatChangedAfterStat() {
        S3Client client = mock(S3Client.class);
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(
            HeadObjectResponse.builder()
                .contentLength(12L)
                .contentType("text/plain")
                .eTag("etag-1")
                .lastModified(Instant.parse("2026-09-26T00:00:00Z"))
                .build(),
            HeadObjectResponse.builder()
                .contentLength(13L)
                .contentType("text/plain")
                .eTag("etag-2")
                .lastModified(Instant.parse("2026-09-26T00:01:00Z"))
                .build()
        );
        var connector = connector(client);
        var observed = connector.stat("handbook.txt");

        assertThatThrownBy(() -> connector.materialize(observed))
            .isInstanceOf(DocumentKnowledgeException.class)
            .extracting(exception -> ((DocumentKnowledgeException) exception).code())
            .isEqualTo("DOCUMENT_SOURCE_CHANGED");
        verify(client, never()).getObject(any(software.amazon.awssdk.services.s3.model.GetObjectRequest.class));
    }

    @Test
    void rejectsATruncatedObjectBodyInsteadOfIndexingPartialEvidence() {
        S3Client client = mock(S3Client.class);
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(
            HeadObjectResponse.builder()
                .contentLength(12L)
                .contentType("text/plain")
                .eTag("etag-1")
                .lastModified(Instant.parse("2026-09-26T00:00:00Z"))
                .build()
        );
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(
            responseStream("short".getBytes(java.nio.charset.StandardCharsets.UTF_8))
        );
        var connector = connector(client);
        var observed = connector.stat("handbook.txt");

        assertThatThrownBy(() -> connector.materialize(observed))
            .isInstanceOf(DocumentKnowledgeException.class)
            .extracting(exception -> ((DocumentKnowledgeException) exception).code())
            .isEqualTo("DOCUMENT_SOURCE_READ_INCOMPLETE");
    }

    @Test
    void rejectsAnObjectBodyThatExceedsTheInspectedAndConfiguredBoundary() {
        S3Client client = mock(S3Client.class);
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(
            HeadObjectResponse.builder()
                .contentLength(4L)
                .contentType("text/plain")
                .eTag("etag-1")
                .lastModified(Instant.parse("2026-09-26T00:00:00Z"))
                .build()
        );
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(
            responseStream(new byte[1025])
        );
        var connector = connector(client);
        var observed = connector.stat("handbook.txt");

        assertThatThrownBy(() -> connector.materialize(observed))
            .isInstanceOf(DocumentKnowledgeException.class)
            .extracting(exception -> ((DocumentKnowledgeException) exception).code())
            .isEqualTo("DOCUMENT_LIMIT_EXCEEDED");
    }

    private ResponseInputStream<GetObjectResponse> responseStream(byte[] body) {
        return new ResponseInputStream<>(
            GetObjectResponse.builder().contentLength((long) body.length).build(),
            new ByteArrayInputStream(body)
        );
    }

    private S3DocumentSourceConnector connector(S3Client client) {
        DocumentKnowledgeProperties.Policy policy = new DocumentKnowledgeProperties.Policy();
        policy.setMaxSourceBytes(1024);
        policy.setAllowedExtensions(java.util.List.of(".txt", ".json"));
        DocumentTemporaryFileManager temporaryFiles = new DocumentTemporaryFileManager(
            temp,
            Duration.ofHours(1),
            Clock.systemUTC()
        );
        temporaryFiles.initialize();
        return new S3DocumentSourceConnector(
            client,
            "customer-bucket",
            "approved",
            "resource-handle-1",
            true,
            policy,
            temporaryFiles
        );
    }
}
