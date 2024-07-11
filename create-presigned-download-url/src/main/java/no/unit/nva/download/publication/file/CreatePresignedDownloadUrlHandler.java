package no.unit.nva.download.publication.file;

import static java.net.HttpURLConnection.HTTP_OK;
import static no.unit.nva.download.publication.file.RequestUtil.getFileIdentifier;
import static no.unit.nva.download.publication.file.RequestUtil.getUser;
import static nva.commons.apigateway.AccessRight.MANAGE_DEGREE_EMBARGO;
import static nva.commons.apigateway.AccessRight.MANAGE_RESOURCES_STANDARD;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.github.bibsysdev.urlshortener.service.UriShortener;
import com.github.bibsysdev.urlshortener.service.UriShortenerImpl;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import no.unit.nva.download.publication.file.aws.s3.AwsS3Service;
import no.unit.nva.download.publication.file.exception.NotFoundException;
import no.unit.nva.download.publication.file.publication.RestPublicationService;
import no.unit.nva.download.publication.file.publication.exception.InputException;
import no.unit.nva.download.publication.file.publication.model.File;
import no.unit.nva.download.publication.file.publication.model.Publication;
import no.unit.nva.download.publication.file.publication.model.PublicationStatusConstants;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;

public class CreatePresignedDownloadUrlHandler extends ApiGatewayHandler<Void, PresignedUri> {

    public static final int DEFAULT_EXPIRATION_SECONDS = 180;
    private final RestPublicationService publicationService;
    private final AwsS3Service awsS3Service;
    private final UriShortener uriShortener;

    /**
     * Constructor for CreatePresignedDownloadUrlHandler.
     *
     * @param publicationService publicationService
     * @param environment        environment
     */
    public CreatePresignedDownloadUrlHandler(RestPublicationService publicationService, AwsS3Service awsS3Service,
                                             Environment environment, UriShortener uriShortener) {
        super(Void.class, environment);
        this.publicationService = publicationService;
        this.awsS3Service = awsS3Service;
        this.uriShortener = uriShortener;
    }

    /**
     * Default constructor for CreatePresignedDownloadUrlHandler.
     */
    @JacocoGenerated
    public CreatePresignedDownloadUrlHandler() {
        this(new RestPublicationService(new Environment()),
             new AwsS3Service(new Environment()),
             new Environment(),
             UriShortenerImpl.createDefault());
    }

    @Override
    protected PresignedUri processInput(Void input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {

        var publication = publicationService.getPublication(RequestUtil.getIdentifier(requestInfo));
        var file = getFileInformation(publication, requestInfo);
        var expiration = defaultExpiration();
        var presignUriLong = getPresignedDownloadUrl(file, expiration);
        var shortenedPresignUri = getShortenedVersion(presignUriLong, expiration);
        return new PresignedUri(presignUriLong, expiration.toInstant(),
                                shortenedPresignUri);
    }

    private String getShortenedVersion(String presignUriLong, Date expiration) {
        var shortenedUri = uriShortener.shorten(UriWrapper.fromUri(presignUriLong).getUri(), expiration.toInstant());
        return shortenedUri.toString();
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, PresignedUri output) {
        return HTTP_OK;
    }

    private File getFileInformation(Publication publication, RequestInfo requestInfo)
        throws NotFoundException, InputException {

        var fileIdentifier = getFileIdentifier(requestInfo);
        if (publication.associatedArtifacts().isEmpty()) {
            throw new NotFoundException(publication.identifier(), fileIdentifier);
        }

        return publication.associatedArtifacts().stream()
                   .filter(File.class::isInstance)
                   .map(File.class::cast)
                   .filter(element -> findByIdentifier(fileIdentifier, element))
                   .map(element -> getFile(element, publication, requestInfo))
                   .filter(Optional::isPresent)
                   .map(Optional::get)
                   .findFirst()
                   .orElseThrow(() -> new NotFoundException(publication.identifier(), fileIdentifier));
    }

    private boolean findByIdentifier(UUID fileIdentifier, File element) {
        return fileIdentifier.equals(element.getIdentifier());
    }

    private boolean hasReadAccess(File file, Publication publication, RequestInfo requestInfo) {

        var isThesisAndEmbargoThesisReader =
            isThesis(publication) && requestInfo.userIsAuthorized(MANAGE_DEGREE_EMBARGO);
        var isOwner = publication.resourceOwner().owner().equals(getUser(requestInfo));
        var hasActiveEmbargo = file.hasActiveEmbargo();

        if (hasActiveEmbargo) {
            return isOwner || isThesisAndEmbargoThesisReader;
        }

        var isPublished = PublicationStatusConstants.PUBLISHED.equals(publication.status());
        var isEditor = requestInfo.userIsAuthorized(MANAGE_RESOURCES_STANDARD);

        return isOwner || isEditor || isPublished && file.isVisibleForNonOwner();
    }

    private Optional<File> getFile(File file, Publication publication, RequestInfo requestInfo) {
        return hasReadAccess(file, publication, requestInfo)
                   ? Optional.of(file)
                   : Optional.empty();
    }

    private boolean isThesis(Publication publication) {
        var kind = attempt(() -> publication
                                     .entityDescription()
                                     .reference()
                                     .publicationInstance()).toOptional();
        return kind.isPresent() && ("DegreeBachelor".equals(kind.get().type())
                                    ||
                                    "DegreeMaster".equals(kind.get().type())
                                    ||
                                    "DegreePhd".equals(kind.get().type()));
    }

    private String getPresignedDownloadUrl(File file, Date expiration) throws ApiGatewayException {
        return awsS3Service.createPresignedDownloadUrl(file.getIdentifier().toString(), file.getMimeType(), expiration);
    }

    private Date defaultExpiration() {
        return Date.from(Instant.now().plus(DEFAULT_EXPIRATION_SECONDS, ChronoUnit.SECONDS));
    }
}