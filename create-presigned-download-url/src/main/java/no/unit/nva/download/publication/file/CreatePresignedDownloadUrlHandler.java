package no.unit.nva.download.publication.file;

import static java.net.HttpURLConnection.HTTP_OK;
import static no.unit.nva.download.publication.file.RequestUtil.getFileIdentifier;
import static no.unit.nva.download.publication.file.RequestUtil.getUser;
import static nva.commons.apigateway.AccessRight.EDIT_OWN_INSTITUTION_RESOURCES;
import com.amazonaws.services.lambda.runtime.Context;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import no.unit.nva.download.publication.file.aws.s3.AwsS3Service;
import no.unit.nva.download.publication.file.exception.NotFoundException;
import no.unit.nva.download.publication.file.publication.RestPublicationService;
import no.unit.nva.download.publication.file.publication.exception.InputException;
import no.unit.nva.model.Publication;
import no.unit.nva.model.PublicationStatus;
import no.unit.nva.model.Username;
import no.unit.nva.model.associatedartifacts.file.File;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.SingletonCollector;

public class CreatePresignedDownloadUrlHandler extends ApiGatewayHandler<Void, PresignedUri> {

    public static final int DEFAULT_EXPIRATION_SECONDS = 10;

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
    protected PresignedUri processInput(Void input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {

        var publication = publicationService.getPublication(RequestUtil.getIdentifier(requestInfo));
        var file = getFileInformation(publication, requestInfo);
        var expiration = defaultExpiration();
        return new PresignedUri(getPresignedDownloadUrl(file, expiration), expiration);
    }

    private File getFileInformation(Publication publication, RequestInfo requestInfo)
        throws NotFoundException, InputException {

        var fileIdentifier = getFileIdentifier(requestInfo);
        if (publication.getAssociatedArtifacts().isEmpty()) {
            throw new NotFoundException(publication.getIdentifier(), fileIdentifier);
        }
        return
            publication.getAssociatedArtifacts().stream()
                .filter(File.class::isInstance)
                .map(File.class::cast)
                .filter(element -> findByIdentifier(fileIdentifier, element))
                .map(element -> getFile(element, publication, requestInfo))
                .collect(SingletonCollector.collect())
                .orElseThrow(() -> new NotFoundException(publication.getIdentifier(), fileIdentifier));
    }

    private boolean findByIdentifier(UUID fileIdentifier, File element) {
        return fileIdentifier.equals(element.getIdentifier());
    }

    private boolean hasReadAccess(File file, Username owner, PublicationStatus status, RequestInfo requestInfo) {

        var isEmbargoReader = requestInfo.userIsAuthorized(AccessRight.PUBLISH_THESIS_EMBARGO_READ.toString());
        var isOwner = owner.equals(getUser(requestInfo));
        var hasActiveEmbargo = !file.fileDoesNotHaveActiveEmbargo();

        if (hasActiveEmbargo) {
            return isOwner || isEmbargoReader;
        }

        var isPublished = PublicationStatus.PUBLISHED.equals(status);
        var isEditor = requestInfo.userIsAuthorized(EDIT_OWN_INSTITUTION_RESOURCES.toString());

        return isOwner || isEditor || isPublished && file.isVisibleForNonOwner();
    }

    private Optional<File> getFile(File file, Publication publication, RequestInfo requestInfo) {
        return
            hasReadAccess(file, publication.getResourceOwner().getOwner(), publication.getStatus(), requestInfo)
                ? Optional.of(file)
                : Optional.empty();
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, PresignedUri output) {
        return HTTP_OK;
    }

    private String getPresignedDownloadUrl(File file, Date expiration) throws ApiGatewayException {
        return awsS3Service.createPresignedDownloadUrl(file.getIdentifier().toString(), file.getMimeType(), expiration);
    }

    private Date defaultExpiration() {
        return Date.from(Instant.now().plus(DEFAULT_EXPIRATION_SECONDS, ChronoUnit.SECONDS));
    }
}
