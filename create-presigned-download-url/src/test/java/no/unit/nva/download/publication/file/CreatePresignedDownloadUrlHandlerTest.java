package no.unit.nva.download.publication.file;

import static java.util.Collections.emptyList;
import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static no.unit.nva.download.publication.file.RequestUtil.IDENTIFIER_IS_NOT_A_VALID_UUID;
import static no.unit.nva.download.publication.file.RequestUtil.MISSING_FILE_IDENTIFIER;
import static no.unit.nva.download.publication.file.RequestUtil.MISSING_RESOURCE_IDENTIFIER;
import static no.unit.nva.download.publication.file.exception.NotFoundException.ERROR_TEMPLATE;
import static no.unit.nva.download.publication.file.publication.RestPublicationService.ERROR_COMMUNICATING_WITH_REMOTE_SERVICE;
import static no.unit.nva.download.publication.file.publication.RestPublicationService.ERROR_PUBLICATION_NOT_FOUND_FOR_IDENTIFIER;
import static no.unit.nva.download.publication.file.publication.RestPublicationService.EXTERNAL_ERROR_MESSAGE_DECORATION;
import static no.unit.nva.model.PublicationStatus.DRAFT;
import static no.unit.nva.model.PublicationStatus.PUBLISHED;
import static no.unit.nva.testutils.RandomDataGenerator.randomInteger;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static no.unit.nva.testutils.TestHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static nva.commons.apigateway.AccessRight.MANAGE_DEGREE_EMBARGO;
import static nva.commons.apigateway.AccessRight.MANAGE_RESOURCES_STANDARD;
import static nva.commons.apigateway.ApiGatewayHandler.ALLOWED_ORIGIN_ENV;
import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.apache.http.HttpStatus.SC_BAD_GATEWAY;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import no.unit.nva.download.publication.file.aws.s3.AwsS3Service;
import no.unit.nva.download.publication.file.publication.RestPublicationService;
import no.unit.nva.identifiers.SortableIdentifier;
import no.unit.nva.model.Publication;
import no.unit.nva.model.PublicationStatus;
import no.unit.nva.model.associatedartifacts.AssociatedArtifactList;
import no.unit.nva.model.associatedartifacts.NullRightsRetentionStrategy;
import no.unit.nva.model.associatedartifacts.RightsRetentionStrategyConfiguration;
import no.unit.nva.model.associatedartifacts.file.AdministrativeAgreement;
import no.unit.nva.model.associatedartifacts.file.File;
import no.unit.nva.model.associatedartifacts.file.License;
import no.unit.nva.model.associatedartifacts.file.PublishedFile;
import no.unit.nva.model.associatedartifacts.file.PublisherVersion;
import no.unit.nva.model.associatedartifacts.file.UnpublishedFile;
import no.unit.nva.model.instancetypes.degree.DegreeMaster;
import no.unit.nva.model.testing.PublicationGenerator;
import no.unit.nva.model.testing.PublicationInstanceBuilder;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.AccessRight;
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

    @ParameterizedTest(name = "Should return not found when user is not owner and publication is unpublished")
    @MethodSource("fileTypeSupplier")
    void shouldReturnNotFoundWhenUserIsNotOwnerAndPublicationIsUnpublished(File file) throws IOException,
                                                                                  InterruptedException {

        var s3Service = getAwsS3ServiceReturningPresignedUrl();
        var publication = getPublicationWithFile(file);
        publication.setStatus(DRAFT);
        var publicationService = mockSuccessfulPublicationRequest(publication.toString());
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());
        handler.handleRequest(
                createRequest(NON_OWNER, publication.getIdentifier(), file.getIdentifier()), output, context);

        var gatewayResponse = GatewayResponse.fromString(output.toString(), Problem.class);
        assertBasicRestRequirements(gatewayResponse, SC_NOT_FOUND, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse, getNotFoundPublicationServiceResponse(
                notFoundError(publication.getIdentifier(), file.getIdentifier())));
    }

    @ParameterizedTest(name = "Files which is not draft but from a type requiring elevated rights is not downloadable "
                              + "by non owner")
    @MethodSource("fileTypeSupplierRequiringElevatedRights")
    void handlerReturnsNotFoundForFilesWhichIsNotDraftButHasTypeRequiringElevationAndIsNonOwner(File file)
        throws IOException, InterruptedException {
        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();
        var publication = getPublication(PUBLISHED, file);
        var publicationService = mockSuccessfulPublicationRequest(publication.toString());
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());
        handler.handleRequest(
                createRequest(NON_OWNER, publication.getIdentifier(), file.getIdentifier()), output, context);

        GatewayResponse<Problem> gatewayResponse = GatewayResponse.fromString(output.toString(), Problem.class);
        assertBasicRestRequirements(gatewayResponse, SC_NOT_FOUND, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse, getNotFoundPublicationServiceResponse(
                                         notFoundError(publication.getIdentifier(), file.getIdentifier())));
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

        var s3Service = getAwsS3ServiceReturningPresignedUrl();
        var publication = getPublication(PUBLISHED);
        var publicationService = mockSuccessfulPublicationRequest(
            publication.toString());
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());

        handler.handleRequest(createRequest(user, publication.getIdentifier(), FILE_IDENTIFIER), output, context);

        var gatewayResponse =
            GatewayResponse.fromString(output.toString(), PresignedUri.class);
        assertBasicRestRequirements(gatewayResponse, SC_OK, APPLICATION_JSON);
        assertExpectedResponseBody(gatewayResponse);
    }

    @Test
    void shouldReturnOkWhenPublicationUnpublishedAndUserIsOwner() throws IOException, InterruptedException {
        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();
        var publication = getPublication(DRAFT);
        var publicationService = mockSuccessfulPublicationRequest(publication.toString());
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());

        handler.handleRequest(
            createRequest(
                publication.getResourceOwner().getOwner().getValue(),
                publication.getIdentifier(),
                FILE_IDENTIFIER),
            output, context);

        GatewayResponse<PresignedUri> gatewayResponse =
            GatewayResponse.fromString(output.toString(), PresignedUri.class);
        assertBasicRestRequirements(gatewayResponse, SC_OK, APPLICATION_JSON);
        assertExpectedResponseBody(gatewayResponse);
    }

    @Test
    void shouldReturnOkWhenPublicationUnpublishedAndUserHasAccessRightManageResourcesStandard()
        throws IOException, InterruptedException {
        var s3Service = getAwsS3ServiceReturningPresignedUrl();
        var publication = getPublication(DRAFT);
        var publicationService = mockSuccessfulPublicationRequest(
            publication.toString());
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());
        var customer = randomUri();
        handler.handleRequest(createRequestWithAccessRight(
                                  NON_OWNER,
                                  publication.getIdentifier(),
                                  FILE_IDENTIFIER,
                                  customer,
                                  MANAGE_DEGREE_EMBARGO, MANAGE_RESOURCES_STANDARD),
                              output,
                              context);

        GatewayResponse<PresignedUri> gatewayResponse =
            GatewayResponse.fromString(output.toString(), PresignedUri.class);
        assertBasicRestRequirements(gatewayResponse, SC_OK, APPLICATION_JSON);
        assertExpectedResponseBody(gatewayResponse);
    }

    @Test
    void shouldReturnBadGatewayWhenPublicationCanNotBeParsed() throws IOException, InterruptedException {
        var s3Service = getAwsS3ServiceReturningPresignedUrl();
        var publicationService = mockSuccessfulPublicationRequest("<</>");
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());

        handler.handleRequest(createRequest(OWNER_USER_ID, SortableIdentifier.next(), FILE_IDENTIFIER),
                output, context);

        GatewayResponse<PresignedUri> gatewayResponse =
            GatewayResponse.fromString(output.toString(), PresignedUri.class);
        assertBasicRestRequirements(gatewayResponse, HttpStatus.SC_BAD_GATEWAY, APPLICATION_PROBLEM_JSON);
    }

    @ParameterizedTest(name = "Should return presigned URI when mime-type of file is {0}")
    @MethodSource("mimeTypeProvider")
    void shouldReturnOkWhenPublicationUnpublishedAndUserIsOwnerAndMimeTypeIs(String mimeType)
        throws IOException, InterruptedException {
        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();
        var publication = getPublication(DRAFT, mimeType);
        var publicationService = mockSuccessfulPublicationRequest(publication.toString());
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());

        handler.handleRequest(createRequest(publication.getResourceOwner().getOwner().getValue(),
                publication.getIdentifier(), FILE_IDENTIFIER), output, context);

        GatewayResponse<PresignedUri> gatewayResponse =
            GatewayResponse.fromString(output.toString(), PresignedUri.class);
        assertBasicRestRequirements(gatewayResponse, SC_OK, APPLICATION_JSON);
        assertExpectedResponseBody(gatewayResponse);
    }

    // Error message here is odd
    @Test
    void handlerReturnsNotFoundResponseOnUnknownIdentifier() throws IOException, InterruptedException {
        var publicationIdentifier = SortableIdentifier.next();
        var publicationService = mockNotFoundPublicationService(publicationIdentifier);
        var handler = new CreatePresignedDownloadUrlHandler(publicationService,
                                                            getAwsS3ServiceReturningPresignedUrl(), mockEnvironment());
        handler.handleRequest(createRequest(OWNER_USER_ID, publicationIdentifier, FILE_IDENTIFIER), output, context);

        GatewayResponse<Problem> gatewayResponse = GatewayResponse.fromOutputStream(output, Problem.class);
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
                                                            getAwsS3ServiceReturningPresignedUrl(), mockEnvironment());

        handler.handleRequest(createRequest(OWNER_USER_ID, SOME_RANDOM_IDENTIFIER, FILE_IDENTIFIER), output, context);

        GatewayResponse<Problem> gatewayResponse = GatewayResponse.fromOutputStream(output, Problem.class);
        assertBasicRestRequirements(gatewayResponse, SC_BAD_GATEWAY, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse, getBadGatewayProblem(SOME_RANDOM_IDENTIFIER));
    }

    @ParameterizedTest(name = "Unpublished publication with filetype {1} is downloadable by user {0}")
    @MethodSource("userFileTypeSupplier")
    void handlerReturnsOkResponseOnValidInputPublication(String user, File file)
        throws IOException, InterruptedException {

        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();
        var publication = getPublicationWithFile(file);
        var publicationService = mockSuccessfulPublicationRequest(publication.toString());
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());
        var customer = randomUri();
        handler.handleRequest(
            createRequestWithAccessRight(
                user,
                publication.getIdentifier(),
                file.getIdentifier(),
                customer,
                MANAGE_DEGREE_EMBARGO, MANAGE_RESOURCES_STANDARD),
            output,
            context);

        GatewayResponse<PresignedUri> gatewayResponse =
            GatewayResponse.fromString(output.toString(), PresignedUri.class);
        assertBasicRestRequirements(gatewayResponse, SC_OK, APPLICATION_JSON);
        assertExpectedResponseBody(gatewayResponse);
    }

    @Test
    void handlerReturnsNotFoundOnPublicationWithoutFile() throws IOException, InterruptedException {
        var publication = createPublishedPublicationWithoutFileSetFile();
        var publicationService = mockSuccessfulPublicationRequest(publication.toString());
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, getAwsS3ServiceReturningNotFound(),
                                                            mockEnvironment());
        var fileIdentifier = UUID.randomUUID();
        var publicationIdentifier = publication.getIdentifier();
        handler.handleRequest(createRequest(OWNER_USER_ID, publicationIdentifier, fileIdentifier),
                              output, context);

        GatewayResponse<Problem> gatewayResponse = GatewayResponse.fromOutputStream(output, Problem.class);
        assertBasicRestRequirements(gatewayResponse, SC_NOT_FOUND, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse,
                                 getNotFoundPublicationServiceResponse(
                                     notFoundError(publicationIdentifier, fileIdentifier)));
    }

    @Test
    void shouldReturnServiceUnavailableResponseOnS3ServiceException() throws IOException, InterruptedException {
        var publication = getPublication(PUBLISHED);
        var publicationIdentifier = publication.getIdentifier();
        var s3Service = getS3ServiceThrowingSdkClientException(publicationIdentifier);
        var publicationService = mockSuccessfulPublicationRequest(publication.toString());
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());

        handler.handleRequest(
            createRequest(
                publication.getResourceOwner().getOwner().getValue(),
                publicationIdentifier,
                FILE_IDENTIFIER),
            output, context);

        GatewayResponse<Problem> gatewayResponse = GatewayResponse.fromOutputStream(output, Problem.class);
        assertBasicRestRequirements(gatewayResponse, SC_BAD_GATEWAY, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse, getBadGatewayProblem(publicationIdentifier));
    }

    @Test
    void shouldReturnNotFoundOnAnonymousRequestForDraftPublication()
        throws IOException, InterruptedException {
        var publication = getPublication(DRAFT);
        var publicationService = mockSuccessfulPublicationRequest(publication.toString());
        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();

        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());

        handler.handleRequest(createAnonymousRequest(publication.getIdentifier()), output, context);

        GatewayResponse<Problem> gatewayResponse = GatewayResponse.fromOutputStream(output, Problem.class);
        assertBasicRestRequirements(gatewayResponse, SC_NOT_FOUND, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse, getNotFoundPublicationServiceResponse(
                                         notFoundError(publication.getIdentifier(), FILE_IDENTIFIER)));
    }

    @ParameterizedTest
    @MethodSource("badRequestProvider")
    void shouldReturnBadRequestWhenRequestIsBad(InputStream request, String detail) throws IOException,
                                                                                           InterruptedException {
        var publicationService = mockSuccessfulPublicationRequest(getPublication(DRAFT).toString());
        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();

        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());

        handler.handleRequest(request, output, context);

        GatewayResponse<Problem> gatewayResponse = GatewayResponse.fromOutputStream(output, Problem.class);
        assertBasicRestRequirements(gatewayResponse, SC_BAD_REQUEST, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse, getBadRequestPublicationServiceResponse(detail));
    }

    @Test
    void shouldReturnNotFoundWhenPublicationServiceResponseIsNotUnderstood() throws IOException, InterruptedException {
        var publicationService = mockPublicationServiceReturningStrangeResponse();
        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();

        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());
        var publicationIdentifier = SortableIdentifier.next();
        handler.handleRequest(createAnonymousRequest(publicationIdentifier), output, context);

        GatewayResponse<Problem> gatewayResponse = GatewayResponse.fromOutputStream(output, Problem.class);
        assertBasicRestRequirements(gatewayResponse, SC_NOT_FOUND, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse,
                                 getNotFoundPublicationServiceResponse(
                                     EXTERNAL_ERROR_MESSAGE_DECORATION + publicationIdentifier
                                     + " " + ERROR_PUBLICATION_NOT_FOUND_FOR_IDENTIFIER + publicationIdentifier));
    }

    @Test
    void shouldReturnNotFoundWhenPublicationServiceResponseIsUnderstood() throws IOException, InterruptedException {
        var publicationIdentifier = SortableIdentifier.next();
        var publicationService = mockNotFoundPublicationService(publicationIdentifier);
        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();

        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());

        handler.handleRequest(createAnonymousRequest(publicationIdentifier), output, context);

        GatewayResponse<Problem> gatewayResponse = GatewayResponse.fromOutputStream(output, Problem.class);
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
        var publication = PublicationGenerator.randomPublication();
        var publicationService = mockPublicationServiceReturningEmbargoedFile(publication);
        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());
        handler.handleRequest(request, output, context);
        GatewayResponse<Problem> gatewayResponse = GatewayResponse.fromOutputStream(output, Problem.class);
        assertBasicRestRequirements(gatewayResponse, SC_NOT_FOUND, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse, getNotFoundPublicationServiceResponse(
                                         notFoundError(publication.getIdentifier(), FILE_IDENTIFIER)));
    }

    @ParameterizedTest(name = "Should return Not Found when requester is not owner and embargo is in place")
    @MethodSource("nonOwnerProvider")
    void shouldDisallowDownloadByNonOwnerWhenFileHasIsAdministrativeAgreement(InputStream request)
        throws IOException, InterruptedException {
        var publication = PublicationGenerator.randomPublication();
        var publicationService = mockPublicationServiceReturningAdministrativeAgreement(publication);
        AwsS3Service s3Service = getAwsS3ServiceReturningPresignedUrl();
        var handler = new CreatePresignedDownloadUrlHandler(publicationService, s3Service, mockEnvironment());
        handler.handleRequest(request, output, context);
        GatewayResponse<Problem> gatewayResponse = GatewayResponse.fromOutputStream(output, Problem.class);
        assertBasicRestRequirements(gatewayResponse, SC_NOT_FOUND, APPLICATION_PROBLEM_JSON);
        assertProblemEquivalence(gatewayResponse, getNotFoundPublicationServiceResponse(
                notFoundError(publication.getIdentifier(), FILE_IDENTIFIER)));
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
            Arguments.of(OWNER_USER_ID, administrativeAgreement(ADMINISTRATIVE_IDENTIFIER)),
            Arguments.of(OWNER_USER_ID, fileWithTypeUnpublished(FILE_IDENTIFIER)),
            Arguments.of(CURATOR, fileWithEmbargo(EMBARGOED_FILE_IDENTIFIER)),
            Arguments.of(CURATOR, fileWithoutEmbargo(APPLICATION_PDF, UNEMBARGOED_FILE_IDENTIFIER)),
            Arguments.of(CURATOR, administrativeAgreement(ADMINISTRATIVE_IDENTIFIER)),
            Arguments.of(CURATOR, fileWithTypeUnpublished(FILE_IDENTIFIER))
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
            administrativeAgreement(ADMINISTRATIVE_IDENTIFIER),
            fileWithTypeUnpublished(FILE_IDENTIFIER)
        );
    }

    private static Stream<File> fileTypeSupplierRequiringElevatedRights() {
        return Stream.of(
            fileWithEmbargo(EMBARGOED_FILE_IDENTIFIER),
            administrativeAgreement(ADMINISTRATIVE_IDENTIFIER),
            fileWithTypeUnpublished(FILE_IDENTIFIER)
        );
    }

    private static File administrativeAgreement(UUID fileIdentifier) {
        return new AdministrativeAgreement(
                fileIdentifier,
                randomString(),
                APPLICATION_PDF,
                randomInteger().longValue(),
                new License(),
                true,
                PublisherVersion.PUBLISHED_VERSION,
                null,
                NullRightsRetentionStrategy.create(RightsRetentionStrategyConfiguration.UNKNOWN));

    }

    private static File fileWithEmbargo(UUID fileIdentifier) {
        var embargo = Instant.now().plus(Duration.ofDays(3L));
        var publishedDate = Instant.now();
        return new PublishedFile(
                fileIdentifier,
                randomString(),
                APPLICATION_PDF,
                randomInteger().longValue(),
                new License(),
                false,
                PublisherVersion.PUBLISHED_VERSION,
                embargo,
                NullRightsRetentionStrategy.create(RightsRetentionStrategyConfiguration.UNKNOWN),
                randomString(),
                publishedDate);
    }

    private static File fileWithoutEmbargo(String mimeType, UUID fileIdentifier) {
        var publishedDate = Instant.now();
        return new PublishedFile(
                fileIdentifier,
                randomString(),
                mimeType,
                randomInteger().longValue(),
                new License(),
                false,
                PublisherVersion.PUBLISHED_VERSION,
                null,
                NullRightsRetentionStrategy.create(RightsRetentionStrategyConfiguration.UNKNOWN),
                randomString(),
                publishedDate);
    }

    private static File fileWithTypeUnpublished(UUID fileIdentifier) {
        return new UnpublishedFile(
                fileIdentifier,
                randomString(),
                APPLICATION_PDF,
                randomInteger().longValue(),
                new License(),
                false,
                PublisherVersion.PUBLISHED_VERSION,
                null,
                NullRightsRetentionStrategy.create(RightsRetentionStrategyConfiguration.UNKNOWN),
                randomString()
        );

    }

    private static InputStream createAnonymousRequest(SortableIdentifier publicationIdentifier) throws IOException {
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

    private RestPublicationService mockPublicationServiceReturningAdministrativeAgreement(Publication publication)
        throws IOException, InterruptedException {
        publication.setAssociatedArtifacts(new AssociatedArtifactList(
                List.of(administrativeAgreement(FILE_IDENTIFIER))));
        var eventString = dtoObjectMapper.writeValueAsString(publication);
        return mockSuccessfulPublicationRequest(eventString);
    }

    private RestPublicationService mockPublicationServiceReturningEmbargoedFile(Publication publication)
        throws IOException, InterruptedException {
        publication.setAssociatedArtifacts(new AssociatedArtifactList(
                List.of(fileWithEmbargo(FILE_IDENTIFIER))));
        var eventString = dtoObjectMapper.writeValueAsString(publication);
        return mockSuccessfulPublicationRequest(eventString);
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

    private Publication createPublishedPublicationWithoutFileSetFile() {
        var publication = PublicationGenerator.randomPublication();
        publication.setAssociatedArtifacts(new AssociatedArtifactList(emptyList()));
        return publication;
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

    private Publication getPublicationWithFile(File file) {
        var publication = PublicationGenerator.randomPublication();
        publication.setAssociatedArtifacts(new AssociatedArtifactList(file));
        if (hasActiveEmbargo(publication)) {
            var instanceOfDegreeMaster = PublicationInstanceBuilder.randomPublicationInstance(DegreeMaster.class);
            publication.getEntityDescription().getReference().setPublicationInstance(instanceOfDegreeMaster);
        }
        return publication;
    }

    private boolean hasActiveEmbargo(Publication publication) {
        return publication
            .getAssociatedArtifacts().stream()
            .filter(File.class::isInstance)
            .map(File.class::cast)
            .anyMatch(f -> !f.fileDoesNotHaveActiveEmbargo());
    }

    private Publication getPublication(PublicationStatus status) {
        return getPublication(status, APPLICATION_PDF);
    }

    private Publication getPublication(PublicationStatus status, File file) {
        var publication = PublicationGenerator.randomPublication();
        publication.setStatus(status);
        publication.setAssociatedArtifacts(new AssociatedArtifactList(List.of(file)));
        return publication;
    }

    private Publication getPublication(PublicationStatus publicationStatus, String mimeType) {
        var publication = PublicationGenerator.randomPublication();
        publication.setStatus(publicationStatus);
        publication.setAssociatedArtifacts(new AssociatedArtifactList(
                List.of(fileWithoutEmbargo(mimeType, FILE_IDENTIFIER))));
        return publication;
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
