package no.unit.nva.download.publication.file;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import no.unit.nva.download.publication.file.aws.s3.AwsS3Service;
import no.unit.nva.download.publication.file.aws.s3.exception.S3ServiceException;
import no.unit.nva.download.publication.file.publication.RestPublicationService;
import no.unit.nva.download.publication.file.publication.exception.NoResponseException;
import no.unit.nva.download.publication.file.publication.exception.NotFoundException;
import no.unit.nva.identifiers.SortableIdentifier;
import no.unit.nva.model.File;
import no.unit.nva.model.FileSet;
import no.unit.nva.model.License;
import no.unit.nva.model.Organization;
import no.unit.nva.model.Publication;
import no.unit.nva.model.PublicationStatus;
import nva.commons.apigateway.GatewayResponse;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.zalando.problem.Problem;

import static no.unit.nva.download.publication.file.RequestUtil.CUSTOM_FEIDE_ID;
import static no.unit.nva.download.publication.file.aws.s3.AwsS3ServiceTest.MIME_TYPE_APPLICATION_PDF;
import static no.unit.nva.download.publication.file.aws.s3.AwsS3ServiceTest.PRESIGNED_DOWNLOAD_URL;
import static no.unit.nva.download.publication.file.publication.RestPublicationService.ERROR_COMMUNICATING_WITH_REMOTE_SERVICE;
import static no.unit.nva.download.publication.file.publication.RestPublicationService.ERROR_PUBLICATION_NOT_FOUND_FOR_IDENTIFIER;
import static no.unit.nva.testutils.HandlerRequestBuilder.AUTHORIZER_NODE;
import static no.unit.nva.testutils.HandlerRequestBuilder.CLAIMS_NODE;
import static nva.commons.apigateway.ApiGatewayHandler.ACCESS_CONTROL_ALLOW_ORIGIN;
import static nva.commons.apigateway.ApiGatewayHandler.ALLOWED_ORIGIN_ENV;
import static nva.commons.core.JsonUtils.objectMapper;
import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_SERVICE_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CreatePresignedDownloadUrlHandlerTest {

    public static final String SOME_API_KEY = "some api key";
    public static final String PATH_PARAMETERS = "pathParameters";
    public static final String HEADERS = "headers";
    public static final String IDENTIFIER = "identifier";
    public static final String IDENTIFIER_FILE = "fileIdentifier";
    public static final String IDENTIFIER_VALUE = "29f6887c-8852-11ea-bc55-0242ac130003";
    public static final String IDENTIFIER_FILE_VALUE = "29f68c1e-8852-11ea-bc55-0242ac130003";
    public static final String OWNER_USER_ID = "owner@unit.no";
    public static final String REQUEST_CONTEXT_NODE = "requestContext";

    private RestPublicationService publicationService;
    private AwsS3Service awsS3Service;
    private Context context;
    private ByteArrayOutputStream output;

    private CreatePresignedDownloadUrlHandler createPresignedDownloadUrlHandler;

    /**
     * Set up environment.
     */
    @BeforeEach
    public void setUp() {
        Environment environment = mock(Environment.class);
        when(environment.readEnv(ALLOWED_ORIGIN_ENV)).thenReturn("*");

        publicationService = mock(RestPublicationService.class);
        awsS3Service = mock(AwsS3Service.class);
        context = mock(Context.class);
        output = new ByteArrayOutputStream();
        createPresignedDownloadUrlHandler =
                new CreatePresignedDownloadUrlHandler(publicationService, awsS3Service, environment);
    }

    @Test
    @DisplayName("handler Default Constructor Throws Exception When Envs Are Not Set")
    public void defaultConstructorThrowsExceptionWhenEnvsAreNotSet() {
        assertThrows(IllegalStateException.class, CreatePresignedDownloadUrlHandler::new);
    }

    @Test
    @DisplayName("handler Returns Ok Response On Valid Input (Published Publication)")
    public void handlerReturnsOkResponseOnValidInputPublishedPublication() throws IOException,
                                                                                  ApiGatewayException {

        Publication publication = createPublishedPublication(IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE);
        when(publicationService.getPublication(any(String.class)))
                .thenReturn(publication);
        when(awsS3Service.createPresignedDownloadUrl(IDENTIFIER_FILE_VALUE, MIME_TYPE_APPLICATION_PDF))
                .thenReturn(PRESIGNED_DOWNLOAD_URL);

        createPresignedDownloadUrlHandler.handleRequest(inputStream(IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE),
                output, context);

        var gatewayResponse = objectMapper.readValue(output.toString(), GatewayResponse.class);
        assertEquals(SC_OK, gatewayResponse.getStatusCode());
        assertTrue(gatewayResponse.getHeaders().containsKey(CONTENT_TYPE));
        assertTrue(gatewayResponse.getHeaders().containsKey(ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("handler Returns Ok Response On Valid Input (Unpublished Publication)")
    public void handlerReturnsOkResponseOnValidInputUnpublishedPublication() throws IOException,
            ApiGatewayException {

        Publication publication = createUnpublishedPublication(IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE);
        when(publicationService.getPublication(any(String.class)))
                .thenReturn(publication);
        when(awsS3Service.createPresignedDownloadUrl(IDENTIFIER_FILE_VALUE, MIME_TYPE_APPLICATION_PDF))
                .thenReturn(PRESIGNED_DOWNLOAD_URL);

        createPresignedDownloadUrlHandler.handleRequest(inputStream(IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE,
                OWNER_USER_ID), output, context);

        var gatewayResponse = objectMapper.readValue(output.toString(), GatewayResponse.class);
        assertEquals(SC_OK, gatewayResponse.getStatusCode());
        assertTrue(gatewayResponse.getHeaders().containsKey(CONTENT_TYPE));
        assertTrue(gatewayResponse.getHeaders().containsKey(ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("handler Returns Not Found Response On Unknown Identifier")
    public void handlerReturnsNotFoundResponseOnUnknownIdentifier() throws IOException, ApiGatewayException {
        when(publicationService.getPublication(any(String.class)))
                .thenThrow(new NotFoundException(ERROR_PUBLICATION_NOT_FOUND_FOR_IDENTIFIER + IDENTIFIER_VALUE));

        createPresignedDownloadUrlHandler.handleRequest(inputStream(IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE),
                output, context);

        var gatewayResponse = objectMapper.readValue(output.toString(), GatewayResponse.class);
        assertEquals(SC_NOT_FOUND, gatewayResponse.getStatusCode());
        assertTrue(gatewayResponse.getHeaders().containsKey(CONTENT_TYPE));
        assertTrue(gatewayResponse.getHeaders().containsKey(ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("handler Returns Not Found Response On Malformed Resource Identifier")
    public void handlerReturnsNotFoundResponseOnMalformedIdentifier() throws IOException, ApiGatewayException {
        when(publicationService.getPublication(any(String.class)))
            .thenThrow(new NotFoundException(ERROR_PUBLICATION_NOT_FOUND_FOR_IDENTIFIER + IDENTIFIER_VALUE));
        createPresignedDownloadUrlHandler.handleRequest(inputStream(IDENTIFIER, IDENTIFIER_FILE_VALUE), output,
                context);

        var gatewayResponse = objectMapper.readValue(output.toString(), GatewayResponse.class);
        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, gatewayResponse.getStatusCode());
        assertTrue(gatewayResponse.getHeaders().containsKey(CONTENT_TYPE));
        assertTrue(gatewayResponse.getHeaders().containsKey(ACCESS_CONTROL_ALLOW_ORIGIN));

    }

    @Test
    @DisplayName("handler Returns Service Unavailable Response On No Response From Publication Service")
    public void handlerReturnsServiceUnavailableResponseOnNoResponseFromPublicationService() throws IOException,
            ApiGatewayException {

        when(publicationService.getPublication(any(String.class)))
                .thenThrow(new NoResponseException(ERROR_COMMUNICATING_WITH_REMOTE_SERVICE,
                        new Exception()));
        createPresignedDownloadUrlHandler.handleRequest(inputStream(IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE),
                output, context);

        var gatewayResponse = objectMapper.readValue(output.toString(), GatewayResponse.class);
        assertEquals(SC_SERVICE_UNAVAILABLE, gatewayResponse.getStatusCode());
        assertTrue(gatewayResponse.getHeaders().containsKey(CONTENT_TYPE));
        assertTrue(gatewayResponse.getHeaders().containsKey(ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("handler Returns Not Found Response On Unknown File Identifier")
    public void handlerReturnsBadRequestResponseOnUnknownFileIdentifier() throws ApiGatewayException, IOException {
        Publication publication = createPublishedPublication(IDENTIFIER_VALUE, IDENTIFIER_VALUE);
        when(publicationService.getPublication(any(String.class)))
                .thenReturn(publication);

        createPresignedDownloadUrlHandler.handleRequest(inputStream(IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE),
                output, context);

        var gatewayResponse = objectMapper.readValue(output.toString(), GatewayResponse.class);
        assertEquals(SC_NOT_FOUND, gatewayResponse.getStatusCode());
        assertTrue(gatewayResponse.getHeaders().containsKey(CONTENT_TYPE));
        assertTrue(gatewayResponse.getHeaders().containsKey(ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("handler Returns Internal Server Error Response On Duplicate File Identifier In Publication")
    public void handlerReturnsInternalServerErrorResponseOnDuplicateFileIdentifierInPublication()
            throws ApiGatewayException, IOException {
        Publication publication = createPublishedPublicationDuplicateFile(IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE);
        when(publicationService.getPublication(any(String.class)))
                .thenReturn(publication);

        createPresignedDownloadUrlHandler.handleRequest(inputStream(IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE),
                output, context);

        var gatewayResponse = objectMapper.readValue(output.toString(), GatewayResponse.class);
        assertEquals(SC_INTERNAL_SERVER_ERROR, gatewayResponse.getStatusCode());
        assertTrue(gatewayResponse.getHeaders().containsKey(CONTENT_TYPE));
        assertTrue(gatewayResponse.getHeaders().containsKey(ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("handler Returns Not Found Response On Publication Without Files")
    public void handlerReturnsBadRequestResponseOnPublicationWithoutFile() throws IOException,
            ApiGatewayException {
        Publication publication = createPublicationWithoutFileSetFile(IDENTIFIER_VALUE);
        when(publicationService.getPublication(any(String.class)))
                .thenReturn(publication);

        createPresignedDownloadUrlHandler.handleRequest(inputStream(IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE),
                output, context);

        var gatewayResponse = objectMapper.readValue(output.toString(), GatewayResponse.class);
        assertEquals(SC_NOT_FOUND, gatewayResponse.getStatusCode());
        assertTrue(gatewayResponse.getHeaders().containsKey(CONTENT_TYPE));
        assertTrue(gatewayResponse.getHeaders().containsKey(ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("handler Returns Service Unavailable Response on S3 Exception")
    public void handlerReturnsServiceUnavailableResponseOnS3ServiceException() throws IOException,
            ApiGatewayException {
        Publication publication = createPublishedPublication(IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE);
        when(publicationService.getPublication(any(String.class)))
                .thenReturn(publication);
        when(awsS3Service.createPresignedDownloadUrl(IDENTIFIER_FILE_VALUE, MIME_TYPE_APPLICATION_PDF))
                .thenThrow(new S3ServiceException("message", new SdkClientException("message")));

        createPresignedDownloadUrlHandler.handleRequest(inputStream(IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE),
                output, context);

        var gatewayResponse = objectMapper.readValue(output.toString(), GatewayResponse.class);
        assertEquals(SC_SERVICE_UNAVAILABLE, gatewayResponse.getStatusCode());
        assertTrue(gatewayResponse.getHeaders().containsKey(CONTENT_TYPE));
        assertTrue(gatewayResponse.getHeaders().containsKey(ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("handler Returns NotFound On Anonymous Request For Not Published Publication")
    public void handlerReturnsNotFoundOnAnonymousRequestForNotPublishedPublication()
            throws ApiGatewayException, IOException {
        Publication publication = createUnpublishedPublication(IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE);
        when(publicationService.getPublication(any(String.class)))
                .thenReturn(publication);

        createPresignedDownloadUrlHandler.handleRequest(anonymousInputStream(IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE),
                output, context);

        var gatewayResponse = objectMapper.readValue(output.toString(), GatewayResponse.class);
        assertEquals(SC_NOT_FOUND, gatewayResponse.getStatusCode());
    }


    private Publication createPublicationWithoutFileSetFile(String identifier) {
        return new Publication.Builder()
                .withIdentifier(new SortableIdentifier(identifier))
                .withModifiedDate(Instant.now())
                .withOwner(OWNER_USER_ID)
                .withFileSet(new FileSet.Builder().build())
                .withStatus(PublicationStatus.PUBLISHED)
                .build();
    }

    private Publication createUnpublishedPublication(String identifier, String fileIdentifier) {
        FileSet fileSet = new FileSet.Builder()
                .withFiles(Collections.singletonList(
                        new File.Builder()
                                .withIdentifier(UUID.fromString(fileIdentifier))
                                .withMimeType(MIME_TYPE_APPLICATION_PDF)
                                .withLicense(new License.Builder().build())
                                .build())
                ).build();

        return new Publication.Builder()
                .withIdentifier(new SortableIdentifier(identifier))
                .withModifiedDate(Instant.now())
                .withOwner(OWNER_USER_ID)
                .withStatus(PublicationStatus.NEW)
                .withFileSet(fileSet).build();
    }

    private Publication createPublishedPublication(String identifier, String fileIdentifier) {
        FileSet fileSet = new FileSet.Builder()
                .withFiles(Collections.singletonList(
                        new File.Builder()
                                .withIdentifier(UUID.fromString(fileIdentifier))
                                .withMimeType(MIME_TYPE_APPLICATION_PDF)
                                .withLicense(new License.Builder().build())
                                .build())
                ).build();

        return new Publication.Builder()
                .withIdentifier(new SortableIdentifier(identifier))
                .withCreatedDate(Instant.now())
                .withModifiedDate(Instant.now())
                .withOwner(OWNER_USER_ID)
                .withPublisher(new Organization.Builder()
                        .withId(URI.create("http://example.org/publisher/1"))
                        .build()
                )
                .withStatus(PublicationStatus.PUBLISHED)
                .withFileSet(fileSet).build();
    }

    private Publication createPublishedPublicationDuplicateFile(String identifier, String fileIdentifier) {
        FileSet fileSet = new FileSet.Builder()
                .withFiles(Collections.nCopies(2,
                        new File.Builder()
                                .withIdentifier(UUID.fromString(fileIdentifier))
                                .withMimeType(MIME_TYPE_APPLICATION_PDF)
                                .withLicense(new License.Builder().build())
                                .build())
                ).build();

        return new Publication.Builder()
                .withIdentifier(new SortableIdentifier(identifier))
                .withCreatedDate(Instant.now())
                .withModifiedDate(Instant.now())
                .withOwner(OWNER_USER_ID)
                .withPublisher(new Organization.Builder()
                        .withId(URI.create("http://example.org/publisher/1"))
                        .build()
                )
                .withStatus(PublicationStatus.PUBLISHED)
                .withFileSet(fileSet).build();
    }

    private InputStream anonymousInputStream(String identifier, String identifierFile) throws IOException {
        Map<String, Object> event = new ConcurrentHashMap<>();
        Map<String, String> headers = new ConcurrentHashMap<>();
        // not authorization header
        event.put(HEADERS, headers);
        Map<String, String> pathParameters = new ConcurrentHashMap<>();
        pathParameters.put(IDENTIFIER, identifier);
        pathParameters.put(IDENTIFIER_FILE, identifierFile);
        event.put(PATH_PARAMETERS, pathParameters);
        // no authorizer claims
        return new ByteArrayInputStream(objectMapper.writeValueAsBytes(event));
    }

    private InputStream inputStream(String identifier, String identifierFile) throws IOException {
        Map<String, Object> event = new ConcurrentHashMap<>();
        Map<String, String> headers = new ConcurrentHashMap<>();
        headers.put(AUTHORIZATION, SOME_API_KEY);
        event.put(HEADERS, headers);
        Map<String, String> pathParameters = new ConcurrentHashMap<>();
        pathParameters.put(IDENTIFIER, identifier);
        pathParameters.put(IDENTIFIER_FILE, identifierFile);
        event.put(PATH_PARAMETERS, pathParameters);
        return new ByteArrayInputStream(objectMapper.writeValueAsBytes(event));
    }

    private InputStream inputStream(String identifier, String identifierFile, String userId) throws IOException {
        Map<String, Object> event = new ConcurrentHashMap<>();
        Map<String, String> headers = new ConcurrentHashMap<>();
        headers.put(AUTHORIZATION, SOME_API_KEY);
        event.put(HEADERS, headers);
        Map<String, String> pathParameters = new ConcurrentHashMap<>();
        pathParameters.put(IDENTIFIER, identifier);
        pathParameters.put(IDENTIFIER_FILE, identifierFile);
        event.put(PATH_PARAMETERS, pathParameters);
        Map<String, String> claimsValue = new ConcurrentHashMap<>();
        claimsValue.put(CUSTOM_FEIDE_ID, userId);
        Map<String, Object> claims = new ConcurrentHashMap<>();
        claims.put(CLAIMS_NODE, claimsValue);
        Map<String, Object> authorizer = new ConcurrentHashMap<>();
        authorizer.put(AUTHORIZER_NODE, claims);
        event.put(REQUEST_CONTEXT_NODE, authorizer);

        return new ByteArrayInputStream(objectMapper.writeValueAsBytes(event));
    }
}
