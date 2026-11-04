package com.ai.fabric.platform.backend.documentstorage.provider;

/** Only fixed, safe codes cross the AWS/provider boundary; SDK causes can contain credentials. */
public class DocumentStorageProviderException extends IllegalStateException {
    public enum Operation { PREPARE_BUCKET, CREATE_READER, PUT_OBJECT }

    enum AwsErrorCode {
        ACCESS_DENIED("AccessDenied"),
        ACCESS_DENIED_EXCEPTION("AccessDeniedException"),
        UNAUTHORIZED_OPERATION("UnauthorizedOperation"),
        INVALID_ACCESS_KEY_ID("InvalidAccessKeyId"),
        SIGNATURE_DOES_NOT_MATCH("SignatureDoesNotMatch"),
        EXPIRED_TOKEN("ExpiredToken"),
        EXPIRED_TOKEN_EXCEPTION("ExpiredTokenException"),
        INVALID_TOKEN("InvalidToken"),
        UNRECOGNIZED_CLIENT_EXCEPTION("UnrecognizedClientException"),
        NO_SUCH_BUCKET("NoSuchBucket"),
        NO_SUCH_KEY("NoSuchKey"),
        NO_SUCH_ENTITY("NoSuchEntity"),
        NO_SUCH_TAG_SET("NoSuchTagSet"),
        NOT_FOUND("NotFound"),
        BUCKET_ALREADY_EXISTS("BucketAlreadyExists"),
        BUCKET_ALREADY_OWNED_BY_YOU("BucketAlreadyOwnedByYou"),
        ENTITY_ALREADY_EXISTS("EntityAlreadyExists"),
        LIMIT_EXCEEDED("LimitExceeded"),
        THROTTLING("Throttling"),
        THROTTLING_EXCEPTION("ThrottlingException"),
        SLOW_DOWN("SlowDown"),
        REQUEST_TIMEOUT("RequestTimeout"),
        REQUEST_TIMEOUT_EXCEPTION("RequestTimeoutException"),
        SERVICE_UNAVAILABLE("ServiceUnavailable"),
        INTERNAL_ERROR("InternalError"),
        INTERNAL_FAILURE("InternalFailure"),
        PRECONDITION_FAILED("PreconditionFailed"),
        CONDITIONAL_REQUEST_CONFLICT("ConditionalRequestConflict"),
        UNKNOWN_AWS_ERROR("UnknownAwsError"),
        SDK_CLIENT_ERROR("SdkClientError");

        private final String value;

        AwsErrorCode(String value) {
            this.value = value;
        }

        static AwsErrorCode fromAws(String code, int status) {
            for (AwsErrorCode candidate : values()) {
                if (candidate.value.equals(code)) { return candidate; }
            }
            return switch (status) {
                case 403 -> ACCESS_DENIED;
                case 404 -> NOT_FOUND;
                case 412 -> PRECONDITION_FAILED;
                case 429 -> THROTTLING;
                case 500 -> INTERNAL_ERROR;
                case 503 -> SERVICE_UNAVAILABLE;
                case 504 -> REQUEST_TIMEOUT;
                default -> UNKNOWN_AWS_ERROR;
            };
        }
    }

    private final String code;
    private final Operation operation;
    private final AwsErrorCode awsErrorCode;

    public DocumentStorageProviderException(String code) {
        this(code, null, null);
    }

    private DocumentStorageProviderException(String code, Operation operation, AwsErrorCode awsErrorCode) {
        super(awsErrorCode == null ? code : code + " [" + operation + ":" + awsErrorCode.value + "]");
        this.code = code;
        this.operation = operation;
        this.awsErrorCode = awsErrorCode;
    }

    public String getCode() { return code; }
    public Operation getOperation() { return operation; }
    public String getAwsErrorCode() { return awsErrorCode == null ? null : awsErrorCode.value; }

    DocumentStorageProviderException withOperation(Operation context) {
        return operation == null ? new DocumentStorageProviderException(code, context, awsErrorCode) : this;
    }

    static DocumentStorageProviderException awsFailure(Operation operation, AwsErrorCode code) {
        return new DocumentStorageProviderException("HOSTED_DOCUMENT_STORAGE_AWS_OPERATION_FAILED", operation, code);
    }
}
