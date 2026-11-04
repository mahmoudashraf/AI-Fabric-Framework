package com.ai.fabric.platform.backend.documentstorage.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.AccessKey;
import software.amazon.awssdk.services.iam.model.AccessKeyMetadata;
import software.amazon.awssdk.services.iam.model.AttachedPermissionsBoundary;
import software.amazon.awssdk.services.iam.model.CreateAccessKeyRequest;
import software.amazon.awssdk.services.iam.model.CreateAccessKeyResponse;
import software.amazon.awssdk.services.iam.model.CreateUserRequest;
import software.amazon.awssdk.services.iam.model.CreateUserResponse;
import software.amazon.awssdk.services.iam.model.EntityAlreadyExistsException;
import software.amazon.awssdk.services.iam.model.GetUserPolicyRequest;
import software.amazon.awssdk.services.iam.model.GetUserPolicyResponse;
import software.amazon.awssdk.services.iam.model.GetUserRequest;
import software.amazon.awssdk.services.iam.model.GetUserResponse;
import software.amazon.awssdk.services.iam.model.IamException;
import software.amazon.awssdk.services.iam.model.ListAccessKeysRequest;
import software.amazon.awssdk.services.iam.model.ListAccessKeysResponse;
import software.amazon.awssdk.services.iam.model.PutUserPolicyRequest;
import software.amazon.awssdk.services.iam.model.User;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AwsCustomerDocumentStorageProviderTest {
    private static final String ACCOUNT = "316658062476";
    private static final String BUCKET = "loomai-customer-docs-" + ACCOUNT + "-connection1";
    private static final String READER = "loomai-doc-reader-deterministic1";
    private static final String BOUNDARY = "arn:aws:iam::" + ACCOUNT + ":policy/loomai/document-storage-reader-boundary";
    private static final String PREFIX = "knowledge/customer-help/";
    private static final String KEY = PREFIX + "upload-1/policy.txt";
    private static final byte[] BYTES = "customer document".getBytes(StandardCharsets.UTF_8);
    private final HostedDocumentStorageProperties properties = new HostedDocumentStorageProperties();
    private final S3Client s3 = mock(S3Client.class);
    private final IamClient iam = mock(IamClient.class);
    private final AtomicReference<String> bucketPolicy = new AtomicReference<>();
    private final AtomicReference<String> readerPolicy = new AtomicReference<>();
    private final AwsCustomerDocumentStorageProvider provider = new AwsCustomerDocumentStorageProvider(properties, s3, iam);

    @BeforeEach
    void configure() {
        properties.setEnabled(true);
        properties.setReaderPermissionsBoundaryArn(BOUNDARY);
    }

    @Test
    void disabledSpringProviderCreatesNoClientsAndKeepsByoStartupIndependent() {
        new ApplicationContextRunner().withUserConfiguration(HostedDocumentStorageConfiguration.class,
            AwsCustomerDocumentStorageProvider.class).run(context -> {
                assertThat(context).hasNotFailed();
                var bean = context.getBean(AwsCustomerDocumentStorageProvider.class);
                assertThat(bean.enabled()).isFalse();
                assertThat(bean.region()).isEqualTo("eu-north-1");
                assertThat(bean.accountId()).isEqualTo(ACCOUNT);
                assertThat(context.getBean(HostedDocumentStorageProperties.class).getControlPlaneId()).isEqualTo("platform");
                assertThat(bean.endpoint()).isEqualTo("https://s3.eu-north-1.amazonaws.com");
                assertThat(bean.bucketPrefix()).isEqualTo("loomai-customer-docs-" + ACCOUNT);
                assertThat(ReflectionTestUtils.getField(bean, "clients")).isNull();
                assertThatThrownBy(() -> bean.prepareBucket("connection1", "customer1", BUCKET))
                    .hasMessage("HOSTED_DOCUMENT_STORAGE_DISABLED");
                assertThat(ReflectionTestUtils.getField(bean, "clients")).isNull();
            });
    }

    @Test
    void enabledConfigurationIsLazyAndCredentialsAreBackendBoundProperties() {
        new ApplicationContextRunner().withUserConfiguration(HostedDocumentStorageConfiguration.class,
            AwsCustomerDocumentStorageProvider.class).withPropertyValues(
                "platform.document-storage.hosted.enabled=true", "platform.document-storage.hosted.region=eu-west-2",
                "platform.document-storage.hosted.bucket-namespace=customer-documents",
                "platform.document-storage.hosted.control-plane-id=production-control-plane",
                "platform.document-storage.hosted.reader-permissions-boundary-arn=" + BOUNDARY,
                "platform.document-storage.hosted.access-key=test-provider-key",
                "platform.document-storage.hosted.secret-key=test-provider-secret").run(context -> {
                    assertThat(context).hasNotFailed();
                    var bean = context.getBean(AwsCustomerDocumentStorageProvider.class);
                    assertThat(bean.enabled()).isTrue();
                    assertThat(bean.endpoint()).isEqualTo("https://s3.eu-west-2.amazonaws.com");
                    assertThat(bean.bucketPrefix()).isEqualTo("customer-documents-" + ACCOUNT);
                    assertThat(ReflectionTestUtils.getField(bean, "clients")).isNull();
                    assertThat(context.getBean(HostedDocumentStorageProperties.class).getReaderPermissionsBoundaryArn())
                        .isEqualTo(BOUNDARY);
                    assertThat(context.getBean(HostedDocumentStorageProperties.class).getControlPlaneId())
                        .isEqualTo("production-control-plane");
                    assertThat(context.getBean(HostedDocumentStorageProperties.class).toString())
                        .doesNotContain("test-provider-key", "test-provider-secret");
                });
    }

    @Test
    void missingProviderCredentialsFailWithoutDefaultCredentialChainOrClientCreation() {
        var bean = new AwsCustomerDocumentStorageProvider(properties);
        assertThatThrownBy(() -> bean.prepareBucket("connection1", "customer1", BUCKET))
            .hasMessage("HOSTED_DOCUMENT_STORAGE_CREDENTIALS_NOT_CONFIGURED");
        assertThat(ReflectionTestUtils.getField(bean, "clients")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"arbitrary-bucket", "loomai-customer-docs-999999999999-connection1",
        "loomai-customer-docs-316658062476-", "loomai-customer-docs-316658062476-bad.name"})
    void refusesBucketsOutsideServerNamespaceAndAccountBeforeAnyAwsCall(String bucket) {
        assertThatThrownBy(() -> provider.prepareBucket("connection1", "customer1", bucket))
            .hasMessage("HOSTED_BUCKET_NAME_INVALID");
        verifyNoInteractions(s3, iam);
    }

    @Test
    void rejectsEndpointLikeRegionAndInvalidAccountWithoutAwsCalls() {
        properties.setRegion("https://attacker.test");
        assertThatThrownBy(provider::endpoint).hasMessage("HOSTED_DOCUMENT_STORAGE_CONFIGURATION_INVALID");
        properties.setRegion("eu-north-1");
        properties.setAccountId("not-an-account");
        assertThatThrownBy(() -> provider.prepareBucket("connection1", "customer1", BUCKET))
            .hasMessage("HOSTED_DOCUMENT_STORAGE_CONFIGURATION_INVALID");
        verifyNoInteractions(s3, iam);
    }

    @Test
    void preparesNewPrivateEncryptedVersionedBucketWithExactOwnershipTagsAndReadback() throws Exception {
        ownedBucket();
        secureBucket();
        when(s3.headBucket(any(HeadBucketRequest.class))).thenThrow(s3Error(404)).thenReturn(HeadBucketResponse.builder().build());
        assertThat(provider.prepareBucket("connection1", "customer1", BUCKET))
            .isEqualTo(new CustomerDocumentStorageProvider.Bucket(BUCKET, true));

        var create = ArgumentCaptor.forClass(CreateBucketRequest.class);
        verify(s3).createBucket(create.capture());
        assertThat(create.getValue().objectOwnership()).isEqualTo(ObjectOwnership.BUCKET_OWNER_ENFORCED);
        assertThat(create.getValue().createBucketConfiguration().locationConstraintAsString()).isEqualTo("eu-north-1");
        var tagging = ArgumentCaptor.forClass(PutBucketTaggingRequest.class);
        verify(s3).putBucketTagging(tagging.capture());
        assertThat(tagging.getValue().expectedBucketOwner()).isEqualTo(ACCOUNT);
        assertThat(tagging.getValue().tagging().tagSet()).containsExactlyInAnyOrder(
            tag("managed-by", AwsCustomerDocumentStorageProvider.MANAGED_BY),
            tag("customer-id", "customer1"), tag("connection-id", "connection1"), tag("control-plane-id", "platform"));
        var encryption = ArgumentCaptor.forClass(PutBucketEncryptionRequest.class);
        verify(s3).putBucketEncryption(encryption.capture());
        assertThat(encryption.getValue().serverSideEncryptionConfiguration().rules().getFirst()
            .applyServerSideEncryptionByDefault().sseAlgorithm()).isEqualTo(ServerSideEncryption.AES256);
        var policy = new ObjectMapper().readTree(bucketPolicy.get());
        assertThat(policy.at("/Statement/0/Effect").asText()).isEqualTo("Deny");
        assertThat(policy.at("/Statement/0/Condition/Bool/aws:SecureTransport").asText()).isEqualTo("false");
        assertThat(policy.at("/Statement/0/Resource/0").asText()).isEqualTo("arn:aws:s3:::" + BUCKET);
        verifyNoInteractions(iam);
    }

    @Test
    void ownedPrepareReplayDoesNotCreateOrRetagBucket() {
        ownedBucket();
        secureBucket();
        provider.prepareBucket("connection1", "customer1", BUCKET);
        provider.prepareBucket("connection1", "customer1", BUCKET);
        verify(s3, never()).createBucket(any(CreateBucketRequest.class));
        verify(s3, never()).putBucketTagging(any(PutBucketTaggingRequest.class));
        verify(s3, times(2)).getBucketVersioning(any(GetBucketVersioningRequest.class));
    }

    @Test
    void usEast1FreshCreateRefusesLegacySuccessThatCannotProveNewOwnership() {
        properties.setRegion("us-east-1");
        when(s3.headBucket(any(HeadBucketRequest.class))).thenThrow(s3Error(404));
        assertThatThrownBy(() -> provider.prepareBucket("connection1", "customer1", BUCKET))
            .hasMessage("HOSTED_BUCKET_FRESH_CREATION_REQUIRES_NON_LEGACY_REGION");
        verifyNoBucketMutations();
    }

    @Test
    void usEast1ExistingExactlyOwnedBucketRemainsUsable() {
        properties.setRegion("us-east-1");
        ownedBucket();
        secureBucket();
        when(s3.getBucketLocation(any(GetBucketLocationRequest.class))).thenReturn(GetBucketLocationResponse.builder().build());
        assertThat(provider.prepareBucket("connection1", "customer1", BUCKET).versioningEnabled()).isTrue();
        verify(s3, never()).createBucket(any(CreateBucketRequest.class));
        verify(s3, never()).putBucketTagging(any(PutBucketTaggingRequest.class));
    }

    @Test
    void sameAccountBucketWithWrongCustomerIsNeverAdopted() {
        ownedBucket();
        assertThatThrownBy(() -> provider.prepareBucket("connection1", "otherCustomer", BUCKET))
            .hasMessage("HOSTED_BUCKET_OWNERSHIP_MISMATCH");
        verifyNoBucketMutations();
    }

    @Test
    void sameAccountBucketWithWrongConnectionIsNeverAdopted() {
        ownedBucket();
        assertThatThrownBy(() -> provider.prepareBucket("otherConnection", "customer1", BUCKET))
            .hasMessage("HOSTED_BUCKET_OWNERSHIP_MISMATCH");
        verifyNoBucketMutations();
    }

    @Test
    void sameAccountCustomerAndConnectionCannotAdoptAnotherControlPlanesBucket() {
        properties.setControlPlaneId("developer-staging");
        ownedBucket();
        bucketControlPlane("production-control-plane");
        assertThatThrownBy(() -> provider.prepareBucket("connection1", "customer1", BUCKET))
            .hasMessage("HOSTED_BUCKET_OWNERSHIP_MISMATCH");
        verifyNoBucketMutations();
        verifyNoInteractions(iam);
    }

    @Test
    void bucketWithoutControlPlaneTagIsNeverAdopted() {
        ownedBucket();
        when(s3.getBucketTagging(any(GetBucketTaggingRequest.class))).thenReturn(GetBucketTaggingResponse.builder()
            .tagSet(tag("managed-by", AwsCustomerDocumentStorageProvider.MANAGED_BY), tag("customer-id", "customer1"),
                tag("connection-id", "connection1")).build());
        assertThatThrownBy(() -> provider.prepareBucket("connection1", "customer1", BUCKET))
            .hasMessage("HOSTED_BUCKET_OWNERSHIP_MISMATCH");
        verifyNoBucketMutations();
    }

    @Test
    void cannotCreateReaderForAnotherControlPlanesBucket() {
        properties.setControlPlaneId("developer-staging");
        ownedBucket();
        bucketControlPlane("production-control-plane");
        assertThatThrownBy(() -> provider.createReader(READER, BUCKET, PREFIX))
            .hasMessage("HOSTED_BUCKET_OWNERSHIP_MISMATCH");
        verifyNoInteractions(iam);
    }

    @Test
    void cannotUploadToAnotherControlPlanesBucket() {
        properties.setControlPlaneId("production-control-plane");
        ownedBucket();
        bucketControlPlane("developer-staging");
        assertThatThrownBy(() -> provider.putObject(BUCKET, KEY, BYTES, "text/plain", hex(BYTES)))
            .hasMessage("HOSTED_BUCKET_OWNERSHIP_MISMATCH");
        verify(s3, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verifyNoInteractions(iam);
    }

    @Test
    void untaggedBucketIsNotAdoptedEvenWhenCreateRacesWithSameAccountOwner() {
        ownedBucket();
        when(s3.headBucket(any(HeadBucketRequest.class))).thenThrow(s3Error(404)).thenReturn(HeadBucketResponse.builder().build());
        when(s3.createBucket(any(CreateBucketRequest.class))).thenThrow(BucketAlreadyOwnedByYouException.builder().build());
        when(s3.getBucketTagging(any(GetBucketTaggingRequest.class))).thenReturn(GetBucketTaggingResponse.builder().build());
        assertThatThrownBy(() -> provider.prepareBucket("connection1", "customer1", BUCKET))
            .hasMessage("HOSTED_BUCKET_OWNERSHIP_MISMATCH");
        verify(s3, never()).putBucketTagging(any(PutBucketTaggingRequest.class));
        verify(s3, never()).putBucketPolicy(any(PutBucketPolicyRequest.class));
    }

    @Test
    void wrongAccountHead403IsNotTreatedAsMissingBucketAndIsSanitized() {
        when(s3.headBucket(any(HeadBucketRequest.class))).thenThrow(S3Exception.builder().statusCode(403)
            .message("Authorization: provider-secret; X-Amz-Security-Token=private").build());
        assertThatThrownBy(() -> provider.prepareBucket("connection1", "customer1", BUCKET))
            .hasMessage("HOSTED_DOCUMENT_STORAGE_AWS_OPERATION_FAILED [PREPARE_BUCKET:AccessDenied]").hasNoCause()
            .isInstanceOfSatisfying(DocumentStorageProviderException.class, error -> {
                assertThat(error.getCode()).isEqualTo("HOSTED_DOCUMENT_STORAGE_AWS_OPERATION_FAILED");
                assertThat(error.getOperation()).isEqualTo(DocumentStorageProviderException.Operation.PREPARE_BUCKET);
                assertThat(error.getAwsErrorCode()).isEqualTo("AccessDenied");
            });
        verifyNoBucketMutations();
    }

    @ParameterizedTest
    @ValueSource(strings = {"AccessDenied", "AccessDeniedException", "InvalidAccessKeyId", "SignatureDoesNotMatch",
        "ExpiredToken", "Throttling", "SlowDown", "BucketAlreadyExists"})
    void preservesOnlyWhitelistedAwsCodeAndOperationWithoutSdkPayloads(String code) {
        when(s3.headBucket(any(HeadBucketRequest.class))).thenThrow(s3Error(400, code));
        assertThatThrownBy(() -> provider.prepareBucket("connection1", "customer1", BUCKET))
            .hasMessage("HOSTED_DOCUMENT_STORAGE_AWS_OPERATION_FAILED [PREPARE_BUCKET:" + code + "]")
            .hasNoCause().isInstanceOfSatisfying(DocumentStorageProviderException.class, error -> {
                assertThat(error.getAwsErrorCode()).isEqualTo(code);
                assertThat(error.getOperation()).isEqualTo(DocumentStorageProviderException.Operation.PREPARE_BUCKET);
                assertThat(error.getSuppressed()).isEmpty();
                assertThat(error.toString()).doesNotContain("provider-secret", "Authorization", "nested-secret", "raw-detail");
            });
    }

    @Test
    void unrecognizedAwsCodeIsNeverEchoedEvenWhenItContainsCredentials() {
        when(s3.headBucket(any(HeadBucketRequest.class)))
            .thenThrow(s3Error(400, "Authorization=provider-secret\r\nresponse-body"));
        assertThatThrownBy(() -> provider.prepareBucket("connection1", "customer1", BUCKET))
            .hasMessage("HOSTED_DOCUMENT_STORAGE_AWS_OPERATION_FAILED [PREPARE_BUCKET:UnknownAwsError]")
            .hasNoCause().isInstanceOfSatisfying(DocumentStorageProviderException.class, error -> {
                assertThat(error.getAwsErrorCode()).isEqualTo("UnknownAwsError");
                assertThat(error.toString()).doesNotContain("Authorization", "provider-secret", "response-body");
            });
    }

    @Test
    void unrecognizedCodeWith403FallsBackToSafeActionableAccessDenied() {
        when(s3.headBucket(any(HeadBucketRequest.class))).thenThrow(s3Error(403, "unsafe-provider-secret"));
        assertThatThrownBy(() -> provider.prepareBucket("connection1", "customer1", BUCKET))
            .hasMessage("HOSTED_DOCUMENT_STORAGE_AWS_OPERATION_FAILED [PREPARE_BUCKET:AccessDenied]")
            .hasNoCause().isInstanceOfSatisfying(DocumentStorageProviderException.class,
                error -> assertThat(error.getAwsErrorCode()).isEqualTo("AccessDenied"));
    }

    @Test
    void iamPermissionFailureIdentifiesReaderOperationWithoutRequestingPolicyIntrospection() {
        ownedBucket();
        newReader();
        IamException.Builder denied = IamException.builder();
        denied.statusCode(403);
        denied.awsErrorDetails(AwsErrorDetails.builder().errorCode("AccessDenied").errorMessage("raw-provider-secret").build());
        denied.message("Authorization: provider-secret");
        denied.cause(new IllegalStateException("nested-secret"));
        when(iam.createUser(any(CreateUserRequest.class))).thenThrow(denied.build());
        assertThatThrownBy(() -> provider.createReader(READER, BUCKET, PREFIX))
            .hasMessage("HOSTED_DOCUMENT_STORAGE_AWS_OPERATION_FAILED [CREATE_READER:AccessDenied]")
            .hasNoCause().isInstanceOfSatisfying(DocumentStorageProviderException.class, error -> {
                assertThat(error.getOperation()).isEqualTo(DocumentStorageProviderException.Operation.CREATE_READER);
                assertThat(error.getAwsErrorCode()).isEqualTo("AccessDenied");
                assertThat(error.toString()).doesNotContain("Authorization", "provider-secret", "nested-secret");
            });
        verify(iam, never()).listAttachedUserPolicies(any(software.amazon.awssdk.services.iam.model.ListAttachedUserPoliciesRequest.class));
        verify(iam, never()).listUserPolicies(any(software.amazon.awssdk.services.iam.model.ListUserPoliciesRequest.class));
        verify(iam, never()).createAccessKey(any(CreateAccessKeyRequest.class));
    }

    @Test
    void uploadPermissionFailureIdentifiesPutOperationWithoutSdkDetails() {
        uploadReady();
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(s3Error(403, "AccessDenied"));
        assertThatThrownBy(() -> provider.putObject(BUCKET, KEY, BYTES, "text/plain", hex(BYTES)))
            .hasMessage("HOSTED_DOCUMENT_STORAGE_AWS_OPERATION_FAILED [PUT_OBJECT:AccessDenied]")
            .hasNoCause().isInstanceOfSatisfying(DocumentStorageProviderException.class, error -> {
                assertThat(error.getOperation()).isEqualTo(DocumentStorageProviderException.Operation.PUT_OBJECT);
                assertThat(error.getAwsErrorCode()).isEqualTo("AccessDenied");
                assertThat(error.toString()).doesNotContain("Authorization", "provider-secret", "nested-secret");
            });
    }

    @Test
    void incorrectRegionFailsBeforeMutatingAnExistingBucket() {
        ownedBucket();
        when(s3.getBucketLocation(any(GetBucketLocationRequest.class))).thenReturn(GetBucketLocationResponse.builder()
            .locationConstraint("eu-west-2").build());
        assertThatThrownBy(() -> provider.prepareBucket("connection1", "customer1", BUCKET))
            .hasMessage("HOSTED_BUCKET_REGION_MISMATCH");
        verifyNoBucketMutations();
    }

    @Test
    void insecureReadbackIsNotReportedAsPrepared() {
        ownedBucket();
        secureBucket();
        when(s3.getPublicAccessBlock(any(GetPublicAccessBlockRequest.class))).thenReturn(GetPublicAccessBlockResponse.builder()
            .publicAccessBlockConfiguration(PublicAccessBlockConfiguration.builder().blockPublicAcls(false).build()).build());
        assertThatThrownBy(() -> provider.prepareBucket("connection1", "customer1", BUCKET))
            .hasMessage("HOSTED_BUCKET_SECURITY_READBACK_FAILED");
    }

    @Test
    void readerHasOnlyExactBucketAndPrefixReadPolicyWithTaggedOwnership() throws Exception {
        ownedBucket();
        newReader();
        CustomerDocumentStorageProvider.Reader reader = provider.createReader(READER, BUCKET, PREFIX);
        assertThat(reader.principalArn()).isEqualTo(user().arn());
        assertThat(reader.accessKey()).isEqualTo("issued-access");
        assertThat(reader.secretKey()).isEqualTo("issued-secret");
        assertThat(reader.toString()).doesNotContain("issued-access", "issued-secret");
        JsonNode statements = new ObjectMapper().readTree(readerPolicy.get()).get("Statement");
        assertThat(statements).hasSize(3);
        assertThat(statements.get(0).get("Action").toString()).isEqualTo("[\"s3:ListBucket\"]");
        assertThat(statements.get(0).at("/Condition/StringLike/s3:prefix").asText()).isEqualTo(PREFIX + "*");
        assertThat(statements.get(0).get("Resource").asText()).isEqualTo("arn:aws:s3:::" + BUCKET);
        assertThat(statements.get(1).get("Action").toString()).isEqualTo("[\"s3:GetBucketVersioning\"]");
        assertThat(statements.get(1).has("StringLike")).isFalse();
        assertThat(statements.get(2).get("Action").toString()).isEqualTo("[\"s3:GetObject\",\"s3:GetObjectVersion\"]");
        assertThat(statements.get(2).get("Resource").asText()).isEqualTo("arn:aws:s3:::" + BUCKET + "/" + PREFIX + "*");
        for (JsonNode statement : statements) {
            assertThat(statement.at("/Condition/StringEquals/s3:ResourceAccount").asText()).isEqualTo(ACCOUNT);
        }
        var create = ArgumentCaptor.forClass(CreateUserRequest.class);
        verify(iam).createUser(create.capture());
        assertThat(create.getValue().path()).isEqualTo(AwsCustomerDocumentStorageProvider.READER_PATH);
        assertThat(create.getValue().permissionsBoundary()).isEqualTo(BOUNDARY);
        assertThat(create.getValue().tags()).hasSize(6).contains(software.amazon.awssdk.services.iam.model.Tag.builder()
            .key("control-plane-id").value("platform").build());
        var order = inOrder(iam);
        order.verify(iam).createUser(any(CreateUserRequest.class));
        order.verify(iam).getUser(any(GetUserRequest.class));
        order.verify(iam).listAccessKeys(any(ListAccessKeysRequest.class));
        order.verify(iam).putUserPolicy(any(PutUserPolicyRequest.class));
        order.verify(iam).getUserPolicy(any(GetUserPolicyRequest.class));
        order.verify(iam).createAccessKey(any(CreateAccessKeyRequest.class));
        verify(iam, never()).listAttachedUserPolicies(any(software.amazon.awssdk.services.iam.model.ListAttachedUserPoliciesRequest.class));
        verify(iam, never()).listUserPolicies(any(software.amazon.awssdk.services.iam.model.ListUserPoliciesRequest.class));
    }

    @Test
    void readerBoundaryIsNotDefaultedAndMissingConfigurationFailsBeforeAnyAwsCall() {
        assertThat(new HostedDocumentStorageProperties().getReaderPermissionsBoundaryArn()).isNull();
        properties.setReaderPermissionsBoundaryArn(null);
        assertThatThrownBy(() -> provider.createReader(READER, BUCKET, PREFIX))
            .hasMessage("HOSTED_READER_PERMISSIONS_BOUNDARY_NOT_CONFIGURED")
            .isInstanceOfSatisfying(DocumentStorageProviderException.class, error -> {
                assertThat(error.getCode()).isEqualTo("HOSTED_READER_PERMISSIONS_BOUNDARY_NOT_CONFIGURED");
                assertThat(error.getOperation()).isEqualTo(DocumentStorageProviderException.Operation.CREATE_READER);
                assertThat(error.getAwsErrorCode()).isNull();
            });
        verifyNoInteractions(s3, iam);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void blankReaderBoundaryIsNotConfigured(String boundary) {
        properties.setReaderPermissionsBoundaryArn(boundary);
        assertThatThrownBy(() -> provider.createReader(READER, BUCKET, PREFIX))
            .hasMessage("HOSTED_READER_PERMISSIONS_BOUNDARY_NOT_CONFIGURED");
        verifyNoInteractions(s3, iam);
    }

    @ParameterizedTest
    @ValueSource(strings = {"arn:aws:iam::999999999999:policy/reader-boundary", "arn:aws:iam::aws:policy/ReadOnlyAccess",
        "arn:aws:iam::316658062476:role/reader-boundary", "arn:aws:iam::316658062476:policy/*",
        "arn:aws:iam:eu-north-1:316658062476:policy/reader-boundary", "https://attacker.test/boundary"})
    void rejectsReaderBoundariesOutsideExactConfiguredAccountManagedPolicy(String boundary) {
        properties.setReaderPermissionsBoundaryArn(boundary);
        assertThatThrownBy(() -> provider.createReader(READER, BUCKET, PREFIX))
            .hasMessage("HOSTED_READER_PERMISSIONS_BOUNDARY_INVALID").hasNoCause();
        verifyNoInteractions(s3, iam);
    }

    @Test
    void newReaderMissingBoundaryReadbackCannotReceivePolicyOrKey() {
        ownedBucket();
        newReader();
        when(iam.getUser(any(GetUserRequest.class))).thenReturn(GetUserResponse.builder().user(user().toBuilder()
            .permissionsBoundary((AttachedPermissionsBoundary) null).build()).build());
        assertThatThrownBy(() -> provider.createReader(READER, BUCKET, PREFIX))
            .hasMessage("HOSTED_READER_PERMISSIONS_BOUNDARY_MISMATCH");
        verify(iam, never()).listAccessKeys(any(ListAccessKeysRequest.class));
        verify(iam, never()).putUserPolicy(any(PutUserPolicyRequest.class));
        verify(iam, never()).createAccessKey(any(CreateAccessKeyRequest.class));
    }

    @Test
    void existingReaderWithDifferentBoundaryIsNeverRepairedOrIssuedCredentials() {
        ownedBucket();
        existingReader();
        when(iam.getUser(any(GetUserRequest.class))).thenReturn(GetUserResponse.builder().user(user().toBuilder()
            .permissionsBoundary(AttachedPermissionsBoundary.builder()
                .permissionsBoundaryArn("arn:aws:iam::" + ACCOUNT + ":policy/other-boundary").build()).build()).build());
        assertThatThrownBy(() -> provider.createReader(READER, BUCKET, PREFIX))
            .hasMessage("HOSTED_READER_PERMISSIONS_BOUNDARY_MISMATCH");
        verify(iam, never()).listAccessKeys(any(ListAccessKeysRequest.class));
        verify(iam, never()).putUserPolicy(any(PutUserPolicyRequest.class));
        verify(iam, never()).createAccessKey(any(CreateAccessKeyRequest.class));
        verify(iam, never()).putUserPermissionsBoundary(any(software.amazon.awssdk.services.iam.model.PutUserPermissionsBoundaryRequest.class));
        verify(iam, never()).deleteUserPermissionsBoundary(any(software.amazon.awssdk.services.iam.model.DeleteUserPermissionsBoundaryRequest.class));
    }

    @Test
    void bucketPreparationRemainsAvailableWithoutReaderBoundary() {
        properties.setReaderPermissionsBoundaryArn(null);
        ownedBucket();
        secureBucket();
        assertThat(provider.prepareBucket("connection1", "customer1", BUCKET).versioningEnabled()).isTrue();
        verifyNoInteractions(iam);
    }

    @Test
    void uploadRemainsAvailableWithoutReaderBoundary() {
        properties.setReaderPermissionsBoundaryArn(null);
        uploadReady();
        assertThat(provider.putObject(BUCKET, KEY, BYTES, "text/plain", hex(BYTES)).size()).isEqualTo(BYTES.length);
        verifyNoInteractions(iam);
    }

    @Test
    void readerReplayWithAnExistingKeyRequiresStoredSecretWithoutPolicyChangesOrRotation() {
        ownedBucket();
        existingReader();
        when(iam.listAccessKeys(any(ListAccessKeysRequest.class))).thenReturn(ListAccessKeysResponse.builder()
            .accessKeyMetadata(AccessKeyMetadata.builder().accessKeyId("existing-access").build()).build());
        assertThatThrownBy(() -> provider.createReader(READER, BUCKET, PREFIX))
            .hasMessage("HOSTED_READER_SECRET_ALREADY_ISSUED_USE_STORED_SECRET");
        verify(iam).createUser(any(CreateUserRequest.class));
        verify(iam, never()).createAccessKey(any(CreateAccessKeyRequest.class));
        verify(iam, never()).putUserPolicy(any(PutUserPolicyRequest.class));
        verify(iam, never()).deleteAccessKey(any(software.amazon.awssdk.services.iam.model.DeleteAccessKeyRequest.class));
    }

    @Test
    void concurrentCreationLoserCannotIssueDuplicateKeyEvenBeforeFirstKeyIsVisible() {
        ownedBucket();
        newReader();
        when(iam.createUser(any(CreateUserRequest.class))).thenThrow(EntityAlreadyExistsException.builder().build());
        assertThatThrownBy(() -> provider.createReader(READER, BUCKET, PREFIX))
            .hasMessage("HOSTED_READER_CREATION_INCOMPLETE_RECONCILIATION_REQUIRED");
        verify(iam, never()).createAccessKey(any(CreateAccessKeyRequest.class));
        verify(iam, never()).putUserPolicy(any(PutUserPolicyRequest.class));
    }

    @Test
    void uncertainKeyIssuanceDoesNotRetryOrIssueAKeyOnNextPrepare() {
        ownedBucket();
        newReader();
        when(iam.createAccessKey(any(CreateAccessKeyRequest.class)))
            .thenThrow(SdkClientException.create("Authorization=secret-key; response lost"));
        assertThatThrownBy(() -> provider.createReader(READER, BUCKET, PREFIX))
            .hasMessage("HOSTED_DOCUMENT_STORAGE_AWS_OPERATION_FAILED [CREATE_READER:SdkClientError]").hasNoCause()
            .isInstanceOfSatisfying(DocumentStorageProviderException.class, error -> {
                assertThat(error.getAwsErrorCode()).isEqualTo("SdkClientError");
                assertThat(error.getOperation()).isEqualTo(DocumentStorageProviderException.Operation.CREATE_READER);
            });
        assertThatThrownBy(() -> provider.createReader(READER, BUCKET, PREFIX))
            .hasMessage("HOSTED_READER_CREATION_INCOMPLETE_RECONCILIATION_REQUIRED");
        verify(iam, times(1)).createAccessKey(any(CreateAccessKeyRequest.class));
    }

    @Test
    void matchingUsernameWithoutOwnershipTagsIsNeverAdopted() {
        ownedBucket();
        existingReader();
        when(iam.getUser(any(GetUserRequest.class))).thenReturn(GetUserResponse.builder()
            .user(user().toBuilder().tags(List.of()).build()).build());
        assertThatThrownBy(() -> provider.createReader(READER, BUCKET, PREFIX))
            .hasMessage("HOSTED_READER_OWNERSHIP_MISMATCH");
        verify(iam, never()).listAccessKeys(any(ListAccessKeysRequest.class));
        verify(iam, never()).putUserPolicy(any(PutUserPolicyRequest.class));
        verify(iam, never()).createAccessKey(any(CreateAccessKeyRequest.class));
    }

    @Test
    void differentReaderPrefixOwnershipIsNeverRewritten() {
        ownedBucket();
        existingReader();
        assertThatThrownBy(() -> provider.createReader(READER, BUCKET, "knowledge/staff-only/"))
            .hasMessage("HOSTED_READER_OWNERSHIP_MISMATCH");
        verify(iam, never()).putUserPolicy(any(PutUserPolicyRequest.class));
        verify(iam, never()).createAccessKey(any(CreateAccessKeyRequest.class));
    }

    @Test
    void existingReaderWithOtherControlPlaneTagCannotBeReused() {
        ownedBucket();
        existingReader();
        var tags = user().tags().stream().map(tag -> "control-plane-id".equals(tag.key())
            ? tag.toBuilder().value("developer-staging").build() : tag).toList();
        when(iam.getUser(any(GetUserRequest.class))).thenReturn(GetUserResponse.builder()
            .user(user().toBuilder().tags(tags).build()).build());
        assertThatThrownBy(() -> provider.createReader(READER, BUCKET, PREFIX))
            .hasMessage("HOSTED_READER_OWNERSHIP_MISMATCH");
        verify(iam, never()).listAccessKeys(any(ListAccessKeysRequest.class));
        verify(iam, never()).putUserPolicy(any(PutUserPolicyRequest.class));
        verify(iam, never()).createAccessKey(any(CreateAccessKeyRequest.class));
    }

    @Test
    void readerCarriesConfiguredControlPlaneFromVerifiedBucket() {
        properties.setControlPlaneId("production-control-plane");
        ownedBucket();
        newReader();
        provider.createReader(READER, BUCKET, PREFIX);
        var create = ArgumentCaptor.forClass(CreateUserRequest.class);
        verify(iam).createUser(create.capture());
        assertThat(create.getValue().tags()).contains(software.amazon.awssdk.services.iam.model.Tag.builder()
            .key("control-plane-id").value("production-control-plane").build());
    }

    @Test
    void incorrectPolicyReadbackPreventsIssuingCredentials() {
        ownedBucket();
        newReader();
        when(iam.getUserPolicy(any(GetUserPolicyRequest.class))).thenReturn(GetUserPolicyResponse.builder()
            .policyDocument("{\"Statement\":[]}").build());
        assertThatThrownBy(() -> provider.createReader(READER, BUCKET, PREFIX))
            .hasMessage("HOSTED_READER_POLICY_READBACK_FAILED");
        verify(iam, never()).createAccessKey(any(CreateAccessKeyRequest.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/knowledge/", "knowledge", "../knowledge/", "knowledge/../staff/", "knowledge/*/",
        "knowledge/?/", "knowledge/${aws:username}/", "knowledge//help/", "knowledge\\help/"})
    void rejectsNonCanonicalOrPolicyExpandingPrefixesBeforeAws(String prefix) {
        assertThatThrownBy(() -> provider.createReader(READER, BUCKET, prefix)).hasMessage("HOSTED_OBJECT_PATH_INVALID");
        verifyNoInteractions(s3, iam);
    }

    @Test
    void uploadIsConditionalChecksummedEncryptedAndVersionReadbackVerified() throws Exception {
        uploadReady();
        assertThat(provider.putObject(BUCKET, KEY, BYTES, "text/plain", hex(BYTES)))
            .isEqualTo(new CustomerDocumentStorageProvider.StoredObject("etag-1", "version-1", BYTES.length));
        var put = ArgumentCaptor.forClass(PutObjectRequest.class);
        var body = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3).putObject(put.capture(), body.capture());
        assertThat(put.getValue().ifNoneMatch()).isEqualTo("*");
        assertThat(put.getValue().expectedBucketOwner()).isEqualTo(ACCOUNT);
        assertThat(put.getValue().serverSideEncryption()).isEqualTo(ServerSideEncryption.AES256);
        assertThat(put.getValue().checksumSHA256()).isEqualTo(base64(BYTES));
        assertThat(put.getValue().metadata()).containsEntry("sha256", hex(BYTES));
        assertThat(body.getValue().contentStreamProvider().newStream().readAllBytes()).isEqualTo(BYTES);
        var head = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3).headObject(head.capture());
        assertThat(head.getValue().versionId()).isEqualTo("version-1");
        assertThat(head.getValue().checksumMode()).isEqualTo(ChecksumMode.ENABLED);
        assertThat(head.getValue().expectedBucketOwner()).isEqualTo(ACCOUNT);
        verifyNoInteractions(iam);
    }

    @ParameterizedTest
    @ValueSource(strings = {"r\u00e9sum\u00e9.txt", "\u0633\u064a\u0627\u0633\u0629.txt", "\u6587\u6863.json",
        "document-\uD83D\uDCC4.txt", "cafe\u0301.txt", "report (final) #1 + 10% & $?.txt"})
    void uploadsUnicodeAndLiteralFilenameCharactersWithoutRewritingTheKey(String filename) {
        uploadReady();
        String key = PREFIX + "upload-1/" + filename;
        assertThat(provider.putObject(BUCKET, key, BYTES, "text/plain", hex(BYTES)).size()).isEqualTo(BYTES.length);
        var put = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(put.capture(), any(RequestBody.class));
        assertThat(put.getValue().key()).isEqualTo(key);
        var head = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(s3).headObject(head.capture());
        assertThat(head.getValue().key()).isEqualTo(key);
    }

    @Test
    void permitsExactly1024Utf8BytesRatherThanLimitingUnicodeCharacters() {
        uploadReady();
        String key = "a/" + "\u00e9".repeat(509) + ".txt";
        assertThat(key.getBytes(StandardCharsets.UTF_8)).hasSize(1024);
        assertThat(provider.putObject(BUCKET, key, BYTES, "text/plain", hex(BYTES)).size()).isEqualTo(BYTES.length);
    }

    @Test
    void refusesUnicodeObjectKeysOver1024Utf8BytesBeforeAnyAwsCall() {
        String key = "a/" + "\u00e9".repeat(510) + ".txt";
        assertThat(key.length()).isLessThan(1024);
        assertThatThrownBy(() -> provider.putObject(BUCKET, key, BYTES, "text/plain", hex(BYTES)))
            .hasMessage("HOSTED_OBJECT_PATH_INVALID");
        verifyNoInteractions(s3, iam);
    }

    @Test
    void rejectsAmbiguousPathsControlsAndMalformedUnicodeBeforeAnyAwsCall() {
        for (String key : List.of("knowledge/../document.txt", "knowledge/./document.txt", "knowledge//document.txt",
            "knowledge/document\0.txt", "knowledge/document\r.txt", "knowledge/document\uD800.txt",
            "knowledge/document\uDC00.txt", "knowledge/document\\name.txt")) {
            assertThatThrownBy(() -> provider.putObject(BUCKET, key, BYTES, "text/plain", hex(BYTES)))
                .hasMessage("HOSTED_OBJECT_PATH_INVALID");
        }
        verifyNoInteractions(s3, iam);
    }

    @ParameterizedTest
    @ValueSource(ints = {409, 412})
    void exactConditionalRetryAcceptsOnlyVerifiedSameChecksum(int code) {
        uploadReady();
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(s3Error(code));
        assertThat(provider.putObject(BUCKET, KEY, BYTES, "text/plain", hex(BYTES)).versionId()).isEqualTo("version-1");
        verify(s3, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void conditionalConflictWithDifferentBytesCannotOverwriteOrClaimSuccess() {
        uploadReady();
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(s3Error(412));
        when(s3.headObject(any(HeadObjectRequest.class))).thenReturn(head().toBuilder()
            .checksumSHA256(base64("other bytes".getBytes(StandardCharsets.UTF_8))).build());
        assertThatThrownBy(() -> provider.putObject(BUCKET, KEY, BYTES, "text/plain", hex(BYTES)))
            .hasMessage("HOSTED_OBJECT_CONFLICT_OR_READBACK_FAILED");
        verify(s3, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void metadataAloneIsNotEnoughToAcceptUploadRetry() {
        uploadReady();
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(s3Error(412));
        when(s3.headObject(any(HeadObjectRequest.class))).thenReturn(head().toBuilder().checksumSHA256((String) null).build());
        assertThatThrownBy(() -> provider.putObject(BUCKET, KEY, BYTES, "text/plain", hex(BYTES)))
            .hasMessage("HOSTED_OBJECT_CONFLICT_OR_READBACK_FAILED");
    }

    @Test
    void missingVersionReadbackCannotClaimUploadSuccess() {
        uploadReady();
        when(s3.headObject(any(HeadObjectRequest.class))).thenReturn(head().toBuilder().versionId("null").build());
        assertThatThrownBy(() -> provider.putObject(BUCKET, KEY, BYTES, "text/plain", hex(BYTES)))
            .hasMessage("HOSTED_OBJECT_CONFLICT_OR_READBACK_FAILED");
    }

    @Test
    void uploadRequiresVersioningBeforeWriting() {
        uploadReady();
        when(s3.getBucketVersioning(any(GetBucketVersioningRequest.class)))
            .thenReturn(GetBucketVersioningResponse.builder().status(BucketVersioningStatus.SUSPENDED).build());
        assertThatThrownBy(() -> provider.putObject(BUCKET, KEY, BYTES, "text/plain", hex(BYTES)))
            .hasMessage("HOSTED_BUCKET_VERSIONING_NOT_ENABLED");
        verify(s3, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void rejectsOversizeEmptyAndIncorrectChecksumBeforeAnyAwsCalls() {
        assertThatThrownBy(() -> provider.putObject(BUCKET, KEY, new byte[1_048_577], "text/plain", hex(BYTES)))
            .hasMessage("HOSTED_OBJECT_SIZE_INVALID");
        assertThatThrownBy(() -> provider.putObject(BUCKET, KEY, new byte[0], "text/plain", hex(BYTES)))
            .hasMessage("HOSTED_OBJECT_SIZE_INVALID");
        assertThatThrownBy(() -> provider.putObject(BUCKET, KEY, BYTES, "text/plain", "0".repeat(64)))
            .hasMessage("HOSTED_OBJECT_CHECKSUM_INVALID");
        assertThatThrownBy(() -> provider.putObject(BUCKET, KEY, BYTES, "text/plain\r\nAuthorization: bad", hex(BYTES)))
            .hasMessage("HOSTED_OBJECT_CONTENT_TYPE_INVALID");
        verifyNoInteractions(s3, iam);
    }

    @Test
    void neverClosesInjectedClientOwnership() {
        provider.close();
        verifyNoInteractions(s3, iam);
    }

    private void ownedBucket() {
        when(s3.headBucket(any(HeadBucketRequest.class))).thenReturn(HeadBucketResponse.builder().build());
        when(s3.getBucketLocation(any(GetBucketLocationRequest.class))).thenReturn(GetBucketLocationResponse.builder()
            .locationConstraint("eu-north-1").build());
        bucketControlPlane(properties.getControlPlaneId());
    }

    private void bucketControlPlane(String controlPlaneId) {
        when(s3.getBucketTagging(any(GetBucketTaggingRequest.class))).thenReturn(GetBucketTaggingResponse.builder()
            .tagSet(tag("managed-by", AwsCustomerDocumentStorageProvider.MANAGED_BY), tag("customer-id", "customer1"),
                tag("connection-id", "connection1"), tag("control-plane-id", controlPlaneId)).build());
    }

    private void secureBucket() {
        when(s3.putBucketPolicy(any(PutBucketPolicyRequest.class))).thenAnswer(invocation -> {
            bucketPolicy.set(invocation.<PutBucketPolicyRequest>getArgument(0).policy());
            return PutBucketPolicyResponse.builder().build();
        });
        when(s3.getBucketPolicy(any(GetBucketPolicyRequest.class))).thenAnswer(invocation ->
            GetBucketPolicyResponse.builder().policy(bucketPolicy.get()).build());
        when(s3.getPublicAccessBlock(any(GetPublicAccessBlockRequest.class))).thenReturn(GetPublicAccessBlockResponse.builder()
            .publicAccessBlockConfiguration(PublicAccessBlockConfiguration.builder().blockPublicAcls(true).ignorePublicAcls(true)
                .blockPublicPolicy(true).restrictPublicBuckets(true).build()).build());
        when(s3.getBucketOwnershipControls(any(GetBucketOwnershipControlsRequest.class)))
            .thenReturn(GetBucketOwnershipControlsResponse.builder().ownershipControls(OwnershipControls.builder()
                .rules(OwnershipControlsRule.builder().objectOwnership(ObjectOwnership.BUCKET_OWNER_ENFORCED).build()).build()).build());
        when(s3.getBucketEncryption(any(GetBucketEncryptionRequest.class))).thenReturn(GetBucketEncryptionResponse.builder()
            .serverSideEncryptionConfiguration(ServerSideEncryptionConfiguration.builder().rules(ServerSideEncryptionRule.builder()
                .applyServerSideEncryptionByDefault(ServerSideEncryptionByDefault.builder().sseAlgorithm(ServerSideEncryption.AES256)
                    .build()).build()).build()).build());
        when(s3.getBucketVersioning(any(GetBucketVersioningRequest.class)))
            .thenReturn(GetBucketVersioningResponse.builder().status(BucketVersioningStatus.ENABLED).build());
    }

    private void existingReader() {
        when(iam.createUser(any(CreateUserRequest.class))).thenThrow(EntityAlreadyExistsException.builder().build());
        when(iam.getUser(any(GetUserRequest.class))).thenReturn(GetUserResponse.builder().user(user()).build());
        when(iam.listAccessKeys(any(ListAccessKeysRequest.class))).thenReturn(ListAccessKeysResponse.builder().build());
    }

    private void newReader() {
        existingReader();
        doReturn(CreateUserResponse.builder().user(user()).build()).doThrow(EntityAlreadyExistsException.builder().build())
            .when(iam).createUser(any(CreateUserRequest.class));
        when(iam.putUserPolicy(any(PutUserPolicyRequest.class))).thenAnswer(invocation -> {
            readerPolicy.set(invocation.<PutUserPolicyRequest>getArgument(0).policyDocument());
            return software.amazon.awssdk.services.iam.model.PutUserPolicyResponse.builder().build();
        });
        when(iam.getUserPolicy(any(GetUserPolicyRequest.class))).thenAnswer(invocation -> GetUserPolicyResponse.builder()
            .policyDocument(URLEncoder.encode(readerPolicy.get(), StandardCharsets.UTF_8)).build());
        when(iam.createAccessKey(any(CreateAccessKeyRequest.class))).thenReturn(CreateAccessKeyResponse.builder().accessKey(
            AccessKey.builder().userName(READER).accessKeyId("issued-access").secretAccessKey("issued-secret").build()).build());
    }

    private User user() {
        return User.builder().userName(READER).path(AwsCustomerDocumentStorageProvider.READER_PATH)
            .arn("arn:aws:iam::" + ACCOUNT + ":user" + AwsCustomerDocumentStorageProvider.READER_PATH + READER)
            .permissionsBoundary(AttachedPermissionsBoundary.builder().permissionsBoundaryArn(BOUNDARY).build())
            .tags(Map.of("managed-by", AwsCustomerDocumentStorageProvider.MANAGED_BY, "bucket", BUCKET,
                "prefix-sha256", hex(PREFIX.getBytes(StandardCharsets.UTF_8)), "customer-id", "customer1", "connection-id", "connection1",
                "control-plane-id", properties.getControlPlaneId())
                .entrySet().stream().map(entry -> software.amazon.awssdk.services.iam.model.Tag.builder()
                    .key(entry.getKey()).value(entry.getValue()).build()).toList()).build();
    }

    private void uploadReady() {
        ownedBucket();
        when(s3.getBucketVersioning(any(GetBucketVersioningRequest.class)))
            .thenReturn(GetBucketVersioningResponse.builder().status(BucketVersioningStatus.ENABLED).build());
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenReturn(PutObjectResponse.builder()
            .eTag("etag-1").versionId("version-1").build());
        when(s3.headObject(any(HeadObjectRequest.class))).thenReturn(head());
    }

    private HeadObjectResponse head() {
        return HeadObjectResponse.builder().eTag("etag-1").versionId("version-1").contentLength((long) BYTES.length)
            .checksumSHA256(base64(BYTES)).metadata(Map.of("sha256", hex(BYTES)))
            .serverSideEncryption(ServerSideEncryption.AES256).build();
    }

    private void verifyNoBucketMutations() {
        verify(s3, never()).createBucket(any(CreateBucketRequest.class));
        verify(s3, never()).putBucketTagging(any(PutBucketTaggingRequest.class));
        verify(s3, never()).putPublicAccessBlock(any(PutPublicAccessBlockRequest.class));
        verify(s3, never()).putBucketOwnershipControls(any(PutBucketOwnershipControlsRequest.class));
        verify(s3, never()).putBucketEncryption(any(PutBucketEncryptionRequest.class));
        verify(s3, never()).putBucketVersioning(any(PutBucketVersioningRequest.class));
        verify(s3, never()).putBucketPolicy(any(PutBucketPolicyRequest.class));
    }

    private static S3Exception s3Error(int code) {
        S3Exception.Builder builder = S3Exception.builder();
        builder.statusCode(code);
        return (S3Exception) builder.build();
    }
    private static S3Exception s3Error(int status, String awsCode) {
        S3Exception.Builder builder = S3Exception.builder();
        builder.statusCode(status);
        builder.awsErrorDetails(AwsErrorDetails.builder().errorCode(awsCode).errorMessage("raw-detail provider-secret").build());
        builder.message("Authorization: provider-secret");
        builder.cause(new IllegalStateException("nested-secret"));
        return (S3Exception) builder.build();
    }
    private static Tag tag(String key, String value) { return Tag.builder().key(key).value(value).build(); }
    private static String hex(byte[] bytes) { return HexFormat.of().formatHex(digest(bytes)); }
    private static String base64(byte[] bytes) { return Base64.getEncoder().encodeToString(digest(bytes)); }
    private static byte[] digest(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (Exception ex) { throw new AssertionError(ex); }
    }
}
