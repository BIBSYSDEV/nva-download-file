package no.unit.nva.download.publication.file;

import com.amazonaws.services.lambda.runtime.Context;
import no.unit.nva.download.publication.file.aws.s3.AwsS3Service;
import no.unit.nva.download.publication.file.exception.UnauthorizedException;
import no.unit.nva.download.publication.file.publication.RestPublicationService;
import no.unit.nva.download.publication.file.publication.exception.FileNotFoundException;
import no.unit.nva.model.File;
import no.unit.nva.model.Publication;
import no.unit.nva.model.PublicationStatus;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.handlers.ApiGatewayHandler;
import nva.commons.handlers.RequestInfo;
import nva.commons.utils.Environment;
import org.apache.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CreatePresignedDownloadUrlHandler extends ApiGatewayHandler<Void, CreatePresignedDownloadUrlResponse> {

    public static final String ERROR_MISSING_FILE_IN_PUBLICATION_FILE_SET = "File not found in publication file set";
    public static final String ERROR_MISSING_FILES_IN_PUBLICATION = "Publication does not have any associated files";
    public static final String ERROR_UNAUTHORIZED = "";

    private final RestPublicationService publicationService;
    private final AwsS3Service awsS3Service;

    /**
     * Constructor for MainHandler.
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
     * Default constructor for MainHandler.
     */
    public CreatePresignedDownloadUrlHandler() {
        this(new RestPublicationService(new Environment()), new AwsS3Service(new Environment()), new Environment());
    }

    @Override
    protected CreatePresignedDownloadUrlResponse processInput(Void input, RequestInfo requestInfo, Context context)
            throws ApiGatewayException {
        Publication publication = publicationService.getPublication(
                RequestUtil.getIdentifier(requestInfo),
                RequestUtil.getAuthorization(requestInfo));

        checkAuthorization(requestInfo, publication);

        File file = validateFile(requestInfo, publication);

        String presignedDownloadUrl = awsS3Service.createPresignedDownloadUrl(file.getIdentifier().toString(),
                file.getMimeType());

        return new CreatePresignedDownloadUrlResponse(presignedDownloadUrl);

    }

    private File validateFile(RequestInfo requestInfo, Publication publication) throws ApiGatewayException {
        UUID fileIdentifier = RequestUtil.getFileIdentifier(requestInfo);

        Optional<List<File>> files = Optional.ofNullable(publication.getFileSet().getFiles());

        if (files.isPresent()) {
            return files.get().stream()
                    .filter(f -> f.getIdentifier().equals(fileIdentifier))
                    .findAny()
                    .orElseThrow(() -> new FileNotFoundException(ERROR_MISSING_FILE_IN_PUBLICATION_FILE_SET));
        }
        throw new FileNotFoundException(ERROR_MISSING_FILES_IN_PUBLICATION);
    }

    protected void checkAuthorization(RequestInfo requestInfo, Publication publication) throws ApiGatewayException {
        if (publication.getStatus().equals(PublicationStatus.PUBLISHED)) {
            return;
        } else if (RequestUtil.getOwner(requestInfo).equalsIgnoreCase(publication.getOwner())) {
            return;
        }
        throw new UnauthorizedException(ERROR_UNAUTHORIZED);
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, CreatePresignedDownloadUrlResponse output) {
        return HttpStatus.SC_CREATED;
    }

}
