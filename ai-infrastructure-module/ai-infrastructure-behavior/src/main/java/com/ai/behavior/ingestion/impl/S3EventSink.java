package com.ai.behavior.ingestion.impl;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.exception.BehaviorStorageException;
import com.ai.behavior.ingestion.BehaviorEventSink;
import com.ai.behavior.model.BehaviorEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.GZIPOutputStream;

@Component
@RequiredArgsConstructor
@ConditionalOnClass(S3Client.class)
@ConditionalOnProperty(prefix = "ai.behavior.sink", name = "type", havingValue = "s3")
public class S3EventSink implements BehaviorEventSink {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final S3Client s3Client;
    private final ObjectMapper objectMapper;
    private final BehaviorModuleProperties properties;

    @Override
    @Transactional
    public void accept(BehaviorEvent event) throws BehaviorStorageException {
        upload(event);
    }

    @Override
    @Transactional
    public void acceptBatch(List<BehaviorEvent> events) throws BehaviorStorageException {
        for (BehaviorEvent event : events) {
            upload(event);
        }
    }

    @Override
    public String getSinkType() {
        return "s3";
    }

    private void upload(BehaviorEvent event) {
        try {
            byte[] payload = serialize(event);
            PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.getSink().getS3().getBucket())
                .key(objectKey(event))
                .storageClass(properties.getSink().getS3().getStorageClass())
                .contentType("application/json")
                .build();
            s3Client.putObject(request, RequestBody.fromBytes(payload));
        } catch (IOException ex) {
            throw new BehaviorStorageException("Failed to serialize behavior event for S3", ex);
        } catch (Exception ex) {
            throw new BehaviorStorageException("Failed to upload behavior event to S3", ex);
        }
    }

    private String objectKey(BehaviorEvent event) {
        String dateFolder = event.getTimestamp() != null
            ? DATE_FORMATTER.format(event.getTimestamp())
            : "undated";
        return properties.getSink().getS3().getPrefix() + "/" + dateFolder + "/" + event.getId() + ".json" +
            (properties.getSink().getS3().isCompress() ? ".gz" : "");
    }

    private byte[] serialize(BehaviorEvent event) throws IOException {
        byte[] json = objectMapper.writeValueAsBytes(event);
        if (!properties.getSink().getS3().isCompress()) {
            return json;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(json);
            gzip.finish();
            return baos.toByteArray();
        }
    }
}
