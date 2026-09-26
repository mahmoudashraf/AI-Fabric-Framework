package com.ai.fabric.runtime.documents;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "loomai.documents", name = "enabled", havingValue = "true")
public class DocumentConnectorConfiguration {

    @Bean
    DocumentTemporaryFileManager documentTemporaryFileManager(
        DocumentKnowledgeProperties properties,
        Clock clock
    ) {
        return new DocumentTemporaryFileManager(
            Path.of(properties.getTempRoot()),
            properties.getTempRetention(),
            clock
        );
    }

    private S3Client documentS3Client(DocumentKnowledgeProperties.S3 options) {
        var builder = S3Client.builder()
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .region(Region.of(required(options.getRegion(), "S3 region")))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(options.isPathStyleAccess())
                .build());
        if (StringUtils.hasText(options.getEndpoint())) {
            URI endpoint = DocumentEndpointPolicy.validate(
                options.getEndpoint(),
                options.isAllowInsecureEndpoint(),
                required(options.getAllowedEndpointHost(), "S3 allowed endpoint host"),
                options.isAllowPrivateEndpoint()
            );
            builder.endpointOverride(endpoint);
        }
        if (StringUtils.hasText(options.getAccessKey()) || StringUtils.hasText(options.getSecretKey())) {
            String accessKey = required(options.getAccessKey(), "S3 access key");
            String secretKey = required(options.getSecretKey(), "S3 secret key");
            if (StringUtils.hasText(options.getSessionToken())) {
                builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsSessionCredentials.create(accessKey, secretKey, options.getSessionToken().trim())
                ));
            } else {
                builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)
                ));
            }
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    DocumentSourceConnector documentSourceConnector(
        DocumentKnowledgeProperties properties,
        DocumentTemporaryFileManager temporaryFiles
    ) {
        DocumentKnowledgeProperties.Connector connector = properties.getConnector();
        if (connector.getType() == null) {
            throw new IllegalStateException("loomai.documents.connector.type is required when document knowledge is enabled");
        }
        return switch (connector.getType()) {
            case MOUNTED_FOLDER -> new MountedFolderDocumentSourceConnector(
                Path.of(required(connector.getMountedFolder().getRoot(), "Mounted document root")),
                connector.getBindingRef(),
                properties.getPolicy(),
                temporaryFiles
            );
            case S3_COMPATIBLE_OBJECT_STORAGE -> new S3DocumentSourceConnector(
                documentS3Client(connector.getS3()),
                connector.getS3().getBucket(),
                connector.getS3().getPrefix(),
                connector.getBindingRef(),
                connector.getS3().isObjectVersioningAvailable(),
                properties.getPolicy(),
                temporaryFiles
            );
        };
    }

    private String required(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(name + " is required");
        }
        return value.trim();
    }
}
