package no.unit.nva.download.publication.file;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.lambda.runtime.Context;
import no.unit.nva.download.publication.file.aws.s3.AwsS3Service;
import no.unit.nva.download.publication.file.aws.s3.exception.S3ServiceException;
import no.unit.nva.download.publication.file.publication.RestPublicationService;
import no.unit.nva.download.publication.file.publication.exception.NoResponseException;
import no.unit.nva.download.publication.file.publication.exception.NotFoundException;
import no.unit.nva.model.File;
import no.unit.nva.model.FileSet;
import no.unit.nva.model.License;
import no.unit.nva.model.Organization;
import no.unit.nva.model.Publication;
import no.unit.nva.model.PublicationStatus;
import no.unit.nva.testutils.TestContext;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.handlers.GatewayResponse;
import nva.commons.utils.Environment;
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

import static no.unit.nva.download.publication.file.aws.s3.AwsS3ServiceTest.MIME_TYPE_APPLICATION_PDF;
import static no.unit.nva.download.publication.file.aws.s3.AwsS3ServiceTest.PRESIGNED_DOWNLOAD_URL;
import static no.unit.nva.download.publication.file.publication.RestPublicationService.ERROR_COMMUNICATING_WITH_REMOTE_SERVICE;
import static no.unit.nva.download.publication.file.publication.RestPublicationService.ERROR_PUBLICATION_NOT_FOUND_FOR_IDENTIFIER;
import static nva.commons.handlers.ApiGatewayHandler.ACCESS_CONTROL_ALLOW_ORIGIN;
import static nva.commons.handlers.ApiGatewayHandler.ALLOWED_ORIGIN_ENV;
import static nva.commons.utils.JsonUtils.objectMapper;
import static nva.commons.utils.MockClaims.AUTHORIZER_NODE;
import static nva.commons.utils.MockClaims.CLAIMS_NODE;
import static nva.commons.utils.MockClaims.CUSTOM_FEIDE_ID;
import static nva.commons.utils.MockClaims.REQUEST_CONTEXT_NODE;
import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_SERVICE_UNAVAILABLE;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    public static final String NOT_OWNER_USER_ID = "not-owner@unit.no";

    private Environment environment;
    private RestPublicationService publicationService;
    private AwsS3Service awsS3Service;
    private Context context;
    private OutputStream output;

    private CreatePresignedDownloadUrlHandler createPresignedDownloadUrlHandler;

    /**
     * Set up environment.
     */
    @BeforeEach
    public void setUp() {
        environment = mock(Environment.class);
        when(environment.readEnv(ALLOWED_ORIGIN_ENV)).thenReturn("*");

        publicationService = mock(RestPublicationService.class);
        awsS3Service = mock(AwsS3Service.class);
        context = new TestContext();
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
        when(publicationService.getPublicationWithAuthorizationToken(any(UUID.class), anyString()))
                .thenReturn(publication);
        when(awsS3Service.createPresignedDownloadUrl(IDENTIFIER_FILE_VALUE, MIME_TYPE_APPLICATION_PDF))
            .thenReturn(PRESIGNED_DOWNLOAD_URL);

        createPresignedDownloadUrlHandler.handleRequest(inputStream(IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE),
            output, context);

        GatewayResponse gatewayResponse = objectMapper.readValue(output.toString(), GatewayResponse.class);
        assertEquals(SC_OK, gatewayResponse.getStatusCode());
        assertTrue(gatewayResponse.getHeaders().keySet().contains(CONTENT_TYPE));
        assertTrue(gatewayResponse.getHeaders().keySet().contains(ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    public void handlerReturnsPublishedPublicationWhenRequestDoesNotContainAuthorizationToken() {
        Publication publication = createPublishedPublication(IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE);
    }

    @Test
    @DisplayName("handler Returns Ok Response On Valid Input (Unpublished Publication)")
    public void handlerReturnsOkResponseOnValidInputUnpublishedPublication() throws IOException,
                                                                                    ApiGatewayException {

        Publication publication = createUnpublishedPublication(IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE);
        when(publicationService.getPublicationWithAuthorizationToken(any(UUID.class), anyString()))
            .thenReturn(publication);
        when(awsS3Service.createPresignedDownloadUrl(IDENTIFIER_FILE_VALUE, MIME_TYPE_APPLICATION_PDF))
                .thenReturn(PRESIGNED_DOWNLOAD_URL);

        createPresignedDownloadUrlHandler.handleRequest(inputStream(IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE,
                OWNER_USER_ID), output, context);

        GatewayResponse gatewayResponse = objectMapper.readValue(output.toString(), GatewayResponse.class);
        assertEquals(SC_OK, gatewayResponse.getStatusCode());
        assertTrue(gatewayResponse.getHeaders().keySet().contains(CONTENT_TYPE));
        assertTrue(gatewayResponse.getHeaders().keySet().contains(ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("handler Returns Not Found Response On Unknown Identifier")
    public void handlerReturnsNotFoundResponseOnUnknownIdentifier() throws IOException, ApiGatewayException {
        when(publicationService.getPublicationWithAuthorizationToken(any(UUID.class), anyString()))
                .thenThrow(new NotFoundException(ERROR_PUBLICATION_NOT_FOUND_FOR_IDENTIFIER + IDENTIFIER_VALUE));

        createPresignedDownloadUrlHandler.handleRequest(inputStream(IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE),
                output, context);

        GatewayResponse gatewayResponse = objectMapper.readValue(output.toString(), GatewayResponse.class);
        assertEquals(SC_NOT_FOUND, gatewayResponse.getStatusCode());
        assertTrue(gatewayResponse.getHeaders().keySet().contains(CONTENT_TYPE));
        assertTrue(gatewayResponse.getHeaders().keySet().contains(ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("handler Returns Bad Request Response On Malformed Identifier")
    public void handlerReturnsBadRequestResponseOnMalformedIdentifier() throws IOException {

        createPresignedDownloadUrlHandler.handleRequest(inputStream(IDENTIFIER, IDENTIFIER_FILE_VALUE), output,
                context);

        GatewayResponse gatewayResponse = objectMapper.readValue(output.toString(), GatewayResponse.class);
        assertEquals(SC_BAD_REQUEST, gatewayResponse.getStatusCode());
        assertTrue(gatewayResponse.getHeaders().keySet().contains(CONTENT_TYPE));
        assertTrue(gatewayResponse.getHeaders().keySet().contains(ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("handler Returns Service Unavailable Response On No Response From Publication Service")
    public void handlerReturnsServiceUnavailableResponseOnNoResponseFromPublicationService() throws IOException,
            ApiGatewayException {

        when(publicationService.getPublicationWithAuthorizationToken(any(UUID.class), any(String.class)))
                .thenThrow(new NoResponseException(ERROR_COMMUNICATING_WITH_REMOTE_SERVICE,
                        new Exception()));
        createPresignedDownloadUrlHandler.handleRequest(inputStream(IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE),
                output, context);

        GatewayResponse gatewayResponse = objectMapper.readValue(output.toString(), GatewayResponse.class);
        assertEquals(SC_SERVICE_UNAVAILABLE, gatewayResponse.getStatusCode());
        assertTrue(gatewayResponse.getHeaders().keySet().contains(CONTENT_TYPE));
        assertTrue(gatewayResponse.getHeaders().keySet().contains(ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("handler Returns Not Found Response On Unknown File Identifier")
    public void handlerReturnsBadRequestResponseOnUnknownFileIdentifier() throws ApiGatewayException, IOException {
        Publication publication = createPublishedPublication(IDENTIFIER_VALUE, IDENTIFIER_VALUE);
        when(publicationService.getPublicationWithAuthorizationToken(any(UUID.class), anyString()))
                .thenReturn(publication);

        createPresignedDownloadUrlHandler.handleRequest(inputStream(IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE),
                output, context);

        GatewayResponse gatewayResponse = objectMapper.readValue(output.toString(), GatewayResponse.class);
        assertEquals(SC_NOT_FOUND, gatewayResponse.getStatusCode());
        assertTrue(gatewayResponse.getHeaders().keySet().contains(CONTENT_TYPE));
        assertTrue(gatewayResponse.getHeaders().keySet().contains(ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("handler Returns Internal Server Error Response On Duplicate File Identifier In Publication")
    public void handlerReturnsInternalServerErrorResponseOnDuplicateFileIdentifierInPublication()
            throws ApiGatewayException, IOException {
        Publication publication = createPublishedPublicationDuplicateFile(IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE);
        when(publicationService.getPublicationWithAuthorizationToken(any(UUID.class), anyString()))
                .thenReturn(publication);

        createPresignedDownloadUrlHandler.handleRequest(inputStream(IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE),
                output, context);

        GatewayResponse gatewayResponse = objectMapper.readValue(output.toString(), GatewayResponse.class);
        assertEquals(SC_INTERNAL_SERVER_ERROR, gatewayResponse.getStatusCode());
        assertTrue(gatewayResponse.getHeaders().keySet().contains(CONTENT_TYPE));
        assertTrue(gatewayResponse.getHeaders().keySet().contains(ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("handler Returns Not Found Response On Publication Without Files")
    public void handlerReturnsBadRequestResponseOnPublicationWithoutFile() throws IOException,
            ApiGatewayException {
        Publication publication = createPublicationWithoutFileSetFile(IDENTIFIER_VALUE);
        when(publicationService.getPublicationWithAuthorizationToken(any(UUID.class), anyString()))
                .thenReturn(publication);

        createPresignedDownloadUrlHandler.handleRequest(inputStream(IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE),
                output, context);

        GatewayResponse gatewayResponse = objectMapper.readValue(output.toString(), GatewayResponse.class);
        assertEquals(SC_NOT_FOUND, gatewayResponse.getStatusCode());
        assertTrue(gatewayResponse.getHeaders().keySet().contains(CONTENT_TYPE));
        assertTrue(gatewayResponse.getHeaders().keySet().contains(ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("handler Returns Bad Request Response When Missing Claims on Unpublished Publication")
    public void handlerReturnsBadRequestResponseWhenMissingClaimsUnpublishedPublication() throws IOException,
            ApiGatewayException {

        Publication publication = createUnpublishedPublication(IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE);

        when(publicationService.getPublicationWithAuthorizationToken(any(UUID.class), anyString()))
                .thenReturn(publication);

        createPresignedDownloadUrlHandler.handleRequest(inputStream(IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE),
                output, context);

        GatewayResponse gatewayResponse = objectMapper.readValue(output.toString(), GatewayResponse.class);
        assertEquals(SC_BAD_REQUEST, gatewayResponse.getStatusCode());
        assertTrue(gatewayResponse.getHeaders().keySet().contains(CONTENT_TYPE));
        assertTrue(gatewayResponse.getHeaders().keySet().contains(ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("handler Returns Unauthorized Response When Unpublished Publication And User Is Not Owner")
    public void handlerReturnsUnauthorizedResponseWhenUnpublishedPublicationAndNotOwner() throws IOException,
            ApiGatewayException {

        Publication publication = createUnpublishedPublication(IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE);

        when(publicationService.getPublicationWithAuthorizationToken(any(UUID.class), anyString()))
                .thenReturn(publication);

        createPresignedDownloadUrlHandler.handleRequest(inputStream(IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE,
                NOT_OWNER_USER_ID), output, context);

        GatewayResponse gatewayResponse = objectMapper.readValue(output.toString(), GatewayResponse.class);
        assertEquals(SC_UNAUTHORIZED, gatewayResponse.getStatusCode());
        assertTrue(gatewayResponse.getHeaders().keySet().contains(CONTENT_TYPE));
        assertTrue(gatewayResponse.getHeaders().keySet().contains(ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("handler Returns Service Unavailable Response on S3 Exception")
    public void handlerReturnsServiceUnavailableResponseOnS3ServiceException() throws IOException,
            ApiGatewayException {
        Publication publication = createPublishedPublication(IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE);
        when(publicationService.getPublicationWithAuthorizationToken(any(UUID.class), anyString()))
                .thenReturn(publication);
        when(awsS3Service.createPresignedDownloadUrl(IDENTIFIER_FILE_VALUE, MIME_TYPE_APPLICATION_PDF))
                .thenThrow(new S3ServiceException("message", new SdkClientException("message")));

        createPresignedDownloadUrlHandler.handleRequest(inputStream(IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE),
                output, context);

        GatewayResponse gatewayResponse = objectMapper.readValue(output.toString(), GatewayResponse.class);
        assertEquals(SC_SERVICE_UNAVAILABLE, gatewayResponse.getStatusCode());
        assertTrue(gatewayResponse.getHeaders().keySet().contains(CONTENT_TYPE));
        assertTrue(gatewayResponse.getHeaders().keySet().contains(ACCESS_CONTROL_ALLOW_ORIGIN));
    }



    private Publication createPublicationWithoutFileSetFile(String identifier) {
        return new Publication.Builder()
                .withIdentifier(UUID.fromString(identifier))
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
                .withIdentifier(UUID.fromString(identifier))
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
                .withIdentifier(UUID.fromString(identifier))
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
                .withIdentifier(UUID.fromString(identifier))
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
