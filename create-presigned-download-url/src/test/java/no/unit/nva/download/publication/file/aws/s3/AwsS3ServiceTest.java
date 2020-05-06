package no.unit.nva.download.publication.file.aws.s3;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import no.unit.nva.download.publication.file.aws.s3.exception.S3ServiceException;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.utils.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AwsS3ServiceTest {

    public static final String REGION = "region";
    public static final String BUCKET_NAME = "bucketname";
    public static final String OBJECT_KEY = "12345";
    public static final String PRESIGNED_DOWNLOAD_URL = "https://example.com/download/12345";
    public static final String MIME_TYPE_APPLICATION_PDF = "application/pdf";

    private AmazonS3 s3Client;
    private Environment environment;

    /**
     * Set up environment.
     */
    @BeforeEach
    public void setUp() {
        s3Client = mock(AmazonS3.class);
        environment = mock(Environment.class);
    }


    @Test
    @DisplayName("createPresignedDownloadUrl throws S3ServiceException when creating download URL fails")
    public void getAwsS3ClientError() {
        when(s3Client.generatePresignedUrl(any())).thenThrow(SdkClientException.class);

        AwsS3Service awsS3Service = new AwsS3Service(s3Client, BUCKET_NAME);

        assertThrows(S3ServiceException.class, () ->
                awsS3Service.createPresignedDownloadUrl(OBJECT_KEY, MIME_TYPE_APPLICATION_PDF));
    }

    @Test
    @DisplayName("createPresignedDownloadUrl returns a non null Url when input is a non null object key and mime "
            + "type is application/pdf")
    public void getS3PresignedDownloadUrlWithMimeType() throws MalformedURLException, ApiGatewayException {

        when(s3Client.generatePresignedUrl(any())).thenReturn(new URL(PRESIGNED_DOWNLOAD_URL));

        AwsS3Service awsS3Service = new AwsS3Service(s3Client, BUCKET_NAME);

        String presignedDownloadUrl = awsS3Service.createPresignedDownloadUrl(OBJECT_KEY, MIME_TYPE_APPLICATION_PDF);

        assertNotNull(presignedDownloadUrl);

    }

    @Test
    @DisplayName("createPresignedDownloadUrl returns a non null Url when input is a non null object key and mime "
            + "type is null")
    public void getS3PresignedDownloadUrl() throws MalformedURLException, ApiGatewayException {

        when(s3Client.generatePresignedUrl(any())).thenReturn(new URL(PRESIGNED_DOWNLOAD_URL));

        AwsS3Service awsS3Service = new AwsS3Service(s3Client, BUCKET_NAME);

        String presignedDownloadUrl = awsS3Service.createPresignedDownloadUrl(OBJECT_KEY, null);

        assertNotNull(presignedDownloadUrl);

    }
}
