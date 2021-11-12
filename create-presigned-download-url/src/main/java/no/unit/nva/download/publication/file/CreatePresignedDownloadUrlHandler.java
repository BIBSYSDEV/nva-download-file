package no.unit.nva.download.publication.file;

import com.amazonaws.services.lambda.runtime.Context;

import java.time.Instant;
import java.util.UUID;
import java.util.function.BinaryOperator;

import no.unit.nva.download.publication.file.aws.s3.AwsS3Service;
import no.unit.nva.download.publication.file.exception.NotFoundException;
import no.unit.nva.download.publication.file.publication.PublicationResponse;
import no.unit.nva.download.publication.file.publication.PublicationStatus;
import no.unit.nva.download.publication.file.publication.RestPublicationService;
import no.unit.nva.download.publication.file.publication.exception.FileNotFoundException;
import no.unit.nva.file.model.File;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;

import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.Objects.nonNull;

public class CreatePresignedDownloadUrlHandler extends ApiGatewayHandler<Void, PresignedUriResponse> {

    public static final String ERROR_DUPLICATE_FILES_IN_PUBLICATION = "Publication contains duplicate files";

    private final RestPublicationService publicationService;
    private final AwsS3Service awsS3Service;

    /**
     * Constructor for CreatePresignedDownloadUrlHandler.
     *
     * @param publicationService publicationService
     * @param environment        environment
     */
    public CreatePresignedDownloadUrlHandler(RestPublicationService publicationService,
                                             AwsS3Service awsS3Service,
                                             Environment environment) {
        super(Void.class, environment);
        this.publicationService = publicationService;
        this.awsS3Service = awsS3Service;
    }

    /**
     * Default constructor for CreatePresignedDownloadUrlHandler.
     */
    @JacocoGenerated
    public CreatePresignedDownloadUrlHandler() {
        this(new RestPublicationService(new Environment()), new AwsS3Service(new Environment()), new Environment());
    }

    @Override
    protected PresignedUriResponse processInput(Void input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {
        var user = RequestUtil.getUser(requestInfo);
        var identifier = RequestUtil.getIdentifier(requestInfo);
        var fileIdentifier = RequestUtil.getFileIdentifier(requestInfo);
        PublicationResponse publication = getPublicationResponse(user, identifier);
        var file = getFileInformation(user, fileIdentifier, publication);
        return new PresignedUriResponse(getPresignedDownloadUrl(file));
    }

    private File getFileInformation(String user, UUID fileIdentifier, PublicationResponse publication) throws
            FileNotFoundException {
        var file = publication.getFileSet().getFiles().stream()
                .filter(element -> fileIdentifier.equals(element.getIdentifier()))
                .reduce(checkForDuplicates())
                .orElseThrow(() -> new FileNotFoundException(fileIdentifier));
        checkEmbargo(file, isOwner(user, publication));
        return file;
    }

    private boolean isOwner(String user, PublicationResponse publication) {
        return nonNull(user) && user.equals(publication.getOwner());
    }

    private void checkEmbargo(File file, boolean isOwner) throws FileNotFoundException {
        var embargoDate = file.getEmbargoDate();
        if (!isOwner && nonNull(embargoDate) && Instant.now().isBefore(file.getEmbargoDate())) {
            throw new FileNotFoundException(file.getIdentifier());
        }
    }

    private PublicationResponse getPublicationResponse(String user, String identifier) throws ApiGatewayException {
        PublicationResponse publication = publicationService.getPublication(identifier);
        authorizeIfNotPublished(user, identifier, publication);
        return publication;
    }

    private void authorizeIfNotPublished(String user, String identifier, PublicationResponse publication)
            throws ApiGatewayException {
        if (!isPublished(publication)) {
            authorize(identifier, user, publication);
        }
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, PresignedUriResponse output) {
        return HTTP_OK;
    }

    private String getPresignedDownloadUrl(File file) throws ApiGatewayException {
        return awsS3Service.createPresignedDownloadUrl(file.getIdentifier().toString(), file.getMimeType());
    }

    private BinaryOperator<File> checkForDuplicates() {
        return (a, b) -> {
            throw new IllegalStateException(ERROR_DUPLICATE_FILES_IN_PUBLICATION);
        };
    }

    private void authorize(String identifier, String user, PublicationResponse publication)
            throws ApiGatewayException {
        if (userIsOwner(user, publication)) {
            return;
        }
        throw new NotFoundException(identifier);
    }

    private boolean userIsOwner(String user, PublicationResponse publication) {
        return publication.getOwner().equals(user);
    }

    private boolean isPublished(PublicationResponse publication) {
        return publication.getStatus().equals(PublicationStatus.PUBLISHED);
    }
}
