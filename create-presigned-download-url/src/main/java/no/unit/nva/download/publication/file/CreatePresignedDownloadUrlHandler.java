package no.unit.nva.download.publication.file;

import com.amazonaws.services.lambda.runtime.Context;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import no.unit.nva.download.publication.file.aws.s3.AwsS3Service;
import no.unit.nva.download.publication.file.exception.UnauthorizedException;
import no.unit.nva.download.publication.file.publication.RestPublicationService;
import no.unit.nva.download.publication.file.publication.exception.FileNotFoundException;
import no.unit.nva.model.File;
import no.unit.nva.model.FileSet;
import no.unit.nva.model.Publication;
import no.unit.nva.model.PublicationStatus;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.handlers.ApiGatewayHandler;
import nva.commons.handlers.RequestInfo;
import nva.commons.utils.Environment;
import nva.commons.utils.JacocoGenerated;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreatePresignedDownloadUrlHandler extends ApiGatewayHandler<Void, CreatePresignedDownloadUrlResponse> {

    public static final String ERROR_MISSING_FILE_IN_PUBLICATION_FILE_SET = "File not found in publication file set";
    public static final String ERROR_DUPLICATE_FILES_IN_PUBLICATION = "Publication contains duplicate files";
    public static final String ERROR_UNAUTHORIZED = "User is not authorized to view the resource";

    private static final Logger logger = LoggerFactory.getLogger(CreatePresignedDownloadUrlHandler.class);
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
        super(Void.class, environment, logger);
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
    protected CreatePresignedDownloadUrlResponse processInput(Void input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {

        Publication publication = fetchPublicationForAuthorizedUser(requestInfo);

        File file = fetchFileDescriptor(requestInfo, publication);
        String presignedDownloadUrl = fetchUrlFromS3(file);

        return new CreatePresignedDownloadUrlResponse(presignedDownloadUrl);
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, CreatePresignedDownloadUrlResponse output) {
        return HttpStatus.SC_OK;
    }

    private Publication fetchPublicationForAuthorizedUser(RequestInfo requestInfo) throws ApiGatewayException {
        Publication publication = fetchPublication(requestInfo);
        authorize(requestInfo, publication);
        return publication;
    }

    private File fetchFileDescriptor(RequestInfo requestInfo, Publication publication) throws ApiGatewayException {
        UUID fileIdentifier = RequestUtil.getFileIdentifier(requestInfo);
        return getValidFile(fileIdentifier, publication.getFileSet());
    }

    private Publication fetchPublication(RequestInfo requestInfo) throws ApiGatewayException {
        String authToken = extractAuthToken(requestInfo);
        return fetchPublication(requestInfo, authToken);
    }

    private Publication fetchPublication(RequestInfo requestInfo, String authToken)
            throws ApiGatewayException {
        return publicationService.getPublication(RequestUtil.getIdentifier(requestInfo), authToken);
    }

    private String extractAuthToken(RequestInfo requestInfo) throws ApiGatewayException {
        return RequestUtil.getAuthorization(requestInfo);
    }

    private String fetchUrlFromS3(File file) throws ApiGatewayException {
        return awsS3Service.createPresignedDownloadUrl(file.getIdentifier().toString(), file.getMimeType());
    }

    private File getValidFile(UUID fileIdentifier, FileSet fileSet) throws ApiGatewayException {

        List<File> files = Optional.ofNullable(fileSet.getFiles()).orElse(Collections.emptyList());

        return files.stream()
            .filter(f -> f.getIdentifier().equals(fileIdentifier))
            .reduce((a, b) -> {
                throw oneItemExpected();
            })
            .orElseThrow(this::fileNotFound);
    }

    private FileNotFoundException fileNotFound() {
        return new FileNotFoundException(ERROR_MISSING_FILE_IN_PUBLICATION_FILE_SET);
    }

    private IllegalStateException oneItemExpected() {
        return new IllegalStateException(ERROR_DUPLICATE_FILES_IN_PUBLICATION);
    }

    private void authorize(RequestInfo requestInfo, Publication publication) throws ApiGatewayException {
        if (isPublished(publication) || userIsOwner(requestInfo, publication)) {
            return;
        }
        throw new UnauthorizedException(ERROR_UNAUTHORIZED);
    }

    private boolean userIsOwner(RequestInfo requestInfo, Publication publication) throws ApiGatewayException {
        return RequestUtil.getUserId(requestInfo).equalsIgnoreCase(publication.getOwner());
    }

    private boolean isPublished(Publication publication) {
        return publication.getStatus().equals(PublicationStatus.PUBLISHED);
    }
}
