package com.ai.fabric.runtime.documents;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentSourceScopeTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path root;

    @Test
    void s3IdentityIncludesEndpointBucketRegionAndPrefixButNotRotatingCredentials() {
        var properties = s3();
        var options = properties.getConnector().getS3();
        String original = DocumentSourceScope.storageDigest(properties, mapper);
        options.setAccessKey("rotated-access-key");
        options.setSecretKey("rotated-secret-key");
        options.setSessionToken("rotated-session-token");
        options.setEndpoint("https://STORAGE.example.test:443/");
        options.setPrefix("/approved");
        assertThat(DocumentSourceScope.storageDigest(properties, mapper)).isEqualTo(original);

        options.setEndpoint("https://other.example.test");
        assertThat(DocumentSourceScope.storageDigest(properties, mapper)).isNotEqualTo(original);
        options.setEndpoint("https://storage.example.test:8443");
        assertThat(DocumentSourceScope.storageDigest(properties, mapper)).isNotEqualTo(original);
        options.setEndpoint("https://storage.example.test");
        options.setBucket("another-bucket");
        assertThat(DocumentSourceScope.storageDigest(properties, mapper)).isNotEqualTo(original);
        options.setBucket("customer-bucket");
        options.setPrefix("different/");
        assertThat(DocumentSourceScope.storageDigest(properties, mapper)).isNotEqualTo(original);
        options.setPrefix("approved/");
        options.setRegion("eu-west-1");
        assertThat(DocumentSourceScope.storageDigest(properties, mapper)).isNotEqualTo(original);
    }

    @Test
    void incompleteOrNonOriginS3IdentityCannotAttestAStoredSource() {
        var properties = s3();
        properties.getConnector().getS3().setEndpoint("https://storage.example.test/another-namespace");
        assertThatThrownBy(() -> DocumentSourceScope.storageDigest(properties, mapper))
            .isInstanceOf(DocumentKnowledgeException.class);
        properties.getConnector().getS3().setEndpoint("https://storage.example.test");
        properties.getConnector().getS3().setBucket(null);
        assertThatThrownBy(() -> DocumentSourceScope.storageDigest(properties, mapper))
            .isInstanceOf(DocumentKnowledgeException.class);
    }

    @Test
    void mountedIdentityPinsTheResolvedRootNotJustTheBindingId() throws Exception {
        var properties = new DocumentKnowledgeProperties();
        properties.getConnector().setType(DocumentKnowledgeProperties.ConnectorType.MOUNTED_FOLDER);
        properties.getConnector().getMountedFolder().setRoot(root.toString());
        String original = DocumentSourceScope.storageDigest(properties, mapper);
        properties.getConnector().getMountedFolder().setRoot(root.resolve(".").toString());
        assertThat(DocumentSourceScope.storageDigest(properties, mapper)).isEqualTo(original);
        properties.getConnector().getMountedFolder().setRoot(Files.createDirectory(root.resolve("other")).toString());
        assertThat(DocumentSourceScope.storageDigest(properties, mapper)).isNotEqualTo(original);
    }

    private DocumentKnowledgeProperties s3() {
        var properties = new DocumentKnowledgeProperties();
        properties.getConnector().setType(DocumentKnowledgeProperties.ConnectorType.S3_COMPATIBLE_OBJECT_STORAGE);
        var options = properties.getConnector().getS3();
        options.setEndpoint("https://storage.example.test");
        options.setBucket("customer-bucket");
        options.setPrefix("approved/");
        return properties;
    }
}
