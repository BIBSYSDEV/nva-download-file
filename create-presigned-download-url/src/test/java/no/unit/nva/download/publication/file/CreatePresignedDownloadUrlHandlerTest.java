package no.unit.nva.download.publication.file;

import com.amazonaws.SdkClientException;
import com.amazonaws.services.kms.model.NotFoundException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.s3.AmazonS3;
import com.fasterxml.jackson.core.JsonProcessingException;
import no.unit.nva.download.publication.file.aws.s3.AwsS3Service;
import no.unit.nva.download.publication.file.publication.Event;
import no.unit.nva.download.publication.file.publication.PublicationStatus;
import no.unit.nva.download.publication.file.publication.RestPublicationService;
import no.unit.nva.file.model.File;
import no.unit.nva.file.model.FileSet;
import no.unit.nva.file.model.License;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.GatewayResponse;
import nva.commons.core.Environment;
import org.apache.http.HttpStatus;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static no.unit.nva.download.publication.file.RequestUtil.IDENTIFIER_IS_NOT_A_VALID_UUID;
import static no.unit.nva.download.publication.file.RequestUtil.MISSING_FILE_IDENTIFIER;
import static no.unit.nva.download.publication.file.RequestUtil.MISSING_RESOURCE_IDENTIFIER;
import static no.unit.nva.download.publication.file.exception.NotFoundException.ERROR_TEMPLATE;
import static no.unit.nva.download.publication.file.publication.PublicationStatus.DRAFT;
import static no.unit.nva.download.publication.file.publication.PublicationStatus.PUBLISHED;
import static no.unit.nva.download.publication.file.publication.RestPublicationService.ERROR_COMMUNICATING_WITH_REMOTE_SERVICE;
import static no.unit.nva.download.publication.file.publication.RestPublicationService.ERROR_PUBLICATION_NOT_FOUND_FOR_IDENTIFIER;
import static no.unit.nva.download.publication.file.publication.RestPublicationService.EXTERNAL_ERROR_MESSAGE_DECORATION;
import static no.unit.nva.testutils.TestHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static nva.commons.apigateway.ApiGatewayHandler.ALLOWED_ORIGIN_ENV;
import static nva.commons.core.JsonUtils.dtoObjectMapper;
import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.apache.http.HttpStatus.SC_BAD_GATEWAY;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
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
import static org.zalando.problem.Status.BAD_GATEWAY;
import static org.zalando.problem.Status.BAD_REQUEST;
import static org.zalando.problem.Status.NOT_FOUND;
import static org.zalando.problem.Status.SERVICE_UNAVAILABLE;

public class CreatePresignedDownloadUrlHandlerTest {

    public static final String SOME_API_KEY = "some api key";
    public static final String IDENTIFIER = "identifier";
    public static final String IDENTIFIER_FILE = "fileIdentifier";
    public static final String OWNER_USER_ID = "owner@unit.no";
    public static final String NON_ONWER = "non.owner@unit.no";
    public static final String ANY_BUCKET = "aBucket";
    public static final String APPLICATION_PROBLEM_JSON = "application/problem+json";
    public static final String PRESIGNED_DOWNLOAD_URL = "https://example.com/download/12345";
    public static final String API_HOST = "example.org";
    public static final String API_SCHEME = "https";
    public static final UUID PUBLICATION_IDENTIFIER = UUID.randomUUID();
    public static final UUID FILE_IDENTIFIER = UUID.randomUUID();
    private static final String APPLICATION_JSON = "application/json; charset=utf-8";
    public static final String ANY_ORIGIN = "*";
    public static final UUID SOME_RANDOM_IDENTIFIER = UUID.randomUUID();
    public static final String NOT_A_UUID = "not-a-UUID";
    public static final String EASY_TO_SEE = "Easy-to-see: ";
    public static final String HTTP_EXAMPLE_ORG_PUBLICATION = "https://example.org/publication/";
    private static final String APPLICATION_PDF = "application/pdf";

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

    private static Stream<InputStream> nonOwnerProvider() throws IOException {
        return Stream.of(
                createAnonymousRequest(),
                createNonOwnerRequest()
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
                getPublication(PUBLISHED));
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());

        handler.handleRequest(createRequest(user, PUBLICATION_IDENTIFIER, FILE_IDENTIFIER), output, context);

        GatewayResponse<PresignedUri> gatewayResponse = GatewayResponse.fromString(output.toString());
        assertBasicRestRequirements(gatewayResponse, SC_OK, APPLICATION_JSON);
        assertExpectedResponseBody(gatewayResponse);
    }

    @Test
    void shouldReturnOkWhenPublicationUnpublishedAndUserIsOwner() throws IOException, InterruptedException {
        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();
        var publicationService = mockSuccessfulPublicationRequest(
                getPublication(DRAFT));
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());

        handler.handleRequest(createRequest(OWNER_USER_ID, PUBLICATION_IDENTIFIER, FILE_IDENTIFIER), output, context);

        GatewayResponse<PresignedUri> gatewayResponse = GatewayResponse.fromString(output.toString());
        assertBasicRestRequirements(gatewayResponse, SC_OK, APPLICATION_JSON);
        assertExpectedResponseBody(gatewayResponse);
    }

    @Test
    void shouldReturnBadGatewayWhenPublicationCanNotBeParsed() throws IOException, InterruptedException {
        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();
        var publicationService = mockSuccessfulPublicationRequest("<</>");
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());

        handler.handleRequest(createRequest(OWNER_USER_ID, PUBLICATION_IDENTIFIER, FILE_IDENTIFIER), output, context);

        GatewayResponse<PresignedUri> gatewayResponse = GatewayResponse.fromString(output.toString());
        assertBasicRestRequirements(gatewayResponse, HttpStatus.SC_BAD_GATEWAY, APPLICATION_PROBLEM_JSON);
    }

    @ParameterizedTest(name = "Should return presigned URI when mime-type of file is {0}")
    @MethodSource("mimeTypeProvider")
    void shouldReturnOkWhenPublicationUnpublishedAndUserIsOwnerAndMimeTypeIs(String mimeType) throws IOException,
            InterruptedException {
        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();
        var publicationService = mockSuccessfulPublicationRequest(
                getPublication(DRAFT, mimeType));
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());

        handler.handleRequest(createRequest(OWNER_USER_ID, PUBLICATION_IDENTIFIER, FILE_IDENTIFIER), output, context);

        GatewayResponse<PresignedUri> gatewayResponse = GatewayResponse.fromString(output.toString());
        assertBasicRestRequirements(gatewayResponse, SC_OK, APPLICATION_JSON);
        assertExpectedResponseBody(gatewayResponse);
    }

    // Error message here is odd
    @Test
    void handlerReturnsNotFoundResponseOnUnknownIdentifier() throws IOException, InterruptedException {
        var publicationService = mockNotFoundPublicationService();
        var handler = new CreatePresignedDownloadUrlHandler(publicationService,
                getAwsS3ServiceReturningPresignedUrl(), mockEnvironment());

        handler.handleRequest(createRequest(OWNER_USER_ID, PUBLICATION_IDENTIFIER, FILE_IDENTIFIER), output, context);

        GatewayResponse<Problem> gatewayResponse = GatewayResponse.fromOutputStream(output);
        assertBasicRestRequirements(gatewayResponse, SC_NOT_FOUND, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse,
                getNotFoundPublicationServiceResponse(EXTERNAL_ERROR_MESSAGE_DECORATION + PUBLICATION_IDENTIFIER
                        + " " + EASY_TO_SEE + PUBLICATION_IDENTIFIER));
    }

    @Test
    void handlerReturnsServiceUnavailableResponseOnServerErrorResponseFromPublicationService() throws IOException,
            InterruptedException {
        var publicationService = mockUnresponsivePublicationService();
        var handler = new CreatePresignedDownloadUrlHandler(publicationService,
                getAwsS3ServiceReturningPresignedUrl(), mockEnvironment());

        handler.handleRequest(createRequest(OWNER_USER_ID, SOME_RANDOM_IDENTIFIER, FILE_IDENTIFIER), output, context);

        GatewayResponse<Problem> gatewayResponse = GatewayResponse.fromOutputStream(output);
        assertBasicRestRequirements(gatewayResponse, SC_BAD_GATEWAY, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse, getBadGatewayProblem(SOME_RANDOM_IDENTIFIER));
    }

    private Problem getBadGatewayProblem(UUID identifier) {
        return Problem.builder()
                .withStatus(BAD_GATEWAY)
                .withTitle(BAD_GATEWAY.getReasonPhrase())
                .withDetail(ERROR_COMMUNICATING_WITH_REMOTE_SERVICE
                        + HTTP_EXAMPLE_ORG_PUBLICATION
                        + identifier.toString())
                .build();
    }

    @Test
    void shouldReturnNotFoundResponseOnUnknownFileIdentifier() {
    }

    @Test
    void handlerReturnsNotFoundOnPublicationWithoutFile() throws IOException, InterruptedException {
        var publication = createPublishedPublicationWithoutFileSetFile();
        var publicationService = mockSuccessfulPublicationRequest(publication);
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, getAwsS3ServiceReturningNotFound(),
                mockEnvironment());

        handler.handleRequest(createRequest(OWNER_USER_ID, PUBLICATION_IDENTIFIER, SOME_RANDOM_IDENTIFIER),
                output, context);

        GatewayResponse<Problem> gatewayResponse = GatewayResponse.fromOutputStream(output);
        assertBasicRestRequirements(gatewayResponse, SC_NOT_FOUND, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse,
                getNotFoundPublicationServiceResponse(
                        notFoundError(SOME_RANDOM_IDENTIFIER)));
    }

    @Test
    void shouldReturnServiceUnavailableResponseOnS3ServiceException() throws IOException, InterruptedException {
        AwsS3Service s3Service = getS3ServiceThrowingSdkClientException();
        var publicationService = mockSuccessfulPublicationRequest(
                getPublication(PUBLISHED));
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());

        handler.handleRequest(createRequest(OWNER_USER_ID, PUBLICATION_IDENTIFIER, FILE_IDENTIFIER), output, context);

        GatewayResponse<Problem> gatewayResponse = GatewayResponse.fromOutputStream(output);
        assertBasicRestRequirements(gatewayResponse, SC_BAD_GATEWAY, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse, getBadGatewayProblem(PUBLICATION_IDENTIFIER));
    }

    @Test
    void shouldReturnNotFoundOnAnonymousRequestForDraftPublication()
            throws IOException, InterruptedException {
        var publicationService = mockSuccessfulPublicationRequest(
                getPublication(DRAFT));
        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();

        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());

        handler.handleRequest(createAnonymousRequest(), output, context);

        GatewayResponse<Problem> gatewayResponse = GatewayResponse.fromOutputStream(output);
        assertBasicRestRequirements(gatewayResponse, SC_NOT_FOUND, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse,
                getNotFoundPublicationServiceResponse(notFoundError(FILE_IDENTIFIER)));
    }

    @ParameterizedTest
    @MethodSource("badRequestProvider")
    void shouldReturnBadRequestWhenRequestIsBad(InputStream request, String detail) throws IOException,
            InterruptedException {
        var publicationService = mockSuccessfulPublicationRequest(
                getPublication(DRAFT));
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

        handler.handleRequest(createAnonymousRequest(), output, context);

        GatewayResponse<Problem> gatewayResponse = GatewayResponse.fromOutputStream(output);
        assertBasicRestRequirements(gatewayResponse, SC_NOT_FOUND, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse,
                getNotFoundPublicationServiceResponse(EXTERNAL_ERROR_MESSAGE_DECORATION + PUBLICATION_IDENTIFIER
                        + " " + ERROR_PUBLICATION_NOT_FOUND_FOR_IDENTIFIER + PUBLICATION_IDENTIFIER));
    }

    @Test
    void shouldReturnNotFoundWhenPublicationServiceResponseIsUnderstood() throws IOException, InterruptedException {
        var publicationService = mockNotFoundPublicationService();
        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();

        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());

        handler.handleRequest(createAnonymousRequest(), output, context);

        GatewayResponse<Problem> gatewayResponse = GatewayResponse.fromOutputStream(output);
        assertBasicRestRequirements(gatewayResponse, SC_NOT_FOUND, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse,
                getNotFoundPublicationServiceResponse(EXTERNAL_ERROR_MESSAGE_DECORATION + PUBLICATION_IDENTIFIER
                        + " " + EASY_TO_SEE + PUBLICATION_IDENTIFIER));
    }

    @ParameterizedTest(name = "Should return Not Found when requester is not owner and embargo is in place")
    @MethodSource("nonOwnerProvider")
    void shouldDisallowDownloadByNonOwnerWhenEmbargoDateHasNotPassed(InputStream request) throws IOException,
            InterruptedException {
        var publicationService = mockPublicationServiceReturningEmbargoedFile();
        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());
        handler.handleRequest(request, output, context);
        GatewayResponse<Problem> gatewayResponse = GatewayResponse.fromOutputStream(output);
        assertBasicRestRequirements(gatewayResponse, SC_NOT_FOUND, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse,
                getNotFoundPublicationServiceResponse(notFoundError(FILE_IDENTIFIER)));
    }

    @ParameterizedTest(name = "Should return Not Found when requester is not owner and embargo is in place")
    @MethodSource("nonOwnerProvider")
    void shouldDisallowDownloadByNonOwnerWhenFileHasIsAdministrativeAgreement(InputStream request) throws IOException,
            InterruptedException {
        var publicationService = mockPublicationServiceReturningAdministrativeAgreement();
        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());
        handler.handleRequest(request, output, context);
        GatewayResponse<Problem> gatewayResponse = GatewayResponse.fromOutputStream(output);
        assertBasicRestRequirements(gatewayResponse, SC_NOT_FOUND, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse,
                getNotFoundPublicationServiceResponse(notFoundError(FILE_IDENTIFIER)));
    }

    private String notFoundError(UUID someRandomIdentifier) {
        return String.format(ERROR_TEMPLATE, PUBLICATION_IDENTIFIER, someRandomIdentifier);
    }

    private RestPublicationService mockPublicationServiceReturningAdministrativeAgreement() throws IOException,
            InterruptedException {
        var event = new Event(OWNER_USER_ID, PUBLICATION_IDENTIFIER, PUBLISHED,
                new FileSet(List.of(administrativeAgreement())));
        var eventString = dtoObjectMapper.writeValueAsString(event);
        return mockSuccessfulPublicationRequest(eventString);
    }

    private RestPublicationService mockPublicationServiceReturningEmbargoedFile() throws IOException,
            InterruptedException {
        var event = new Event(OWNER_USER_ID, PUBLICATION_IDENTIFIER, PUBLISHED,
                new FileSet(List.of(fileWithEmbargo())));
        var eventString = dtoObjectMapper.writeValueAsString(event);
        return mockSuccessfulPublicationRequest(eventString);
    }

    private File administrativeAgreement() {
        return new File.Builder()
                .withAdministrativeAgreement(true)
                .withIdentifier(FILE_IDENTIFIER)
                .withLicense(new License.Builder().build())
                .withMimeType(APPLICATION_PDF)
                .withName("A file name.txt")
                .withPublisherAuthority(true)
                .withSize(200L)
                .build();
    }

    private File fileWithEmbargo() {
        return new File.Builder()
                .withAdministrativeAgreement(false)
                .withEmbargoDate(Instant.now().plus(Duration.ofDays(3L)))
                .withIdentifier(FILE_IDENTIFIER)
                .withLicense(new License.Builder().build())
                .withMimeType(APPLICATION_PDF)
                .withName("A file name.txt")
                .withPublisherAuthority(true)
                .withSize(200L)
                .build();
    }

    private File fileWithoutEmbargo(String mimeType) {
        return new File.Builder()
                .withAdministrativeAgreement(false)
                .withIdentifier(FILE_IDENTIFIER)
                .withLicense(new License.Builder().build())
                .withMimeType(mimeType)
                .withName("A file name.txt")
                .withPublisherAuthority(true)
                .withSize(200L)
                .build();
    }

    private void assertProblemEquivalence(GatewayResponse<Problem> gatewayResponse, Problem expected)
            throws JsonProcessingException {
        var actual = gatewayResponse.getBodyObject(Problem.class);
        assertThat(actual.getStatus(), equalTo(expected.getStatus()));
        assertThat(actual.getTitle(), equalTo(expected.getTitle()));
        assertThat(actual.getDetail(), equalTo(expected.getDetail()));
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

    private String createPublishedPublicationWithoutFileSetFile() throws JsonProcessingException {
        return dtoObjectMapper.writeValueAsString(
                new Event(OWNER_USER_ID, PUBLICATION_IDENTIFIER, PUBLISHED, new FileSet(emptyList())));
    }

    private AwsS3Service getAwsS3ServiceReturningNotFound() {
        var amazonS3 = mock(AmazonS3.class);
        when(amazonS3.generatePresignedUrl(any())).thenThrow(notFoundException());
        return new AwsS3Service(amazonS3, ANY_BUCKET);
    }

    private SdkClientException notFoundException() {
        return new NotFoundException("Not Found");
    }

    private RestPublicationService mockUnresponsivePublicationService() throws IOException, InterruptedException {
        var publicationService = new RestPublicationService(httpClient, dtoObjectMapper, API_SCHEME, API_HOST);
        when(httpClient.<String>send(any(), any())).thenThrow(IOException.class);
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
                .writeValueAsString(getNotFoundPublicationServiceResponse(EASY_TO_SEE + PUBLICATION_IDENTIFIER));
    }


    private Problem getNotFoundPublicationServiceResponse(String message) {
        return Problem.builder()
                .withStatus(NOT_FOUND)
                .withTitle(NOT_FOUND.getReasonPhrase())
                .withDetail(message)
                .build();
    }

    private String getPublication(PublicationStatus status) throws JsonProcessingException {
        return getPublication(status, APPLICATION_PDF);
    }

    private String getPublication(PublicationStatus publicationStatus, String mimeType) throws JsonProcessingException {
        var event = new Event(OWNER_USER_ID, PUBLICATION_IDENTIFIER, publicationStatus,
                new FileSet(List.of(fileWithoutEmbargo(mimeType))));
        return dtoObjectMapper.writeValueAsString(event);
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

    private void assertExpectedResponseBody(GatewayResponse<PresignedUri> gatewayResponse)
            throws JsonProcessingException {
        var body = gatewayResponse.getBodyObject(PresignedUri.class);
        assertThat(body.getPresignedDownloadUrl(), is(notNullValue()));
        assertThat(body.getId(), is(notNullValue()));
        assertTrue(greaterThanNow(body.getExpires()));
        assertThat(body.getContext(), is(notNullValue()));
    }

    private boolean greaterThanNow(Instant instant) {
        return Instant.now().isBefore(instant);
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

    private InputStream createRequest(String user, UUID identifier, UUID fileIdentifier) throws IOException {
        return new HandlerRequestBuilder<Void>(dtoObjectMapper)
                .withHeaders(Map.of(AUTHORIZATION, SOME_API_KEY))
                .withFeideId(user)
                .withPathParameters(Map.of(IDENTIFIER, identifier.toString(),
                        IDENTIFIER_FILE, fileIdentifier.toString()))
                .build();
    }

    private static InputStream createAnonymousRequest() throws IOException {
        return new HandlerRequestBuilder<Void>(dtoObjectMapper)
                .withPathParameters(Map.of(IDENTIFIER, PUBLICATION_IDENTIFIER.toString(),
                        IDENTIFIER_FILE, FILE_IDENTIFIER.toString()))
                .build();
    }

    private static InputStream createNonOwnerRequest() throws JsonProcessingException {
        return new HandlerRequestBuilder<Void>(dtoObjectMapper)
                .withFeideId(NON_ONWER)
                .withPathParameters(Map.of(IDENTIFIER, PUBLICATION_IDENTIFIER.toString(),
                        IDENTIFIER_FILE, FILE_IDENTIFIER.toString()))
                .build();
    }

    private static InputStream createBadRequestNoIdentifier() throws IOException {
        return new HandlerRequestBuilder<Void>(dtoObjectMapper)
                .withPathParameters(Map.of(IDENTIFIER_FILE, FILE_IDENTIFIER.toString()))
                .build();
    }

    private static InputStream createBadRequestNoFileIdentifier() throws JsonProcessingException {
        return new HandlerRequestBuilder<Void>(dtoObjectMapper)
                .withPathParameters(Map.of(IDENTIFIER, PUBLICATION_IDENTIFIER.toString()))
                .build();
    }

    private static InputStream createBadRequestNonUuidFileIdentifier() throws JsonProcessingException {
        return new HandlerRequestBuilder<Void>(dtoObjectMapper)
                .withPathParameters(Map.of(IDENTIFIER, PUBLICATION_IDENTIFIER.toString(),
                        RequestUtil.FILE_IDENTIFIER, NOT_A_UUID))
                .build();
    }

    private AwsS3Service getS3ServiceThrowingSdkClientException() {
        var amazonS3 = mock(AmazonS3.class);
        when(amazonS3.generatePresignedUrl(any()))
                .thenThrow(new SdkClientException(ERROR_COMMUNICATING_WITH_REMOTE_SERVICE
                        + HTTP_EXAMPLE_ORG_PUBLICATION
                        + PUBLICATION_IDENTIFIER));
        return new AwsS3Service(amazonS3, ANY_BUCKET);
    }
}
