package com.ai.fabric.platform.backend.documentstorage.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.arns.Arn;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.CreateAccessKeyRequest;
import software.amazon.awssdk.services.iam.model.CreateUserRequest;
import software.amazon.awssdk.services.iam.model.EntityAlreadyExistsException;
import software.amazon.awssdk.services.iam.model.GetUserPolicyRequest;
import software.amazon.awssdk.services.iam.model.GetUserRequest;
import software.amazon.awssdk.services.iam.model.ListAccessKeysRequest;
import software.amazon.awssdk.services.iam.model.PutUserPolicyRequest;
import software.amazon.awssdk.services.iam.model.User;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.ai.fabric.platform.backend.documentstorage.provider.DocumentStorageProviderException.Operation.CREATE_READER;
import static com.ai.fabric.platform.backend.documentstorage.provider.DocumentStorageProviderException.Operation.PREPARE_BUCKET;
import static com.ai.fabric.platform.backend.documentstorage.provider.DocumentStorageProviderException.Operation.PUT_OBJECT;

@Component
public class AwsCustomerDocumentStorageProvider implements CustomerDocumentStorageProvider {
    static final String MANAGED_BY = "loomai-document-storage";
    static final String READER_PATH = "/loomai-document-storage/";
    static final String READER_POLICY = "loomai-document-readonly";
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HostedDocumentStorageProperties properties;
    private final boolean ownsClients;
    private volatile Clients clients;

    @Autowired
    public AwsCustomerDocumentStorageProvider(HostedDocumentStorageProperties properties) {
        this.properties = properties;
        this.ownsClients = true;
    }

    public AwsCustomerDocumentStorageProvider(HostedDocumentStorageProperties properties, S3Client s3, IamClient iam) {
        this.properties = properties;
        this.clients = new Clients(s3, iam);
        this.ownsClients = false;
    }

    @Override public boolean enabled() { return properties.isEnabled(); }
    @Override public String region() { return properties.getRegion(); }
    @Override public String accountId() { return properties.getAccountId(); }
    @Override public String endpoint() { validateConfiguration(); return "https://s3." + region() + ".amazonaws.com"; }
    @Override public String bucketPrefix() { return properties.getBucketNamespace() + "-" + accountId(); }

    @Override
    public Bucket prepareBucket(String connectionId, String customerId, String bucketName) {
        return safely(PREPARE_BUCKET, () -> {
            validateId(connectionId);
            validateId(customerId);
            validateBucket(bucketName);
            S3Client s3 = clients().s3();
            boolean created = false;
            try {
                s3.headBucket(HeadBucketRequest.builder().bucket(bucketName).expectedBucketOwner(accountId()).build());
            } catch (S3Exception ex) {
                if (ex.statusCode() != 404) { throw ex; }
                // us-east-1 can return 200 and reset ACLs for an existing bucket. Without atomic
                // create-only semantics, a successful response cannot establish new ownership.
                if ("us-east-1".equals(region())) {
                    throw failure("HOSTED_BUCKET_FRESH_CREATION_REQUIRES_NON_LEGACY_REGION");
                }
                var request = CreateBucketRequest.builder().bucket(bucketName)
                    .objectOwnership(ObjectOwnership.BUCKET_OWNER_ENFORCED)
                    .createBucketConfiguration(CreateBucketConfiguration.builder().locationConstraint(region()).build());
                try {
                    s3.createBucket(request.build());
                    created = true;
                } catch (BucketAlreadyOwnedByYouException ex2) {
                    // A concurrent creator is reusable only after exact ownership tags are readable.
                }
            }
            if (created) {
                s3.putBucketTagging(PutBucketTaggingRequest.builder().bucket(bucketName).expectedBucketOwner(accountId())
                    .tagging(Tagging.builder().tagSet(
                        tag("managed-by", MANAGED_BY), tag("customer-id", customerId), tag("connection-id", connectionId),
                        tag("control-plane-id", properties.getControlPlaneId()))
                        .build()).build());
            }
            Map<String, String> tags = ownedBucket(bucketName);
            if (!customerId.equals(tags.get("customer-id")) || !connectionId.equals(tags.get("connection-id"))) {
                throw failure("HOSTED_BUCKET_OWNERSHIP_MISMATCH");
            }
            s3.putPublicAccessBlock(PutPublicAccessBlockRequest.builder().bucket(bucketName).expectedBucketOwner(accountId())
                .publicAccessBlockConfiguration(PublicAccessBlockConfiguration.builder()
                    .blockPublicAcls(true).ignorePublicAcls(true).blockPublicPolicy(true).restrictPublicBuckets(true).build()).build());
            s3.putBucketOwnershipControls(PutBucketOwnershipControlsRequest.builder().bucket(bucketName)
                .expectedBucketOwner(accountId()).ownershipControls(OwnershipControls.builder()
                    .rules(OwnershipControlsRule.builder().objectOwnership(ObjectOwnership.BUCKET_OWNER_ENFORCED).build()).build())
                .build());
            s3.putBucketEncryption(PutBucketEncryptionRequest.builder().bucket(bucketName).expectedBucketOwner(accountId())
                .serverSideEncryptionConfiguration(ServerSideEncryptionConfiguration.builder()
                    .rules(ServerSideEncryptionRule.builder().applyServerSideEncryptionByDefault(
                        ServerSideEncryptionByDefault.builder().sseAlgorithm(ServerSideEncryption.AES256).build()).build()).build())
                .build());
            s3.putBucketPolicy(PutBucketPolicyRequest.builder().bucket(bucketName).expectedBucketOwner(accountId())
                .policy(bucketPolicy(bucketName)).build());
            s3.putBucketVersioning(PutBucketVersioningRequest.builder().bucket(bucketName).expectedBucketOwner(accountId())
                .versioningConfiguration(VersioningConfiguration.builder().status(BucketVersioningStatus.ENABLED).build()).build());
            verifyBucketSecurity(bucketName);
            return new Bucket(bucketName, true);
        });
    }

    @Override
    public synchronized Reader createReader(String readerName, String bucketName, String prefix) {
        return safely(CREATE_READER, () -> {
            validateBucket(bucketName);
            validatePath(prefix, true);
            if (readerName == null || !readerName.matches("loomai-doc-reader-[a-z0-9][a-z0-9-]{0,45}")) {
                throw failure("HOSTED_READER_NAME_INVALID");
            }
            String boundaryArn = readerPermissionsBoundaryArn();
            Map<String, String> bucketTags = ownedBucket(bucketName);
            String policy = readerPolicy(bucketName, prefix);
            Map<String, String> expectedTags = Map.of("managed-by", MANAGED_BY, "bucket", bucketName,
                "prefix-sha256", hexDigest(prefix.getBytes(StandardCharsets.UTF_8)),
                "customer-id", bucketTags.get("customer-id"), "connection-id", bucketTags.get("connection-id"),
                "control-plane-id", bucketTags.get("control-plane-id"));
            IamClient iam = clients().iam();
            boolean created = false;
            try {
                iam.createUser(CreateUserRequest.builder().userName(readerName).path(READER_PATH).permissionsBoundary(boundaryArn)
                    .tags(expectedTags.entrySet().stream().map(entry -> software.amazon.awssdk.services.iam.model.Tag
                        .builder().key(entry.getKey()).value(entry.getValue()).build()).toList()).build());
                created = true;
            } catch (EntityAlreadyExistsException ex) {
                // Atomic creation avoids a denied GetUser probe for a not-yet-created, path-scoped user.
            }
            String arn = "arn:aws:iam::" + accountId() + ":user" + READER_PATH + readerName;
            // GetUser includes ownership tags; do not infer ownership from the username alone.
            User readback = iam.getUser(GetUserRequest.builder().userName(readerName).build()).user();
            if (readback == null || !arn.equals(readback.arn()) || !readerName.equals(readback.userName())
                || !READER_PATH.equals(readback.path()) || !expectedTags.equals(readback.tags().stream()
                .collect(Collectors.toMap(software.amazon.awssdk.services.iam.model.Tag::key,
                    software.amazon.awssdk.services.iam.model.Tag::value)))) {
                throw failure("HOSTED_READER_OWNERSHIP_MISMATCH");
            }
            // The externally approved boundary caps IAM policy/key issuance; never attach or repair one on an existing user.
            if (readback.permissionsBoundary() == null
                || !boundaryArn.equals(readback.permissionsBoundary().permissionsBoundaryArn())) {
                throw failure("HOSTED_READER_PERMISSIONS_BOUNDARY_MISMATCH");
            }
            var keys = iam.listAccessKeys(ListAccessKeysRequest.builder().userName(readerName).maxItems(2).build());
            if (!keys.accessKeyMetadata().isEmpty() || Boolean.TRUE.equals(keys.isTruncated())) {
                throw failure("HOSTED_READER_SECRET_ALREADY_ISSUED_USE_STORED_SECRET");
            }
            // Only the atomic CreateUser winner may issue a key. Retrying an uncertain issuance never creates another.
            if (!created) { throw failure("HOSTED_READER_CREATION_INCOMPLETE_RECONCILIATION_REQUIRED"); }
            iam.putUserPolicy(PutUserPolicyRequest.builder().userName(readerName).policyName(READER_POLICY)
                .policyDocument(policy).build());
            String policyReadback = iam.getUserPolicy(GetUserPolicyRequest.builder().userName(readerName)
                .policyName(READER_POLICY).build()).policyDocument();
            if (!samePolicy(policy, decodePolicy(policyReadback))) { throw failure("HOSTED_READER_POLICY_READBACK_FAILED"); }
            var key = iam.createAccessKey(CreateAccessKeyRequest.builder().userName(readerName).build()).accessKey();
            if (key == null || !readerName.equals(key.userName()) || !hasText(key.accessKeyId()) || !hasText(key.secretAccessKey())) {
                throw failure("HOSTED_READER_KEY_ISSUANCE_UNCERTAIN_RECONCILIATION_REQUIRED");
            }
            return new Reader(arn, key.accessKeyId(), key.secretAccessKey());
        });
    }

    @Override
    public StoredObject putObject(String bucketName, String key, byte[] bytes, String contentType, String sha256Hex) {
        return safely(PUT_OBJECT, () -> {
            validateBucket(bucketName);
            validatePath(key, false);
            if (bytes == null || bytes.length == 0 || bytes.length > properties.getMaxObjectBytes()) {
                throw failure("HOSTED_OBJECT_SIZE_INVALID");
            }
            if (contentType == null || !contentType.matches("[a-zA-Z0-9!#$&^_.+-]+/[a-zA-Z0-9!#$&^_.+-]+(?:; ?charset=[a-zA-Z0-9-]+)?")
                || contentType.length() > 128) { throw failure("HOSTED_OBJECT_CONTENT_TYPE_INVALID"); }
            byte[] snapshot = bytes.clone();
            String checksum = hexDigest(snapshot);
            if (sha256Hex == null || !sha256Hex.matches("[a-fA-F0-9]{64}") || !checksum.equalsIgnoreCase(sha256Hex)) {
                throw failure("HOSTED_OBJECT_CHECKSUM_INVALID");
            }
            ownedBucket(bucketName);
            if (clients().s3().getBucketVersioning(GetBucketVersioningRequest.builder().bucket(bucketName)
                .expectedBucketOwner(accountId()).build()).status() != BucketVersioningStatus.ENABLED) {
                throw failure("HOSTED_BUCKET_VERSIONING_NOT_ENABLED");
            }
            String base64 = Base64.getEncoder().encodeToString(HexFormat.of().parseHex(checksum));
            PutObjectResponse put;
            try {
                put = clients().s3().putObject(PutObjectRequest.builder().bucket(bucketName).key(key)
                    .expectedBucketOwner(accountId()).ifNoneMatch("*").contentLength((long) snapshot.length)
                    .contentType(contentType).serverSideEncryption(ServerSideEncryption.AES256)
                    .checksumSHA256(base64).metadata(Map.of("sha256", checksum)).build(), RequestBody.fromBytes(snapshot));
            } catch (S3Exception ex) {
                if (ex.statusCode() != 412 && ex.statusCode() != 409) { throw ex; }
                return verifyObject(bucketName, key, null, base64, checksum, snapshot.length);
            }
            if (!hasText(put.versionId()) || "null".equals(put.versionId())) {
                throw failure("HOSTED_OBJECT_VERSION_READBACK_FAILED");
            }
            return verifyObject(bucketName, key, put.versionId(), base64, checksum, snapshot.length);
        });
    }

    private StoredObject verifyObject(String bucket, String key, String version, String base64, String hex, int size) {
        var head = clients().s3().headObject(HeadObjectRequest.builder().bucket(bucket).key(key).versionId(version)
            .expectedBucketOwner(accountId()).checksumMode(ChecksumMode.ENABLED).build());
        if (!base64.equals(head.checksumSHA256()) || !hex.equals(head.metadata().get("sha256"))
            || head.contentLength() == null || head.contentLength() != size || Boolean.TRUE.equals(head.deleteMarker())
            || head.serverSideEncryption() != ServerSideEncryption.AES256 || !hasText(head.eTag())
            || !hasText(head.versionId()) || "null".equals(head.versionId())
            || (version != null && !version.equals(head.versionId()))) {
            throw failure("HOSTED_OBJECT_CONFLICT_OR_READBACK_FAILED");
        }
        return new StoredObject(head.eTag(), head.versionId(), head.contentLength());
    }

    private Map<String, String> ownedBucket(String bucket) {
        S3Client s3 = clients().s3();
        s3.headBucket(HeadBucketRequest.builder().bucket(bucket).expectedBucketOwner(accountId()).build());
        String location = s3.getBucketLocation(GetBucketLocationRequest.builder().bucket(bucket)
            .expectedBucketOwner(accountId()).build()).locationConstraintAsString();
        location = !hasText(location) ? "us-east-1" : "EU".equals(location) ? "eu-west-1" : location;
        if (!region().equals(location)) { throw failure("HOSTED_BUCKET_REGION_MISMATCH"); }
        Map<String, String> tags = s3.getBucketTagging(GetBucketTaggingRequest.builder().bucket(bucket)
            .expectedBucketOwner(accountId()).build()).tagSet().stream().collect(Collectors.toMap(Tag::key, Tag::value));
        // Same-account resources still belong to distinct developer/production control planes.
        if (!MANAGED_BY.equals(tags.get("managed-by")) || !properties.getControlPlaneId().equals(tags.get("control-plane-id"))) {
            throw failure("HOSTED_BUCKET_OWNERSHIP_MISMATCH");
        }
        validateId(tags.get("customer-id"));
        validateId(tags.get("connection-id"));
        return tags;
    }

    private void verifyBucketSecurity(String bucket) {
        S3Client s3 = clients().s3();
        var block = s3.getPublicAccessBlock(GetPublicAccessBlockRequest.builder().bucket(bucket)
            .expectedBucketOwner(accountId()).build()).publicAccessBlockConfiguration();
        var ownership = s3.getBucketOwnershipControls(GetBucketOwnershipControlsRequest.builder().bucket(bucket)
            .expectedBucketOwner(accountId()).build()).ownershipControls();
        var encryption = s3.getBucketEncryption(GetBucketEncryptionRequest.builder().bucket(bucket)
            .expectedBucketOwner(accountId()).build()).serverSideEncryptionConfiguration();
        var versioning = s3.getBucketVersioning(GetBucketVersioningRequest.builder().bucket(bucket)
            .expectedBucketOwner(accountId()).build());
        String policy = s3.getBucketPolicy(GetBucketPolicyRequest.builder().bucket(bucket)
            .expectedBucketOwner(accountId()).build()).policy();
        if (block == null || !Boolean.TRUE.equals(block.blockPublicAcls()) || !Boolean.TRUE.equals(block.ignorePublicAcls())
            || !Boolean.TRUE.equals(block.blockPublicPolicy()) || !Boolean.TRUE.equals(block.restrictPublicBuckets())
            || ownership == null || ownership.rules().size() != 1
            || ownership.rules().getFirst().objectOwnership() != ObjectOwnership.BUCKET_OWNER_ENFORCED
            || encryption == null || encryption.rules().size() != 1
            || encryption.rules().getFirst().applyServerSideEncryptionByDefault() == null
            || encryption.rules().getFirst().applyServerSideEncryptionByDefault().sseAlgorithm() != ServerSideEncryption.AES256
            || versioning.status() != BucketVersioningStatus.ENABLED || !samePolicy(bucketPolicy(bucket), policy)) {
            throw failure("HOSTED_BUCKET_SECURITY_READBACK_FAILED");
        }
    }

    private String bucketPolicy(String bucket) {
        return json(Map.of("Version", "2012-10-17", "Statement", List.of(Map.of(
            "Sid", "DenyInsecureTransport", "Effect", "Deny", "Principal", "*", "Action", "s3:*",
            "Resource", List.of("arn:aws:s3:::" + bucket, "arn:aws:s3:::" + bucket + "/*"),
            "Condition", Map.of("Bool", Map.of("aws:SecureTransport", "false"))))));
    }

    private String readerPolicy(String bucket, String prefix) {
        String arn = "arn:aws:s3:::" + bucket;
        Map<String, String> owner = Map.of("s3:ResourceAccount", accountId());
        return json(Map.of("Version", "2012-10-17", "Statement", List.of(
            Map.of("Effect", "Allow", "Action", List.of("s3:ListBucket"), "Resource", arn,
                "Condition", Map.of("StringLike", Map.of("s3:prefix", prefix + "*"), "StringEquals", owner)),
            Map.of("Effect", "Allow", "Action", List.of("s3:GetBucketVersioning"), "Resource", arn,
                "Condition", Map.of("StringEquals", owner)),
            Map.of("Effect", "Allow", "Action", List.of("s3:GetObject", "s3:GetObjectVersion"),
                "Resource", arn + "/" + prefix + "*", "Condition", Map.of("StringEquals", owner)))));
    }

    private <T> T safely(DocumentStorageProviderException.Operation context, Supplier<T> operation) {
        try {
            if (!enabled()) { throw failure("HOSTED_DOCUMENT_STORAGE_DISABLED"); }
            validateConfiguration();
            return operation.get();
        } catch (DocumentStorageProviderException ex) {
            throw ex.withOperation(context);
        } catch (SdkException ex) {
            // Never propagate SDK messages, HTTP headers, response bodies, or their cause chain.
            var code = DocumentStorageProviderException.AwsErrorCode.SDK_CLIENT_ERROR;
            if (ex instanceof AwsServiceException service) {
                String awsCode = service.awsErrorDetails() == null ? null : service.awsErrorDetails().errorCode();
                code = DocumentStorageProviderException.AwsErrorCode.fromAws(awsCode, service.statusCode());
            }
            throw DocumentStorageProviderException.awsFailure(context, code);
        } catch (RuntimeException ex) {
            throw failure("HOSTED_DOCUMENT_STORAGE_OPERATION_FAILED").withOperation(context);
        }
    }

    private void validateConfiguration() {
        if (accountId() == null || !accountId().matches("[0-9]{12}") || region() == null
            || !region().matches("(af|ap|ca|eu|il|me|mx|sa|us)-(central|east|west|north|south|northeast|northwest|southeast|southwest)-[0-9]")
            || properties.getBucketNamespace() == null || !properties.getBucketNamespace().matches("[a-z0-9][a-z0-9-]{1,29}")
            || properties.getControlPlaneId() == null || !properties.getControlPlaneId().matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")
            || properties.getMaxObjectBytes() < 1 || properties.getMaxObjectBytes() > 1_048_576) {
            throw failure("HOSTED_DOCUMENT_STORAGE_CONFIGURATION_INVALID");
        }
    }

    private void validateBucket(String bucket) {
        // Namespace + expected AWS owner constrain side effects to this backend's hosted resources.
        if (bucket == null || bucket.length() > 63 || !bucket.startsWith(bucketPrefix() + "-")
            || !bucket.matches("[a-z0-9][a-z0-9-]+[a-z0-9]") || bucket.length() <= bucketPrefix().length() + 1) {
            throw failure("HOSTED_BUCKET_NAME_INVALID");
        }
    }

    private String readerPermissionsBoundaryArn() {
        String value = properties.getReaderPermissionsBoundaryArn();
        if (!hasText(value)) { throw failure("HOSTED_READER_PERMISSIONS_BOUNDARY_NOT_CONFIGURED"); }
        try {
            Arn arn = Arn.fromString(value);
            if (value.length() > 2048 || !"aws".equals(arn.partition()) || !"iam".equals(arn.service())
                || !accountId().equals(arn.accountId().orElse(null)) || arn.region().filter(region -> !region.isEmpty()).isPresent()
                || !arn.resourceAsString().matches("policy/(?:[A-Za-z0-9_+=,.@-]+/)*[A-Za-z0-9_+=,.@-]+")) {
                throw failure("HOSTED_READER_PERMISSIONS_BOUNDARY_INVALID");
            }
        } catch (IllegalArgumentException ex) {
            throw failure("HOSTED_READER_PERMISSIONS_BOUNDARY_INVALID");
        }
        return value;
    }

    private static void validateId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")) {
            throw failure("HOSTED_RESOURCE_OWNERSHIP_TAG_INVALID");
        }
    }

    private static void validatePath(String path, boolean prefix) {
        // S3 keys are literal UTF-8, not IAM patterns. Bound bytes and reject ambiguous transport/path segments.
        if (path == null || path.isEmpty() || path.getBytes(StandardCharsets.UTF_8).length > 1024 || path.startsWith("/")
            || path.contains("//") || path.contains("\\") || path.endsWith("/") != prefix
            || path.codePoints().anyMatch(codePoint -> Character.isISOControl(codePoint)
                || (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE))) {
            throw failure("HOSTED_OBJECT_PATH_INVALID");
        }
        // Prefixes become IAM resource/condition patterns; never allow wildcard or variable expansion there.
        if (prefix && (!path.matches("[A-Za-z0-9_./ -]+") || path.contains("*") || path.contains("?") || path.contains("$"))) {
            throw failure("HOSTED_OBJECT_PATH_INVALID");
        }
        for (String segment : path.split("/")) {
            if (segment.equals(".") || segment.equals("..") || segment.isBlank()) {
                throw failure("HOSTED_OBJECT_PATH_INVALID");
            }
        }
    }

    private synchronized Clients clients() {
        if (clients != null) { return clients; }
        if (!hasText(properties.getAccessKey()) || !hasText(properties.getSecretKey())) {
            throw failure("HOSTED_DOCUMENT_STORAGE_CREDENTIALS_NOT_CONFIGURED");
        }
        AwsCredentials credentials = hasText(properties.getSessionToken())
            ? AwsSessionCredentials.create(properties.getAccessKey(), properties.getSecretKey(), properties.getSessionToken())
            : AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey());
        var auth = StaticCredentialsProvider.create(credentials);
        var http = UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(3)).socketTimeout(Duration.ofSeconds(10));
        // IAM key creation is not idempotent. Do not automatically retry an ambiguous issuance.
        var timeouts = ClientOverrideConfiguration.builder().apiCallTimeout(Duration.ofSeconds(30))
            .apiCallAttemptTimeout(Duration.ofSeconds(15)).retryStrategy(AwsRetryStrategy.doNotRetry()).build();
        S3Client s3 = S3Client.builder().region(Region.of(region())).credentialsProvider(auth)
            .httpClientBuilder(http).overrideConfiguration(timeouts).build();
        try {
            clients = new Clients(s3, IamClient.builder().region(Region.AWS_GLOBAL).credentialsProvider(auth)
                .httpClientBuilder(http).overrideConfiguration(timeouts).build());
        } catch (RuntimeException ex) {
            s3.close();
            throw ex;
        }
        return clients;
    }

    @PreDestroy
    public synchronized void close() {
        if (ownsClients && clients != null) {
            clients.s3().close();
            clients.iam().close();
            clients = null;
        }
    }

    private static String json(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw failure("HOSTED_POLICY_INVALID"); }
    }

    private static boolean samePolicy(String expected, String actual) {
        if (actual == null) { return false; }
        try { return JSON.readTree(expected).equals(JSON.readTree(actual)); }
        catch (JsonProcessingException ex) { return false; }
    }

    private static String decodePolicy(String policy) {
        return policy == null || policy.stripLeading().startsWith("{") ? policy
            : URLDecoder.decode(policy, StandardCharsets.UTF_8);
    }

    private static String hexDigest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw failure("HOSTED_CHECKSUM_UNAVAILABLE"); }
    }

    private static Tag tag(String key, String value) { return Tag.builder().key(key).value(value).build(); }
    private static boolean hasText(String value) { return value != null && !value.isBlank(); }
    private static DocumentStorageProviderException failure(String code) { return new DocumentStorageProviderException(code); }
    private record Clients(S3Client s3, IamClient iam) { }
}
