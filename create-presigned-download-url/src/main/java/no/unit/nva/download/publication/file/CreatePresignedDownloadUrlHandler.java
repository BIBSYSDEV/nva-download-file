package no.unit.nva.download.publication.file;

import static java.net.HttpURLConnection.HTTP_OK;
import static no.unit.nva.download.publication.file.RequestUtil.getFileIdentifier;
import com.amazonaws.services.lambda.runtime.Context;
import com.github.bibsysdev.urlshortener.service.UriShortener;
import com.github.bibsysdev.urlshortener.service.UriShortenerImpl;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import no.unit.nva.download.publication.file.aws.s3.AwsS3Service;
import no.unit.nva.download.publication.file.publication.RestPublicationService;
import no.unit.nva.download.publication.file.publication.model.File;
import no.unit.nva.download.publication.file.publication.model.Publication;
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
        this(new RestPublicationService(new Environment()), new AwsS3Service(new Environment()), new Environment(),
             UriShortenerImpl.createDefault());
    }

    @Override
    protected void validateRequest(Void unused, RequestInfo requestInfo, Context context) {
        //Do nothing
    }

    @Override
    protected PresignedUri processInput(Void input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {

        var publication = publicationService.getPublication(RequestUtil.getIdentifier(requestInfo));
        var fileIdentifier = getFileIdentifier(requestInfo);

        FileAccessValidationUtil.create(fileIdentifier, publication).validateAccess(requestInfo);

        return createPresignedUrl(publication, fileIdentifier);
    }

    private PresignedUri createPresignedUrl(Publication publication, UUID fileIdentifier) throws ApiGatewayException {
        var file = publication.getFile(fileIdentifier);
        var expiration = defaultExpiration();
        var presignUriLong = getPresignedDownloadUrl(file, expiration);
        var shortenedPresignUri = getShortenedVersion(presignUriLong, expiration);
        return new PresignedUri(presignUriLong, expiration.toInstant(), shortenedPresignUri);
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, PresignedUri output) {
        return HTTP_OK;
    }

    private String getShortenedVersion(String presignUriLong, Date expiration) {
        return uriShortener.shorten(UriWrapper.fromUri(presignUriLong).getUri(), expiration.toInstant()).toString();
    }

    private String getPresignedDownloadUrl(File file, Date expiration) throws ApiGatewayException {
        return awsS3Service.createPresignedDownloadUrl(file.getIdentifier().toString(), file.getMimeType(), expiration);
    }

    private Date defaultExpiration() {
        return Date.from(Instant.now().plus(DEFAULT_EXPIRATION_SECONDS, ChronoUnit.SECONDS));
    }
}