package no.unit.nva.download.publication.file;

import static nva.commons.utils.attempt.Try.attempt;

import com.amazonaws.services.lambda.runtime.Context;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreatePresignedDownloadUrlHandler extends ApiGatewayHandler<Void,
        CreatePresignedDownloadUrlResponse> {

    public static final String ERROR_MISSING_FILE_IN_PUBLICATION_FILE_SET = "File not found in publication file set";
    public static final String ERROR_DUPLICATE_FILES_IN_PUBLICATION = "Publication contains duplicate files";
    public static final String ERROR_MISSING_FILES_IN_PUBLICATION = "Publication does not have any associated files";
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

        var publication = attempt(() -> RequestUtil.getAuthorization(requestInfo))
            .map(authToken -> fetchPublilcationWithAuthorizationToken(requestInfo, authToken))
            .orElse(fail -> fetchPublicationWithoutAuthorizationToken(requestInfo));

        UUID fileIdentifier = RequestUtil.getFileIdentifier(requestInfo);

        authorize(requestInfo, publication);

        File file = getValidFile(fileIdentifier, publication.getFileSet());

        String presignedDownloadUrl = awsS3Service.createPresignedDownloadUrl(file.getIdentifier().toString(),
            file.getMimeType());

        return new CreatePresignedDownloadUrlResponse(presignedDownloadUrl);
    }

    private Publication fetchPublicationWithoutAuthorizationToken(RequestInfo requestInfo) throws ApiGatewayException {
        return publicationService.getPublicationWithoutAuthorizationToken(RequestUtil.getIdentifier(requestInfo));
    }

    private Publication fetchPublilcationWithAuthorizationToken(RequestInfo requestInfo, String authToken)
        throws ApiGatewayException {
        return publicationService.getPublicationWithAuthorizationToken(
            RequestUtil.getIdentifier(requestInfo), authToken);
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, CreatePresignedDownloadUrlResponse output) {
        return HttpStatus.SC_OK;
    }

    /**
     * Get valid file from publication.
     *
     * @param fileIdentifier fileIdentifier
     * @param fileSet fileSet
     * @return valid file
     * @throws ApiGatewayException exception thrown if valid file not present
     */
    private File getValidFile(UUID fileIdentifier, FileSet fileSet) throws ApiGatewayException {

        Optional<List<File>> files = Optional.ofNullable(fileSet.getFiles());

        if (files.isPresent()) {
            return files.get().stream()
                    .filter(f -> f.getIdentifier().equals(fileIdentifier))
                    .reduce((a, b) -> {
                        throw new IllegalStateException(ERROR_DUPLICATE_FILES_IN_PUBLICATION);
                    })
                    .orElseThrow(() -> new FileNotFoundException(ERROR_MISSING_FILE_IN_PUBLICATION_FILE_SET));
        }
        throw new FileNotFoundException(ERROR_MISSING_FILES_IN_PUBLICATION);
    }

    /**
     * Authorize request if publication is published or user is publication owner.
     *
     * @param requestInfo requestInfo
     * @param publication publication
     * @throws ApiGatewayException when authorization fails.
     */
    private void authorize(RequestInfo requestInfo, Publication publication) throws ApiGatewayException {
        if (publication.getStatus().equals(PublicationStatus.PUBLISHED)) {
            return;
        } else if (RequestUtil.getUserId(requestInfo).equalsIgnoreCase(publication.getOwner())) {
            return;
        }
        throw new UnauthorizedException(ERROR_UNAUTHORIZED);
    }

}
