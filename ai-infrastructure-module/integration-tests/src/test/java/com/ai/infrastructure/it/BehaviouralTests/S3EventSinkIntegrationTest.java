package com.ai.infrastructure.it.BehaviouralTests;

import com.ai.behavior.ingestion.BehaviorIngestionService;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.infrastructure.it.TestApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import io.findify.s3mock.S3Mock;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = {TestApplication.class, S3EventSinkIntegrationTest.S3TestConfig.class},
    properties = {
        "ai.behavior.sink.type=s3",
        "ai.behavior.sink.s3.bucket=" + S3EventSinkIntegrationTest.BUCKET,
        "ai.behavior.sink.s3.prefix=" + S3EventSinkIntegrationTest.PREFIX,
        "ai.behavior.sink.s3.compress=true",
        "ai.behavior.sink.s3.storage-class=STANDARD"
    }
)
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class S3EventSinkIntegrationTest {

    static final String BUCKET = "behavior-it-bucket";
    static final String PREFIX = "ai-behavior-tests";

    private static S3Mock s3Mock;
    private static int s3Port;

    @Autowired
    private BehaviorIngestionService ingestionService;

    @Autowired
    private S3Client s3Client;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    static void startS3() {
        s3Port = findAvailablePort();
        s3Mock = new S3Mock.Builder()
            .withPort(s3Port)
            .withInMemoryBackend()
            .build();
        s3Mock.start();
    }

    @AfterAll
    static void stopS3() {
        if (s3Mock != null) {
            s3Mock.stop();
        }
    }

    @BeforeEach
    void ensureBucketReady() {
        ListBucketsResponse buckets = s3Client.listBuckets();
        boolean exists = buckets.buckets().stream().map(Bucket::name).anyMatch(BUCKET::equals);
        if (!exists) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        } else {
            purgeBucket();
        }
    }

    @Test
    void s3SinkArchivesCompressedObjects() throws IOException {
        BehaviorSignal event = ingestionService.ingest(BehaviorSignal.builder()
            .userId(UUID.randomUUID())
            .sessionId("s3-session")
            .schemaId("intent.search")
            .entityType("catalog")
            .entityId("catalog-" + UUID.randomUUID())
            .timestamp(LocalDateTime.now())
            .attributes(new HashMap<>(Map.of("channel", "mobile", "query", "ai sneakers")))
            .build());

        ListObjectsV2Response objects = s3Client.listObjectsV2(ListObjectsV2Request.builder()
            .bucket(BUCKET)
            .prefix(PREFIX + "/")
            .build());

        assertThat(objects.contents()).hasSize(1);
        S3Object object = objects.contents().get(0);
        assertThat(object.key()).startsWith(PREFIX + "/");
        assertThat(object.key()).endsWith(".json.gz");
        assertThat(object.key()).contains(event.getId().toString());

        byte[] decompressed;
        try (ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(
            GetObjectRequest.builder().bucket(BUCKET).key(object.key()).build())) {
            decompressed = gunzip(stream.readAllBytes());
        }
        BehaviorSignal stored = objectMapper.readValue(decompressed, BehaviorSignal.class);

        assertThat(stored.getId()).isEqualTo(event.getId());
        assertThat(stored.getSchemaId()).isEqualTo("intent.search");
        assertThat(stored.getAttributes()).containsEntry("channel", "mobile");
    }

    private void purgeBucket() {
        ListObjectsV2Response objects = s3Client.listObjectsV2(ListObjectsV2Request.builder()
            .bucket(BUCKET)
            .build());
        for (S3Object object : objects.contents()) {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(BUCKET)
                .key(object.key())
                .build());
        }
    }

    private byte[] gunzip(byte[] gzipped) throws IOException {
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(gzipped))) {
            return gis.readAllBytes();
        }
    }

    @Configuration
    static class S3TestConfig {

        @Bean
        @Primary
        S3Client localStackS3Client() {
            return S3Client.builder()
                .endpointOverride(URI.create("http://localhost:" + s3Port))
                .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("test-access", "test-secret")))
                .region(Region.US_EAST_1)
                .forcePathStyle(true)
                .build();
        }
    }

    private static int findAvailablePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to allocate S3 mock port", ex);
        }
    }
}
