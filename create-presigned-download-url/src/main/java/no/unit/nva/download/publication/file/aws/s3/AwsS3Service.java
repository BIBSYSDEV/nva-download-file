package no.unit.nva.download.publication.file.aws.s3;

import java.time.Duration;
import no.unit.nva.download.publication.file.PresignedUri;
import no.unit.nva.download.publication.file.aws.s3.exception.S3ServiceException;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import java.util.Date;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;


public class AwsS3Service {

    public static final String BUCKET_NAME_ENV = "BUCKET_NAME";

    private final String bucketName;

    private final S3Client s3Client;

    /**
     * Constructor for AwsS3Service.
     *
     * @param s3Client   s3Client
     * @param bucketName bucketKey
     */
    public AwsS3Service(S3Client s3Client, String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    /**
     * Constructor for AwsS3Service.
     *
     * @param environment environment
     */
    @JacocoGenerated
    public AwsS3Service(Environment environment) {
        this(S3Client.create(), environment.readEnv(BUCKET_NAME_ENV));
    }

    /**
     * Generate a presigned download URL.
     *
     * @param key      key
     * @return A presigned download URL
     * @throws ApiGatewayException exception thrown if value is missing
     */
    @JacocoGenerated
    public PresignedUri createPresignedDownloadUrl(String key, Duration duration) throws ApiGatewayException {
        try (var s3Presigner = S3Presigner.builder().region(Region.EU_WEST_1).s3Client(s3Client).build()) {
            var presignGetObject = s3Presigner.presignGetObject(createPresignObjectRequest(key, duration));
            return new PresignedUri(presignGetObject.url().toExternalForm(), Date.from(presignGetObject.expiration())
                , null);
        } catch (Exception e) {
            e.printStackTrace();
            throw new S3ServiceException(e.getMessage(), e);
        }
    }

    private GetObjectPresignRequest createPresignObjectRequest(String key, Duration duration) {
        return GetObjectPresignRequest.builder()
                   .signatureDuration(duration)
                   .getObjectRequest(createObjectRequest(key))
                   .build();
    }

    private GetObjectRequest createObjectRequest(String key) {
        return GetObjectRequest.builder()
                   .bucket(bucketName)
                   .key(key)
                   .build();
    }
}
