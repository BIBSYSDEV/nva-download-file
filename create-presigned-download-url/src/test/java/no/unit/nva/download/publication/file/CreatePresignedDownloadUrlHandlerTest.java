package no.unit.nva.download.publication.file;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.download.publication.file.RequestUtil.IDENTIFIER_IS_NOT_A_VALID_UUID;
import static no.unit.nva.download.publication.file.RequestUtil.MISSING_FILE_IDENTIFIER;
import static no.unit.nva.download.publication.file.RequestUtil.MISSING_RESOURCE_IDENTIFIER;
import static no.unit.nva.download.publication.file.exception.NotFoundException.ERROR_TEMPLATE;
import static no.unit.nva.download.publication.file.publication.RestPublicationService.ERROR_COMMUNICATING_WITH_REMOTE_SERVICE;
import static no.unit.nva.download.publication.file.publication.RestPublicationService.ERROR_PUBLICATION_NOT_FOUND_FOR_IDENTIFIER;
import static no.unit.nva.download.publication.file.publication.RestPublicationService.EXTERNAL_ERROR_MESSAGE_DECORATION;
import static no.unit.nva.download.publication.file.publication.model.PublicationStatus.DRAFT;
import static no.unit.nva.download.publication.file.publication.model.PublicationStatus.PUBLISHED;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static no.unit.nva.testutils.TestHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static nva.commons.apigateway.AccessRight.MANAGE_DEGREE_EMBARGO;
import static nva.commons.apigateway.AccessRight.MANAGE_RESOURCES_STANDARD;
import static nva.commons.apigateway.ApiGatewayHandler.ALLOWED_ORIGIN_ENV;
import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.apache.http.HttpStatus.SC_BAD_GATEWAY;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.zalando.problem.Status.BAD_GATEWAY;
import static org.zalando.problem.Status.BAD_REQUEST;
import static org.zalando.problem.Status.NOT_FOUND;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.kms.model.NotFoundException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.s3.AmazonS3;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import no.unit.nva.download.publication.file.aws.s3.AwsS3Service;
import no.unit.nva.download.publication.file.publication.RestPublicationService;
import no.unit.nva.download.publication.file.publication.model.AssociatedLink;
import no.unit.nva.download.publication.file.publication.model.EntityDescription;
import no.unit.nva.download.publication.file.publication.model.File;
import no.unit.nva.download.publication.file.publication.model.NullAssociatedArtifact;
import no.unit.nva.download.publication.file.publication.model.Publication;
import no.unit.nva.download.publication.file.publication.model.PublicationInstance;
import no.unit.nva.download.publication.file.publication.model.PublicationStatus;
import no.unit.nva.download.publication.file.publication.model.PublishedFile;
import no.unit.nva.download.publication.file.publication.model.Reference;
import no.unit.nva.download.publication.file.publication.model.ResourceOwner;
import no.unit.nva.download.publication.file.publication.model.UnpublishableFile;
import no.unit.nva.download.publication.file.publication.model.UnpublishedFile;
import no.unit.nva.download.publication.file.utils.FakeUriShortener;
import no.unit.nva.download.publication.file.utils.FakeUriShortenerThrowingException;
import no.unit.nva.identifiers.SortableIdentifier;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.GatewayResponse;
import nva.commons.core.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.stubbing.Answer;
import org.zalando.problem.Problem;

class CreatePresignedDownloadUrlHandlerTest {

    private static final String SOME_API_KEY = "some api key";
    private static final String IDENTIFIER = "identifier";
    private static final String IDENTIFIER_FILE = "fileIdentifier";
    private static final String OWNER_USER_ID = "owner@unit.no";
    private static final String NON_OWNER = "non.owner@unit.no";
    private static final String CURATOR = "curator@unit.no";
    private static final String ANY_BUCKET = "aBucket";
    private static final String APPLICATION_PROBLEM_JSON = "application/problem+json";
    private static final String PRESIGNED_DOWNLOAD_URL = "https://example.com/download/12345";
    private static final String API_HOST = "example.org";
    private static final String API_SCHEME = "https";
    private static final UUID FILE_IDENTIFIER = UUID.randomUUID();
    private static final UUID UNEMBARGOED_FILE_IDENTIFIER = UUID.randomUUID();
    private static final UUID EMBARGOED_FILE_IDENTIFIER = UUID.randomUUID();
    private static final UUID ADMINISTRATIVE_IDENTIFIER = UUID.randomUUID();
    private static final String ANY_ORIGIN = "*";
    private static final SortableIdentifier SOME_RANDOM_IDENTIFIER = SortableIdentifier.next();
    private static final String NOT_A_UUID = "not-a-UUID";
    private static final String EASY_TO_SEE = "Easy-to-see: ";
    private static final String HTTP_EXAMPLE_ORG_PUBLICATION = "https://example.org/publication/";
    private static final String APPLICATION_JSON = "application/json; charset=utf-8";
    private static final String APPLICATION_PDF = "application/pdf";
    private HttpClient httpClient;
    private Context context;
    private ByteArrayOutputStream output;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        context = mock(Context.class);
        output = new ByteArrayOutputStream();
    }

    @ParameterizedTest(name = "Should return not found when user is not owner and publication is unpublished")
    @MethodSource("fileTypeSupplier")
    void shouldReturnNotFoundWhenUserIsNotOwnerAndPublicationIsUnpublished(File file) throws IOException,
                                                                                             InterruptedException {
        var s3Service = getAwsS3ServiceReturningPresignedUrl();
        var publication = buildPublication(DRAFT, file);
        var publicationService = mockSuccessfulPublicationRequest(dtoObjectMapper.writeValueAsString(publication));
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment(),
                                                            new FakeUriShortener());
        handler.handleRequest(
            createRequest(NON_OWNER, publication.identifier(), file.getIdentifier()), output, context);

        var gatewayResponse = GatewayResponse.fromString(output.toString(), Problem.class);
        assertBasicRestRequirements(gatewayResponse, SC_NOT_FOUND, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse, getNotFoundPublicationServiceResponse(
            notFoundError(publication.identifier(), file.getIdentifier())));
    }

    @ParameterizedTest(
        name = "Files which is not draft but from a type requiring elevated rights is not downloadable by non owner")
    @MethodSource("fileTypeSupplierRequiringElevatedRights")
    void handlerReturnsNotFoundForFilesWhichIsNotDraftButHasTypeRequiringElevationAndIsNonOwner(File file)
        throws IOException, InterruptedException {
        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();
        var publication = buildPublication(PUBLISHED, file);
        var publicationService = mockSuccessfulPublicationRequest(dtoObjectMapper.writeValueAsString(publication));
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment(),
                                                            new FakeUriShortener());
        handler.handleRequest(
            createRequest(NON_OWNER, publication.identifier(), file.getIdentifier()), output, context);

        var gatewayResponse = GatewayResponse.fromString(output.toString(), Problem.class);
        assertBasicRestRequirements(gatewayResponse, SC_NOT_FOUND, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse, getNotFoundPublicationServiceResponse(
            notFoundError(publication.identifier(), file.getIdentifier())));
    }

    @Test
    void shouldThrowExceptionWhenEnvironmentVariablesAreUnset() {
        assertThrows(IllegalStateException.class, CreatePresignedDownloadUrlHandler::new);
    }

    @ParameterizedTest(name = "Published publication is downloadable by user {0}")
    @MethodSource("userSupplier")
    void handlerReturnsOkResponseOnValidInputPublishedPublication(String user) throws IOException,
                                                                                      InterruptedException {

        var s3Service = getAwsS3ServiceReturningPresignedUrl();
        var publication = buildPublication(PUBLISHED, fileWithoutEmbargo(APPLICATION_PDF, FILE_IDENTIFIER));
        var publicationService = mockSuccessfulPublicationRequest(
            dtoObjectMapper.writeValueAsString(publication));
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment(),
                                                            new FakeUriShortener());

        handler.handleRequest(createRequest(user, publication.identifier(), FILE_IDENTIFIER), output, context);

        var gatewayResponse = GatewayResponse.fromString(output.toString(), PresignedUri.class);
        assertBasicRestRequirements(gatewayResponse, SC_OK, APPLICATION_JSON);
        assertExpectedResponseBody(gatewayResponse);
    }

    @Test
    void shouldReturnOkWhenPublicationUnpublishedAndUserIsOwner() throws IOException, InterruptedException {
        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();
        var publication = buildPublication(DRAFT, fileWithoutEmbargo(APPLICATION_PDF, FILE_IDENTIFIER));
        var publicationService = mockSuccessfulPublicationRequest(dtoObjectMapper.writeValueAsString(publication));
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment(),
                                                            new FakeUriShortener());

        handler.handleRequest(
            createRequest(
                publication.resourceOwner().owner(),
                publication.identifier(),
                FILE_IDENTIFIER),
            output, context);

        var gatewayResponse = GatewayResponse.fromString(output.toString(), PresignedUri.class);
        assertBasicRestRequirements(gatewayResponse, SC_OK, APPLICATION_JSON);
        assertExpectedResponseBody(gatewayResponse);
    }

    @Test
    void shouldReturnOkWhenPublicationUnpublishedAndUserHasAccessRightManageResourcesStandard()
        throws IOException, InterruptedException {
        var s3Service = getAwsS3ServiceReturningPresignedUrl();
        var publication = buildPublication(DRAFT, fileWithoutEmbargo(APPLICATION_PDF, FILE_IDENTIFIER));
        var publicationService = mockSuccessfulPublicationRequest(dtoObjectMapper.writeValueAsString(publication));

        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment(),
                                                            new FakeUriShortener());
        var customer = randomUri();
        handler.handleRequest(createRequestWithAccessRight(
                                  NON_OWNER,
                                  publication.identifier(),
                                  FILE_IDENTIFIER,
                                  customer,
                                  MANAGE_DEGREE_EMBARGO, MANAGE_RESOURCES_STANDARD),
                              output,
                              context);

        var gatewayResponse = GatewayResponse.fromString(output.toString(), PresignedUri.class);
        assertBasicRestRequirements(gatewayResponse, SC_OK, APPLICATION_JSON);
        assertExpectedResponseBody(gatewayResponse);
    }

    @Test
    void shouldReturnBadGatewayWhenPublicationCanNotBeParsed() throws IOException, InterruptedException {
        var s3Service = getAwsS3ServiceReturningPresignedUrl();
        var publicationService = mockSuccessfulPublicationRequest("<</>");
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment(),
                                                            new FakeUriShortener());

        handler.handleRequest(createRequest(OWNER_USER_ID, SortableIdentifier.next(), FILE_IDENTIFIER),
                              output, context);

        var gatewayResponse = GatewayResponse.fromString(output.toString(), PresignedUri.class);
        assertBasicRestRequirements(gatewayResponse, SC_BAD_GATEWAY, APPLICATION_PROBLEM_JSON);
    }

    @ParameterizedTest(name = "Should return presigned URI when mime-type of file is {0}")
    @MethodSource("mimeTypeProvider")
    void shouldReturnOkWhenPublicationUnpublishedAndUserIsOwnerAndMimeTypeIs(String mimeType)
        throws IOException, InterruptedException {
        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();
        var publication = buildPublication(DRAFT, fileWithoutEmbargo(mimeType, FILE_IDENTIFIER));
        var publicationService = mockSuccessfulPublicationRequest(dtoObjectMapper.writeValueAsString(publication));
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment(),
                                                            new FakeUriShortener());

        handler.handleRequest(createRequest(publication.resourceOwner().owner(),
                                            publication.identifier(), FILE_IDENTIFIER), output, context);

        var gatewayResponse = GatewayResponse.fromString(output.toString(), PresignedUri.class);
        assertBasicRestRequirements(gatewayResponse, SC_OK, APPLICATION_JSON);
        assertExpectedResponseBody(gatewayResponse);
    }

    // Error message here is odd
    @Test
    void handlerReturnsNotFoundResponseOnUnknownIdentifier() throws IOException, InterruptedException {
        var publicationIdentifier = SortableIdentifier.next();
        var publicationService = mockNotFoundPublicationService(publicationIdentifier);
        var handler = new CreatePresignedDownloadUrlHandler(publicationService,
                                                            getAwsS3ServiceReturningPresignedUrl(),
                                                            mockEnvironment(), new FakeUriShortener());
        handler.handleRequest(createRequest(OWNER_USER_ID, publicationIdentifier, FILE_IDENTIFIER), output,
                              context);

        var gatewayResponse = GatewayResponse.fromOutputStream(output, Problem.class);
        assertBasicRestRequirements(gatewayResponse, SC_NOT_FOUND, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse,
                                 getNotFoundPublicationServiceResponse(
                                     EXTERNAL_ERROR_MESSAGE_DECORATION + publicationIdentifier
                                     + " " + EASY_TO_SEE + publicationIdentifier));
    }

    @Test
    void handlerReturnsServiceUnavailableResponseOnServerErrorResponseFromPublicationService()
        throws IOException, InterruptedException {
        var publicationService = mockUnresponsivePublicationService();
        var handler = new CreatePresignedDownloadUrlHandler(publicationService,
                                                            getAwsS3ServiceReturningPresignedUrl(),
                                                            mockEnvironment(), new FakeUriShortener());

        handler.handleRequest(createRequest(OWNER_USER_ID, SOME_RANDOM_IDENTIFIER, FILE_IDENTIFIER), output,
                              context);

        var gatewayResponse = GatewayResponse.fromOutputStream(output, Problem.class);
        assertBasicRestRequirements(gatewayResponse, SC_BAD_GATEWAY, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse, getBadGatewayProblem(SOME_RANDOM_IDENTIFIER));
    }

    @ParameterizedTest(name = "Unpublished publication with filetype {1} is downloadable by user {0}")
    @MethodSource("userFileTypeSupplier")
    void handlerReturnsOkResponseOnValidInputPublication(String user, File file)
        throws IOException, InterruptedException {

        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();
        var publication = buildPublication(DRAFT, file);
        var publicationService = mockSuccessfulPublicationRequest(dtoObjectMapper.writeValueAsString(publication));
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment(),
                                                            new FakeUriShortener());
        var customer = randomUri();
        handler.handleRequest(
            createRequestWithAccessRight(
                user,
                publication.identifier(),
                file.getIdentifier(),
                customer,
                MANAGE_DEGREE_EMBARGO, MANAGE_RESOURCES_STANDARD),
            output,
            context);

        var gatewayResponse = GatewayResponse.fromString(output.toString(), PresignedUri.class);
        assertBasicRestRequirements(gatewayResponse, SC_OK, APPLICATION_JSON);
        assertExpectedResponseBody(gatewayResponse);
    }

    @Test
    void handlerReturnsNotFoundOnPublicationWithoutFile() throws IOException, InterruptedException {
        var publication = buildPublishedPublicationWithoutFiles();
        var json = dtoObjectMapper.writeValueAsString(publication);
        var publicationService = mockSuccessfulPublicationRequest(json);
        var handler = new CreatePresignedDownloadUrlHandler(publicationService,
                                                            getAwsS3ServiceReturningNotFound(),
                                                            mockEnvironment(), new FakeUriShortener());
        var fileIdentifier = UUID.randomUUID();
        handler.handleRequest(createRequest(OWNER_USER_ID, publication.identifier(), fileIdentifier),
                              output, context);

        var gatewayResponse = GatewayResponse.fromOutputStream(output, Problem.class);
        assertBasicRestRequirements(gatewayResponse, SC_NOT_FOUND, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse,
                                 getNotFoundPublicationServiceResponse(
                                     notFoundError(publication.identifier(), fileIdentifier)));
    }

    @Test
    void shouldReturnServiceUnavailableResponseOnS3ServiceException() throws IOException, InterruptedException {
        var publication = buildPublication(PUBLISHED, fileWithoutEmbargo(APPLICATION_PDF, FILE_IDENTIFIER));
        var publicationIdentifier = publication.identifier();
        var s3Service = getS3ServiceThrowingSdkClientException(publicationIdentifier);
        var publicationService = mockSuccessfulPublicationRequest(dtoObjectMapper.writeValueAsString(publication));
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment(),
                                                            new FakeUriShortener());

        handler.handleRequest(
            createRequest(
                publication.resourceOwner().owner(),
                publicationIdentifier,
                FILE_IDENTIFIER),
            output, context);

        var gatewayResponse = GatewayResponse.fromOutputStream(output, Problem.class);
        assertBasicRestRequirements(gatewayResponse, SC_BAD_GATEWAY, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse, getBadGatewayProblem(publicationIdentifier));
    }

    @Test
    void shouldReturnNotFoundOnAnonymousRequestForDraftPublication()
        throws IOException, InterruptedException {
        var publication = buildPublication(DRAFT, fileWithoutEmbargo(APPLICATION_PDF, FILE_IDENTIFIER));
        var publicationService = mockSuccessfulPublicationRequest(dtoObjectMapper.writeValueAsString(publication));
        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();

        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment(),
                                                            new FakeUriShortener());

        handler.handleRequest(createAnonymousRequest(publication.identifier()), output, context);

        var gatewayResponse = GatewayResponse.fromOutputStream(output, Problem.class);
        assertBasicRestRequirements(gatewayResponse, SC_NOT_FOUND, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse, getNotFoundPublicationServiceResponse(
            notFoundError(publication.identifier(), FILE_IDENTIFIER)));
    }

    @ParameterizedTest
    @MethodSource("badRequestProvider")
    void shouldReturnBadRequestWhenRequestIsBad(InputStream request, String detail) throws IOException,
                                                                                           InterruptedException {
        var publication = buildPublication(DRAFT, fileWithoutEmbargo(APPLICATION_PDF, FILE_IDENTIFIER));
        var publicationService = mockSuccessfulPublicationRequest(dtoObjectMapper.writeValueAsString(publication));
        var s3Service = getAwsS3ServiceReturningPresignedUrl();

        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment(),
                                                            new FakeUriShortener());

        handler.handleRequest(request, output, context);

        var gatewayResponse = GatewayResponse.fromOutputStream(output, Problem.class);
        assertBasicRestRequirements(gatewayResponse, SC_BAD_REQUEST, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse, getBadRequestPublicationServiceResponse(detail));
    }

    @Test
    void shouldReturnNotFoundWhenPublicationServiceResponseIsNotUnderstood() throws IOException,
                                                                                    InterruptedException {
        var publicationService = mockPublicationServiceReturningStrangeResponse();
        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();

        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment(),
                                                            new FakeUriShortener());
        var publicationIdentifier = SortableIdentifier.next();
        handler.handleRequest(createAnonymousRequest(publicationIdentifier), output, context);

        var gatewayResponse = GatewayResponse.fromOutputStream(output, Problem.class);
        assertBasicRestRequirements(gatewayResponse, SC_NOT_FOUND, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse,
                                 getNotFoundPublicationServiceResponse(
                                     EXTERNAL_ERROR_MESSAGE_DECORATION + publicationIdentifier
                                     + " " + ERROR_PUBLICATION_NOT_FOUND_FOR_IDENTIFIER + publicationIdentifier));
    }

    @Test
    void shouldReturnNotFoundWhenPublicationServiceResponseIsUnderstood() throws IOException,
                                                                                 InterruptedException {
        var publicationIdentifier = SortableIdentifier.next();
        var publicationService = mockNotFoundPublicationService(publicationIdentifier);
        var s3Service = getAwsS3ServiceReturningPresignedUrl();

        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment(),
                                                            new FakeUriShortener());

        handler.handleRequest(createAnonymousRequest(publicationIdentifier), output, context);

        var gatewayResponse = GatewayResponse.fromOutputStream(output, Problem.class);
        assertBasicRestRequirements(gatewayResponse, SC_NOT_FOUND, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse,
                                 getNotFoundPublicationServiceResponse(
                                     EXTERNAL_ERROR_MESSAGE_DECORATION + publicationIdentifier
                                     + " " + EASY_TO_SEE + publicationIdentifier));
    }

    @ParameterizedTest(name = "Should return Not Found when requester is not owner and embargo is in place")
    @MethodSource("nonOwnerProvider")
    void shouldDisallowDownloadByNonOwnerWhenEmbargoDateHasNotPassed(InputStream request) throws IOException,
                                                                                                 InterruptedException {
        var publication = buildPublication(PUBLISHED, fileWithEmbargo(FILE_IDENTIFIER));
        var publicationService = mockSuccessfulPublicationRequest(dtoObjectMapper.writeValueAsString(publication));
        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment(),
                                                            new FakeUriShortener());
        handler.handleRequest(request, output, context);
        var gatewayResponse = GatewayResponse.fromOutputStream(output, Problem.class);
        assertBasicRestRequirements(gatewayResponse, SC_NOT_FOUND, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse, getNotFoundPublicationServiceResponse(
            notFoundError(publication.identifier(), FILE_IDENTIFIER)));
    }

    @ParameterizedTest(name = "Should return Not Found when requester is not owner and file is administrative "
                              + "agreement")
    @MethodSource("nonOwnerProvider")
    void shouldDisallowDownloadByNonOwnerWhenFileHasIsAdministrativeAgreement(InputStream request)
        throws IOException, InterruptedException {
        var publication = buildPublication(PUBLISHED, fileWithTypeUnpublishable(FILE_IDENTIFIER, true));
        var publicationService = mockSuccessfulPublicationRequest(dtoObjectMapper.writeValueAsString(publication));
        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment(),
                                                            new FakeUriShortener());
        handler.handleRequest(request, output, context);
        var gatewayResponse = GatewayResponse.fromOutputStream(output, Problem.class);
        assertBasicRestRequirements(gatewayResponse, SC_NOT_FOUND, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse, getNotFoundPublicationServiceResponse(
            notFoundError(publication.identifier(), FILE_IDENTIFIER)));
    }

    @Test
    void shouldThrowInternalServerExceptionIfUriShortenerFails() throws IOException, InterruptedException {
        var s3Service = getAwsS3ServiceReturningPresignedUrl();
        var publication = buildPublication(DRAFT, fileWithoutEmbargo(APPLICATION_PDF, FILE_IDENTIFIER));
        var publicationService = mockSuccessfulPublicationRequest(dtoObjectMapper.writeValueAsString(publication));
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment(),
        new FakeUriShortenerThrowingException());
        var customer = randomUri();
        handler.handleRequest(createRequestWithAccessRight(
                                  NON_OWNER,
                                  publication.identifier(),
                                  FILE_IDENTIFIER,
                                  customer,
                                  MANAGE_DEGREE_EMBARGO, MANAGE_RESOURCES_STANDARD),
                              output,
                              context);

        var gatewayResponse = GatewayResponse.fromOutputStream(output, Problem.class);
        assertBasicRestRequirements(gatewayResponse, SC_INTERNAL_SERVER_ERROR, APPLICATION_PROBLEM_JSON);
    }

    private static Stream<String> userSupplier() {
        return Stream.of(
            OWNER_USER_ID,
            null,
            NON_OWNER,
            CURATOR
        );
    }

    private static Stream<Arguments> userFileTypeSupplier() {
        return Stream.of(
            Arguments.of(OWNER_USER_ID, fileWithEmbargo(EMBARGOED_FILE_IDENTIFIER)),
            Arguments.of(OWNER_USER_ID, fileWithoutEmbargo(APPLICATION_PDF, UNEMBARGOED_FILE_IDENTIFIER)),
            Arguments.of(OWNER_USER_ID, fileWithTypeUnpublishable(ADMINISTRATIVE_IDENTIFIER, true)),
            Arguments.of(OWNER_USER_ID, fileWithTypeUnpublishable(ADMINISTRATIVE_IDENTIFIER, false)),
            Arguments.of(OWNER_USER_ID, fileWithTypeUnpublished()),
            Arguments.of(CURATOR, fileWithEmbargo(EMBARGOED_FILE_IDENTIFIER)),
            Arguments.of(CURATOR, fileWithoutEmbargo(APPLICATION_PDF, UNEMBARGOED_FILE_IDENTIFIER)),
            Arguments.of(CURATOR, fileWithTypeUnpublishable(ADMINISTRATIVE_IDENTIFIER, true)),
            Arguments.of(CURATOR, fileWithTypeUnpublishable(ADMINISTRATIVE_IDENTIFIER, false)),
            Arguments.of(CURATOR, fileWithTypeUnpublished())
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
            Arguments.of(createBadRequestNoFileIdentifier(SortableIdentifier.next()), MISSING_FILE_IDENTIFIER),
            Arguments.of(createBadRequestNonUuidFileIdentifier(SortableIdentifier.next()),
                         IDENTIFIER_IS_NOT_A_VALID_UUID + NOT_A_UUID)
        );
    }

    private static Stream<InputStream> nonOwnerProvider() throws IOException {
        return Stream.of(
            createAnonymousRequest(SortableIdentifier.next()),
            createNonOwnerRequest(SortableIdentifier.next())
        );
    }

    private static Stream<File> fileTypeSupplier() {
        return Stream.of(
            fileWithEmbargo(EMBARGOED_FILE_IDENTIFIER),
            fileWithoutEmbargo(APPLICATION_PDF, UNEMBARGOED_FILE_IDENTIFIER),
            fileWithTypeUnpublishable(ADMINISTRATIVE_IDENTIFIER, true),
            fileWithTypeUnpublishable(ADMINISTRATIVE_IDENTIFIER, false),
            fileWithTypeUnpublished()
        );
    }

    private static Stream<File> fileTypeSupplierRequiringElevatedRights() {
        return Stream.of(
            fileWithEmbargo(EMBARGOED_FILE_IDENTIFIER),
            fileWithTypeUnpublishable(ADMINISTRATIVE_IDENTIFIER, true),
            fileWithTypeUnpublishable(ADMINISTRATIVE_IDENTIFIER, false),
            fileWithTypeUnpublished()
        );
    }

    private static File fileWithEmbargo(UUID fileIdentifier) {
        var embargo = Instant.now().plus(Duration.ofDays(3L));
        return new PublishedFile(
            fileIdentifier,
            APPLICATION_PDF,
            embargo, false);
    }

    private static File fileWithoutEmbargo(String mimeType, UUID fileIdentifier) {
        return new PublishedFile(
            fileIdentifier,
            mimeType,
            null,
            false);
    }

    private static File fileWithTypeUnpublished() {
        return new UnpublishedFile(
            FILE_IDENTIFIER,
            APPLICATION_PDF,
            null,
            false
        );
    }

    private static File fileWithTypeUnpublishable(UUID fileIdentifier, boolean administrativeAgreement) {
        return new UnpublishableFile(
            fileIdentifier,
            APPLICATION_PDF,
            null,
            administrativeAgreement
        );
    }

    private static InputStream createAnonymousRequest(SortableIdentifier publicationIdentifier) throws
                                                                                                IOException {
        return new HandlerRequestBuilder<Void>(dtoObjectMapper)
                   .withPathParameters(Map.of(IDENTIFIER, publicationIdentifier.toString(),
                                              IDENTIFIER_FILE, FILE_IDENTIFIER.toString()))
                   .build();
    }

    private static InputStream createNonOwnerRequest(SortableIdentifier publicationIdentifier)
        throws JsonProcessingException {
        return new HandlerRequestBuilder<Void>(dtoObjectMapper)
                   .withUserName(NON_OWNER)
                   .withCurrentCustomer(randomUri())
                   .withPathParameters(Map.of(IDENTIFIER, publicationIdentifier.toString(),
                                              IDENTIFIER_FILE, FILE_IDENTIFIER.toString()))
                   .build();
    }

    private static InputStream createBadRequestNoIdentifier() throws IOException {
        return new HandlerRequestBuilder<Void>(dtoObjectMapper)
                   .withCurrentCustomer(randomUri())
                   .withUserName(OWNER_USER_ID)
                   .withPathParameters(Map.of(IDENTIFIER_FILE, FILE_IDENTIFIER.toString()))
                   .build();
    }

    private static InputStream createBadRequestNoFileIdentifier(SortableIdentifier publicationIdentifier)
        throws JsonProcessingException {
        return new HandlerRequestBuilder<Void>(dtoObjectMapper)
                   .withCurrentCustomer(randomUri())
                   .withUserName(OWNER_USER_ID)
                   .withPathParameters(Map.of(IDENTIFIER, publicationIdentifier.toString()))
                   .build();
    }

    private static InputStream createBadRequestNonUuidFileIdentifier(SortableIdentifier publicationIdentifier)
        throws JsonProcessingException {
        return new HandlerRequestBuilder<Void>(dtoObjectMapper)
                   .withCurrentCustomer(randomUri())
                   .withUserName(OWNER_USER_ID)
                   .withPathParameters(Map.of(IDENTIFIER, publicationIdentifier.toString(),
                                              RequestUtil.FILE_IDENTIFIER, NOT_A_UUID))
                   .build();
    }

    private Problem getBadGatewayProblem(SortableIdentifier identifier) {
        return Problem.builder()
                   .withStatus(BAD_GATEWAY)
                   .withTitle(BAD_GATEWAY.getReasonPhrase())
                   .withDetail(ERROR_COMMUNICATING_WITH_REMOTE_SERVICE
                               + HTTP_EXAMPLE_ORG_PUBLICATION
                               + identifier.toString())
                   .build();
    }

    private String notFoundError(SortableIdentifier publicationIdentifier, UUID someRandomIdentifier) {
        return String.format(ERROR_TEMPLATE, publicationIdentifier, someRandomIdentifier);
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
        when(httpClient.<String>send(any(), any())).thenAnswer((Answer<HttpResponse<String>>) invocation ->
                                                                                                  response);
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

    private RestPublicationService mockNotFoundPublicationService(SortableIdentifier publicationIdentifier)
        throws IOException, InterruptedException {
        @SuppressWarnings("unchecked")
        var response = (HttpResponse<String>) mock(HttpResponse.class);
        when(response.statusCode()).thenAnswer(i -> 404);
        when(response.body()).thenAnswer(i -> notFoundProblem(publicationIdentifier));
        when(httpClient.<String>send(any(), any())).thenAnswer((Answer<HttpResponse<String>>) invocation -> response);
        return new RestPublicationService(httpClient, dtoObjectMapper, API_SCHEME, API_HOST);
    }

    private String notFoundProblem(SortableIdentifier publicationIdentifier) throws JsonProcessingException {
        return dtoObjectMapper
                   .writeValueAsString(getNotFoundPublicationServiceResponse(EASY_TO_SEE + publicationIdentifier));
    }

    private Problem getNotFoundPublicationServiceResponse(String message) {
        return Problem.builder()
                   .withStatus(NOT_FOUND)
                   .withTitle(NOT_FOUND.getReasonPhrase())
                   .withDetail(message)
                   .build();
    }

    private Publication buildPublication(PublicationStatus status, File file) {
        var publicationInstanceType = file.hasActiveEmbargo() ? "DegreeMaster" : "AcademicMonograph";
        var entityDescription = new EntityDescription(new Reference(new PublicationInstance(publicationInstanceType)));
        return new Publication(SOME_RANDOM_IDENTIFIER,
                               status,
                               new ResourceOwner("myUsername",
                                                 URI.create("https://my.affiliation.com")),
                               entityDescription, Collections.singletonList(file));
    }

    private Publication buildPublishedPublicationWithoutFiles() {
        var publication = buildPublication(PUBLISHED, fileWithTypeUnpublished());

        var associatedLink = new AssociatedLink();
        var nullAssociatedArtifact = new NullAssociatedArtifact();

        return new Publication(publication.identifier(), PUBLISHED, publication.resourceOwner(),
                               publication.entityDescription(), List.of(associatedLink, nullAssociatedArtifact));
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

    private InputStream createRequest(String user, SortableIdentifier identifier, UUID fileIdentifier)
        throws IOException {
        return new HandlerRequestBuilder<Void>(dtoObjectMapper)
                   .withHeaders(Map.of(AUTHORIZATION, SOME_API_KEY))
                   .withCurrentCustomer(randomUri())
                   .withUserName(user)
                   .withPathParameters(Map.of(IDENTIFIER, identifier.toString(),
                                              IDENTIFIER_FILE, fileIdentifier.toString()))
                   .build();
    }

    private InputStream createRequestWithAccessRight(String user,
                                                     SortableIdentifier identifier,
                                                     UUID fileIdentifier,
                                                     URI customer,
                                                     AccessRight... accessRight) throws IOException {
        return new HandlerRequestBuilder<Void>(dtoObjectMapper)
                   .withHeaders(Map.of(AUTHORIZATION, SOME_API_KEY))
                   .withCurrentCustomer(customer)
                   .withAccessRights(customer, accessRight)
                   .withUserName(user)
                   .withPathParameters(Map.of(IDENTIFIER, identifier.toString(),
                                              IDENTIFIER_FILE, fileIdentifier.toString()))
                   .build();
    }

    private AwsS3Service getS3ServiceThrowingSdkClientException(SortableIdentifier publicationIdentifier) {
        var amazonS3 = mock(AmazonS3.class);
        when(amazonS3.generatePresignedUrl(any()))
            .thenThrow(new SdkClientException(ERROR_COMMUNICATING_WITH_REMOTE_SERVICE
                                              + HTTP_EXAMPLE_ORG_PUBLICATION
                                              + publicationIdentifier));
        return new AwsS3Service(amazonS3, ANY_BUCKET);
    }
}
