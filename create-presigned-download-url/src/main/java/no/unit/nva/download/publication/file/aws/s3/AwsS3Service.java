package no.unit.nva.download.publication.file.aws.s3;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import no.unit.nva.download.publication.file.aws.s3.exception.S3ServiceException;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.apache.http.HttpHeaders;

import java.util.Date;

import static java.util.Objects.nonNull;


public class AwsS3Service {
    public static final String AWS_REGION_ENV = "AWS_REGION";
    public static final String BUCKET_NAME_ENV = "BUCKET_NAME";

    private final String bucketName;

    private final AmazonS3 s3Client;

    /**
     * Constructor for AwsS3Service.
     *
     * @param s3Client   s3Client
     * @param bucketName bucketKey
     */
    public AwsS3Service(AmazonS3 s3Client, String bucketName) {
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
        this(AmazonS3ClientBuilder.standard().withRegion(environment.readEnv(AWS_REGION_ENV)).build(),
             environment.readEnv(BUCKET_NAME_ENV));
    }

    /**
     * Generate a presigned download URL.
     *
     * @param key      key
     * @param mimeType mimeType
     * @return A presigned download URL
     * @throws ApiGatewayException exception thrown if value is missing
     */
    public String createPresignedDownloadUrl(String key, String mimeType, Date expiration) throws ApiGatewayException {
        try {
            GeneratePresignedUrlRequest generatePresignedUrlRequest =
                createGeneratePresignedUrlRequest(key, mimeType, expiration);

            return s3Client.generatePresignedUrl(generatePresignedUrlRequest).toExternalForm();
        } catch (Exception e) {
            e.printStackTrace();
            throw new S3ServiceException(e.getMessage(), e);
        }
    }

    private GeneratePresignedUrlRequest createGeneratePresignedUrlRequest(String key,
                                                                          String mimeType,
                                                                          Date expiration) {
        GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(bucketName, key,
                                                                                                  HttpMethod.GET);
        generatePresignedUrlRequest.setExpiration(expiration);

        if (nonNull(mimeType)) {
            generatePresignedUrlRequest.addRequestParameter(HttpHeaders.CONTENT_TYPE, mimeType);
        }

        return generatePresignedUrlRequest;
    }
}