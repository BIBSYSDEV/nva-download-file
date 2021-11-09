package no.unit.nva.download.publication.file;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.kms.model.NotFoundException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.s3.AmazonS3;
import com.fasterxml.jackson.core.JsonProcessingException;
import no.unit.nva.download.publication.file.aws.s3.AwsS3Service;
import no.unit.nva.download.publication.file.publication.PublicationBuilder;
import no.unit.nva.download.publication.file.publication.PublicationStatus;
import no.unit.nva.download.publication.file.publication.RestPublicationService;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.GatewayResponse;
import nva.commons.core.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.stubbing.Answer;
import org.zalando.problem.Problem;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static com.google.common.net.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static no.unit.nva.download.publication.file.CreatePresignedDownloadUrlHandler.ERROR_MISSING_FILE_IN_PUBLICATION_FILE_SET;
import static no.unit.nva.download.publication.file.RequestUtil.FILE_IDENTIFIER;
import static no.unit.nva.download.publication.file.RequestUtil.IDENTIFIER_IS_NOT_A_VALID_UUID;
import static no.unit.nva.download.publication.file.RequestUtil.MISSING_FILE_IDENTIFIER;
import static no.unit.nva.download.publication.file.RequestUtil.MISSING_RESOURCE_IDENTIFIER;
import static no.unit.nva.download.publication.file.exception.NotFoundException.RESOURCE_NOT_FOUND;
import static no.unit.nva.download.publication.file.publication.PublicationBuilder.APPLICATION_PDF;
import static no.unit.nva.download.publication.file.publication.PublicationStatus.DRAFT;
import static no.unit.nva.download.publication.file.publication.PublicationStatus.PUBLISHED;
import static no.unit.nva.download.publication.file.publication.RestPublicationService.ERROR_COMMUNICATING_WITH_REMOTE_SERVICE;
import static no.unit.nva.download.publication.file.publication.RestPublicationService.ERROR_PUBLICATION_NOT_FOUND_FOR_IDENTIFIER;
import static no.unit.nva.download.publication.file.publication.RestPublicationService.EXTERNAL_ERROR_MESSAGE_DECORATION;
import static nva.commons.apigateway.ApiGatewayHandler.ALLOWED_ORIGIN_ENV;
import static nva.commons.core.JsonUtils.dtoObjectMapper;
import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_SERVICE_UNAVAILABLE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.zalando.problem.Status.BAD_REQUEST;
import static org.zalando.problem.Status.INTERNAL_SERVER_ERROR;
import static org.zalando.problem.Status.NOT_FOUND;
import static org.zalando.problem.Status.SERVICE_UNAVAILABLE;

public class CreatePresignedDownloadUrlHandlerTest {

    public static final String SOME_API_KEY = "some api key";
    public static final String IDENTIFIER = "identifier";
    public static final String IDENTIFIER_FILE = "fileIdentifier";
    public static final String IDENTIFIER_VALUE = "29f6887c-8852-11ea-bc55-0242ac130003";
    public static final String IDENTIFIER_FILE_VALUE = "29f68c1e-8852-11ea-bc55-0242ac130003";
    public static final String OWNER_USER_ID = "owner@unit.no";
    public static final String NON_ONWER = "non.owner@unit.no";
    public static final String ANY_BUCKET = "aBucket";
    public static final String APPLICATION_PROBLEM_JSON = "application/problem+json";
    public static final String PRESIGNED_DOWNLOAD_URL = "https://example.com/download/12345";
    public static final String API_HOST = "example.org";
    public static final String API_SCHEME = "http";
    public static final String OWNER_ORGANIZATION = "https://example.com/customer/1";
    private static final String APPLICATION_JSON = "application/json; charset=utf-8";
    public static final String ANY_ORIGIN = "*";
    public static final String SOME_RANDOM_IDENTIFIER = UUID.randomUUID().toString();
    public static final String UNKNOWN_FILE_ID = UUID.randomUUID().toString();
    public static final String NOT_A_UUID = "not-a-UUID";
    public static final String EASY_TO_SEE = "Easy-to-see: ";

    private HttpClient httpClient;
    private Context context;
    private ByteArrayOutputStream output;

    private static Stream<String> userSupplier() {
        return Stream.of(
                OWNER_USER_ID,
                null,
                NON_ONWER
        );
    }

    private static Stream<String> mimeTypeProvider() {
        return Stream.of(
                APPLICATION_PDF,
                null
        );
    }

    private static Stream<Arguments> badRequestProvider() throws IOException {
        return Stream.of(
                Arguments.of(createBadRequestNoIdentifier(), MISSING_RESOURCE_IDENTIFIER),
                Arguments.of(createBadRequestNoFileIdentifier(), MISSING_FILE_IDENTIFIER),
                Arguments.of(createBadRequestNonUuidFileIdentifier(), IDENTIFIER_IS_NOT_A_VALID_UUID + NOT_A_UUID)
        );
    }

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        context = mock(Context.class);
        output = new ByteArrayOutputStream();
    }

    @Test
    void shouldThrowExceptionWhenEnvironmentVariablesAreUnset() {
        assertThrows(IllegalStateException.class, CreatePresignedDownloadUrlHandler::new);
    }

    @ParameterizedTest(name = "Published publication is downloadable by user {0}")
    @MethodSource("userSupplier")
    void handlerReturnsOkResponseOnValidInputPublishedPublication(String user) throws IOException,
            InterruptedException {
        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();
        var publicationService = mockSuccessfulPublicationRequest(
                getPublication(PUBLISHED, IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE));
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());

        handler.handleRequest(createRequest(user, IDENTIFIER_VALUE), output, context);

        GatewayResponse<PresignedUriResponse> gatewayResponse = GatewayResponse.fromString(output.toString());
        assertBasicRestRequirements(gatewayResponse, SC_OK, APPLICATION_JSON);
        assertPresignedUriIsPresent(gatewayResponse);
    }

    @Test
    void shouldReturnOkWhenPublicationUnpublishedAndUserIsOwner() throws IOException, InterruptedException {
        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();
        var publicationService = mockSuccessfulPublicationRequest(
                getPublication(PublicationStatus.DRAFT, IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE));
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());

        handler.handleRequest(createRequest(OWNER_USER_ID, IDENTIFIER_VALUE), output, context);

        GatewayResponse<PresignedUriResponse> gatewayResponse = GatewayResponse.fromString(output.toString());
        assertBasicRestRequirements(gatewayResponse, SC_OK, APPLICATION_JSON);
        assertPresignedUriIsPresent(gatewayResponse);
    }

    @ParameterizedTest(name = "Should return presigned URI when mime-type of file is {0}")
    @MethodSource("mimeTypeProvider")
    void shouldReturnOkWhenPublicationUnpublishedAndUserIsOwnerAndMimeTypeIs(String mimeType) throws IOException,
            InterruptedException {
        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();
        var publicationService = mockSuccessfulPublicationRequest(
                getPublication(PublicationStatus.DRAFT, IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE, mimeType));
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());

        handler.handleRequest(createRequest(OWNER_USER_ID, IDENTIFIER_VALUE), output, context);

        GatewayResponse<PresignedUriResponse> gatewayResponse = GatewayResponse.fromString(output.toString());
        assertBasicRestRequirements(gatewayResponse, SC_OK, APPLICATION_JSON);
        assertPresignedUriIsPresent(gatewayResponse);
    }

    // Error message here is odd
    @Test
    void handlerReturnsNotFoundResponseOnUnknownIdentifier() throws IOException, InterruptedException {
        var publicationService = mockNotFoundPublicationService();
        var handler = new CreatePresignedDownloadUrlHandler(publicationService,
                getAwsS3ServiceReturningPresignedUrl(), mockEnvironment());

        handler.handleRequest(createRequest(OWNER_USER_ID, SOME_RANDOM_IDENTIFIER), output, context);

        GatewayResponse<Problem> gatewayResponse = GatewayResponse.fromOutputStream(output);
        assertBasicRestRequirements(gatewayResponse, SC_NOT_FOUND, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse,
                getNotFoundPublicationServiceResponse(EXTERNAL_ERROR_MESSAGE_DECORATION + SOME_RANDOM_IDENTIFIER
                        + " " + EASY_TO_SEE + IDENTIFIER_VALUE));
    }

    @Test
    void handlerReturnsServiceUnavailableResponseOnServerErrorResponseFromPublicationService() throws IOException,
            InterruptedException {
        var publicationService = mockUnresponsivePublicationService();
        var handler = new CreatePresignedDownloadUrlHandler(publicationService,
                getAwsS3ServiceReturningPresignedUrl(), mockEnvironment());

        handler.handleRequest(createRequest(OWNER_USER_ID, SOME_RANDOM_IDENTIFIER), output, context);

        GatewayResponse<Problem> gatewayResponse = GatewayResponse.fromOutputStream(output);
        assertBasicRestRequirements(gatewayResponse, SC_SERVICE_UNAVAILABLE, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse, getServiceUnavailableProblem(SOME_RANDOM_IDENTIFIER));
    }

    private void assertProblemEquivalence(GatewayResponse<Problem> gatewayResponse, Problem expected)
            throws JsonProcessingException {
        var actual = gatewayResponse.getBodyObject(Problem.class);
        assertThat(actual.getStatus(), equalTo(expected.getStatus()));
        assertThat(actual.getTitle(), equalTo(expected.getTitle()));
        assertThat(actual.getDetail(), equalTo(expected.getDetail()));
    }

    @Test
    void shouldReturnNotFoundResponseOnUnknownFileIdentifier() throws IOException, InterruptedException {
        var publicationService = mockSuccessfulPublicationRequest(
                getPublication(PUBLISHED, IDENTIFIER_VALUE, UNKNOWN_FILE_ID));

        var handler = new CreatePresignedDownloadUrlHandler(publicationService, getAwsS3ServiceReturningNotFound(),
                mockEnvironment());
        handler.handleRequest(createRequest(OWNER_USER_ID, IDENTIFIER_VALUE), output, context);

        GatewayResponse<Problem> gatewayResponse = GatewayResponse.fromOutputStream(output);
        assertBasicRestRequirements(gatewayResponse, SC_NOT_FOUND, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse,
                getNotFoundPublicationServiceResponse(ERROR_MISSING_FILE_IN_PUBLICATION_FILE_SET));
    }

    @Test
    void shouldReturnInternalServerErrorResponseOnDuplicateFileIdentifierInPublication() throws IOException,
            InterruptedException {
        var publication = getPublicationWithDuplicateFileInFileSet(PUBLISHED, IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE);
        var publicationService = mockSuccessfulPublicationRequest(publication);
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, getAwsS3ServiceReturningNotFound(),
                mockEnvironment());

        handler.handleRequest(createRequest(OWNER_USER_ID, IDENTIFIER_VALUE), output, context);

        GatewayResponse<Problem> gatewayResponse = GatewayResponse.fromOutputStream(output);
        assertBasicRestRequirements(gatewayResponse, SC_INTERNAL_SERVER_ERROR, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse, getInternalServerError());
    }

    @Test
    void handlerReturnsBadRequestResponseOnPublicationWithoutFile() throws IOException, InterruptedException {
        var publication = createPublicationWithoutFileSetFile(PUBLISHED, IDENTIFIER_VALUE);
        var publicationService = mockSuccessfulPublicationRequest(publication);
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, getAwsS3ServiceReturningNotFound(),
                mockEnvironment());

        handler.handleRequest(createRequest(OWNER_USER_ID, IDENTIFIER_VALUE), output, context);

        GatewayResponse<Problem> gatewayResponse = GatewayResponse.fromOutputStream(output);
        assertBasicRestRequirements(gatewayResponse, SC_NOT_FOUND, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse,
                getNotFoundPublicationServiceResponse(ERROR_MISSING_FILE_IN_PUBLICATION_FILE_SET));
    }


    @Test
    void shouldReturnServiceUnavailableResponseOnS3ServiceException() throws IOException, InterruptedException {
        AwsS3Service s3Service = getS3ServiceThrowingSdkClientException(IDENTIFIER_VALUE);
        var publicationService = mockSuccessfulPublicationRequest(
                getPublication(PUBLISHED, IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE));
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());

        handler.handleRequest(createRequest(OWNER_USER_ID, IDENTIFIER_VALUE), output, context);

        GatewayResponse<Problem> gatewayResponse = GatewayResponse.fromOutputStream(output);
        assertBasicRestRequirements(gatewayResponse, SC_SERVICE_UNAVAILABLE, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse, getServiceUnavailableProblem(IDENTIFIER_VALUE));
    }

    private AwsS3Service getS3ServiceThrowingSdkClientException(String identifier) {
        var amazonS3 = mock(AmazonS3.class);
        when(amazonS3.generatePresignedUrl(any()))
                .thenThrow(new SdkClientException(ERROR_COMMUNICATING_WITH_REMOTE_SERVICE
                        + "http://example.org/publication/"
                        + identifier));
        return new AwsS3Service(amazonS3, ANY_BUCKET);
    }

    @Test
    void shouldReturnNotFoundOnAnonymousRequestForDraftPublication()
            throws IOException, InterruptedException {
        var publicationService = mockSuccessfulPublicationRequest(
                getPublication(DRAFT, IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE));
        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();

        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());

        handler.handleRequest(createAnonymousRequest(IDENTIFIER_VALUE), output, context);

        GatewayResponse<Problem> gatewayResponse = GatewayResponse.fromOutputStream(output);
        assertBasicRestRequirements(gatewayResponse, SC_NOT_FOUND, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse,
                getNotFoundPublicationServiceResponse(RESOURCE_NOT_FOUND + IDENTIFIER_VALUE));
    }

    @ParameterizedTest
    @MethodSource("badRequestProvider")
    void shouldReturnBadRequestWhenRequestIsBad(InputStream request, String detail) throws IOException,
            InterruptedException {
        var publicationService = mockSuccessfulPublicationRequest(
                getPublication(DRAFT, IDENTIFIER_VALUE, IDENTIFIER_FILE_VALUE));
        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();

        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());

        handler.handleRequest(request, output, context);

        GatewayResponse<Problem> gatewayResponse = GatewayResponse.fromOutputStream(output);
        assertBasicRestRequirements(gatewayResponse, SC_BAD_REQUEST, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse, getBadRequestPublicationServiceResponse(detail));
    }

    @Test
    void shouldReturnNotFoundWhenPublicationServiceResponseIsNotUnderstood() throws IOException, InterruptedException {
        var publicationService = mockPublicationServiceReturningStrangeResponse();
        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();

        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());

        handler.handleRequest(createAnonymousRequest(IDENTIFIER_VALUE), output, context);

        GatewayResponse<Problem> gatewayResponse = GatewayResponse.fromOutputStream(output);
        assertBasicRestRequirements(gatewayResponse, SC_NOT_FOUND, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse,
                getNotFoundPublicationServiceResponse(EXTERNAL_ERROR_MESSAGE_DECORATION + IDENTIFIER_VALUE
                        + " " + ERROR_PUBLICATION_NOT_FOUND_FOR_IDENTIFIER + IDENTIFIER_VALUE));
    }

    @Test
    void shouldReturnNotFoundWhenPublicationServiceResponseIsUnderstood() throws IOException, InterruptedException {
        var publicationService = mockNotFoundPublicationService();
        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();

        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());

        handler.handleRequest(createAnonymousRequest(IDENTIFIER_VALUE), output, context);

        GatewayResponse<Problem> gatewayResponse = GatewayResponse.fromOutputStream(output);
        assertBasicRestRequirements(gatewayResponse, SC_NOT_FOUND, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse,
                getNotFoundPublicationServiceResponse(EXTERNAL_ERROR_MESSAGE_DECORATION + IDENTIFIER_VALUE
                        + " " + EASY_TO_SEE + IDENTIFIER_VALUE));
    }

    private RestPublicationService mockPublicationServiceReturningStrangeResponse() throws IOException,
            InterruptedException {
        @SuppressWarnings("unchecked")
        var response = (HttpResponse<String>) mock(HttpResponse.class);
        when(response.statusCode()).thenAnswer(i -> 404);
        when(response.body()).thenAnswer(i -> getStrangeResponse());
        when(httpClient.<String>send(any(), any())).thenAnswer((Answer<HttpResponse<String>>) invocation -> response);
        return new RestPublicationService(httpClient, dtoObjectMapper, API_SCHEME, API_HOST);
    }

    private String getStrangeResponse() {
        return "Cowboys code with strings";
    }

    private Problem getBadRequestPublicationServiceResponse(String detail) {
        return Problem.builder()
                .withStatus(BAD_REQUEST)
                .withTitle(BAD_REQUEST.getReasonPhrase())
                .withDetail(detail)
                .build();
    }

    private String createPublicationWithoutFileSetFile(PublicationStatus publicationStatus, String identifier) {
        return new PublicationBuilder("publication_with_no_files_template.json")
                .withStatus(publicationStatus)
                .withOwner(OWNER_USER_ID)
                .withOrganization(OWNER_ORGANIZATION)
                .withIdentifier(identifier)
                .withFileIdentifier(null)
                .withMimeType(APPLICATION_PDF)
                .build();
    }

    private Problem getInternalServerError() {
        return Problem.builder()
                .withStatus(INTERNAL_SERVER_ERROR)
                .withTitle(INTERNAL_SERVER_ERROR.getReasonPhrase())
                .withDetail("Internal server error. Contact application administrator.")
                .build();
    }

    private String getPublicationWithDuplicateFileInFileSet(PublicationStatus publicationStatus,
                                                            String identifier,
                                                            String fileIdentifier) {
        return new PublicationBuilder("publication_with_duplicate_file_template.json")
                .withStatus(publicationStatus)
                .withOwner(OWNER_USER_ID)
                .withOrganization(OWNER_ORGANIZATION)
                .withIdentifier(identifier)
                .withFileIdentifier(fileIdentifier)
                .withMimeType(APPLICATION_PDF)
                .build();
    }

    private AwsS3Service getAwsS3ServiceReturningNotFound() {
        var amazonS3 = mock(AmazonS3.class);
        when(amazonS3.generatePresignedUrl(any())).thenThrow(new NotFoundException("Not Found"));
        return new AwsS3Service(amazonS3, ANY_BUCKET);
    }

    private Problem getServiceUnavailableProblem(String identifier) throws JsonProcessingException {
        return Problem.builder()
                .withStatus(SERVICE_UNAVAILABLE)
                .withTitle(SERVICE_UNAVAILABLE.getReasonPhrase())
                .withDetail("Error communicating with remote service: http://example.org/publication/" + identifier)
                .build();
    }

    private RestPublicationService mockUnresponsivePublicationService() throws IOException, InterruptedException {
        var publicationService = new RestPublicationService(httpClient, dtoObjectMapper, API_SCHEME, API_HOST);
        @SuppressWarnings("unchecked")
        var response = (HttpResponse<String>) mock(HttpResponse.class);
        when(response.statusCode()).thenAnswer(i -> 500);
        when(httpClient.<String>send(any(), any())).thenAnswer((Answer<HttpResponse<String>>) invocation -> response);
        return publicationService;
    }

    private RestPublicationService mockNotFoundPublicationService() throws IOException, InterruptedException {
        @SuppressWarnings("unchecked")
        var response = (HttpResponse<String>) mock(HttpResponse.class);
        when(response.statusCode()).thenAnswer(i -> 404);
        when(response.body()).thenAnswer(i -> notFoundProblem());
        when(httpClient.<String>send(any(), any())).thenAnswer((Answer<HttpResponse<String>>) invocation -> response);
        return new RestPublicationService(httpClient, dtoObjectMapper, API_SCHEME, API_HOST);
    }

    private String notFoundProblem() throws JsonProcessingException {
        return dtoObjectMapper
                .writeValueAsString(getNotFoundPublicationServiceResponse(EASY_TO_SEE + IDENTIFIER_VALUE));
    }


    private Problem getNotFoundPublicationServiceResponse(String message) {
        return Problem.builder()
                .withStatus(NOT_FOUND)
                .withTitle(NOT_FOUND.getReasonPhrase())
                .withDetail(message)
                .build();
    }

    private String getPublication(PublicationStatus publicationStatus,
                                  String identifier,
                                  String fileIdentifier) {
        return new PublicationBuilder("publication_template.json")
                .withStatus(publicationStatus)
                .withOwner(OWNER_USER_ID)
                .withOrganization(OWNER_ORGANIZATION)
                .withIdentifier(identifier)
                .withFileIdentifier(fileIdentifier)
                .withMimeType(APPLICATION_PDF)
                .build();
    }

    private String getPublication(PublicationStatus publicationStatus,
                                  String identifier,
                                  String fileIdentifier,
                                  String mimeType) {
        return new PublicationBuilder("publication_template.json")
                .withStatus(publicationStatus)
                .withOwner(OWNER_USER_ID)
                .withOrganization(OWNER_ORGANIZATION)
                .withIdentifier(identifier)
                .withFileIdentifier(fileIdentifier)
                .withMimeType(mimeType)
                .build();
    }

    private void assertBasicRestRequirements(GatewayResponse<?> gatewayResponse,
                                             int expectedStatusCode,
                                             String expectedContentType) {
        assertThat(gatewayResponse.getStatusCode(), equalTo(expectedStatusCode));
        assertTrue(gatewayResponse.getHeaders().containsKey(CONTENT_TYPE));
        assertThat(gatewayResponse.getHeaders().get(CONTENT_TYPE), equalTo(expectedContentType));
        assertTrue(gatewayResponse.getHeaders().containsKey(ACCESS_CONTROL_ALLOW_ORIGIN));
        assertThat(gatewayResponse.getHeaders().get(ACCESS_CONTROL_ALLOW_ORIGIN), equalTo(ANY_ORIGIN));
    }

    private void assertPresignedUriIsPresent(GatewayResponse<PresignedUriResponse> gatewayResponse) throws
            JsonProcessingException {
        var presignedDownloadUrl = gatewayResponse.getBodyObject(PresignedUriResponse.class).getPresignedDownloadUrl();
        assertThat(presignedDownloadUrl, is(notNullValue()));
    }

    private AwsS3Service getAwsS3ServiceReturningPresignedUrl() throws MalformedURLException {
        var amazonS3 = mock(AmazonS3.class);
        when(amazonS3.generatePresignedUrl(any())).thenReturn(new URL(PRESIGNED_DOWNLOAD_URL));
        return new AwsS3Service(amazonS3, ANY_BUCKET);
    }

    private Environment mockEnvironment() {
        Environment environment = mock(Environment.class);
        when(environment.readEnv(ALLOWED_ORIGIN_ENV)).thenReturn(ANY_ORIGIN);
        return environment;
    }

    private RestPublicationService mockSuccessfulPublicationRequest(String responseBody)
            throws IOException, InterruptedException {
        var publicationService = new RestPublicationService(httpClient, dtoObjectMapper, API_SCHEME, API_HOST);
        @SuppressWarnings("unchecked")
        var response = (HttpResponse<String>) mock(HttpResponse.class);
        when((response.body())).thenAnswer(i -> responseBody);
        when(httpClient.<String>send(any(), any())).thenAnswer((Answer<HttpResponse<String>>) invocation -> response);
        return publicationService;
    }

    private InputStream createRequest(String user, String identifier) throws IOException {
        return new HandlerRequestBuilder<Void>(dtoObjectMapper)
                .withHeaders(Map.of(AUTHORIZATION, SOME_API_KEY))
                .withFeideId(user)
                .withPathParameters(Map.of(IDENTIFIER, identifier,
                        IDENTIFIER_FILE, IDENTIFIER_FILE_VALUE))
                .build();
    }

    private InputStream createAnonymousRequest(String identifier) throws IOException {
        return new HandlerRequestBuilder<Void>(dtoObjectMapper)
                .withPathParameters(Map.of(IDENTIFIER, identifier,
                        IDENTIFIER_FILE, IDENTIFIER_FILE_VALUE))
                .build();
    }

    private static InputStream createBadRequestNoIdentifier() throws IOException {
        return new HandlerRequestBuilder<Void>(dtoObjectMapper)
                .withPathParameters(Map.of(IDENTIFIER_FILE, IDENTIFIER_FILE_VALUE))
                .build();
    }

    private static InputStream createBadRequestNoFileIdentifier() throws JsonProcessingException {
        return new HandlerRequestBuilder<Void>(dtoObjectMapper)
                .withPathParameters(Map.of(IDENTIFIER, IDENTIFIER_VALUE))
                .build();
    }

    private static InputStream createBadRequestNonUuidFileIdentifier() throws JsonProcessingException {
        return new HandlerRequestBuilder<Void>(dtoObjectMapper)
                .withPathParameters(Map.of(IDENTIFIER, IDENTIFIER_VALUE, FILE_IDENTIFIER, NOT_A_UUID))
                .build();
    }
}
