package no.unit.nva.download.publication.file;

import com.amazonaws.services.lambda.runtime.Context;

import java.util.Optional;
import java.util.UUID;

import no.unit.nva.download.publication.file.aws.s3.AwsS3Service;
import no.unit.nva.download.publication.file.publication.PublicationResponse;
import no.unit.nva.download.publication.file.publication.RestPublicationService;
import no.unit.nva.download.publication.file.exception.NotFoundException;
import no.unit.nva.file.model.File;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;

import static java.net.HttpURLConnection.HTTP_OK;
import static no.unit.nva.download.publication.file.RequestUtil.getFileIdentifier;
import static no.unit.nva.download.publication.file.RequestUtil.getUser;

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
        var publication = publicationService.getPublication(RequestUtil.getIdentifier(requestInfo));
        var file = getFileInformation(getUser(requestInfo), getFileIdentifier(requestInfo), publication);
        return new PresignedUriResponse(getPresignedDownloadUrl(file));
    }

    private File getFileInformation(String user, UUID fileIdentifier, PublicationResponse publication) throws
            NotFoundException {
        return publication.getFileSet().getFiles().stream()
                .filter(element -> fileIdentifier.equals(element.getIdentifier()))
                .reduce(this::checkForDuplicates)
                .map(file -> getFile(file, user, publication))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .orElseThrow(() -> new NotFoundException(publication.getIdentifier(), fileIdentifier));
    }

    private boolean isFindable(String user, File file, PublicationResponse publicationResponse) {
        return publicationResponse.isOwner(user) || publicationResponse.isPublished() && file.isVisibleForNonOwner();
    }

    private Optional<File> getFile(File file, String user, PublicationResponse publicationResponse) {
        return isFindable(user, file, publicationResponse) ? Optional.of(file) : Optional.empty();
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, PresignedUriResponse output) {
        return HTTP_OK;
    }

    private String getPresignedDownloadUrl(File file) throws ApiGatewayException {
        return awsS3Service.createPresignedDownloadUrl(file.getIdentifier().toString(), file.getMimeType());
    }

    private File checkForDuplicates(File first, File second) {
        throw new IllegalStateException(ERROR_DUPLICATE_FILES_IN_PUBLICATION);
    }
}
