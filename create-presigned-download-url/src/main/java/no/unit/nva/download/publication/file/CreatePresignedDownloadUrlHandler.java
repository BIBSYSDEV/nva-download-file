package no.unit.nva.download.publication.file;

import com.amazonaws.services.lambda.runtime.Context;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import no.unit.nva.download.publication.file.aws.s3.AwsS3Service;
import no.unit.nva.download.publication.file.exception.NotFoundException;
import no.unit.nva.download.publication.file.publication.PublicationResponse;
import no.unit.nva.download.publication.file.publication.PublicationStatus;
import no.unit.nva.download.publication.file.publication.RestPublicationService;
import no.unit.nva.download.publication.file.publication.exception.FileNotFoundException;
import no.unit.nva.file.model.File;
import no.unit.nva.file.model.FileSet;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.apache.http.HttpStatus;

public class CreatePresignedDownloadUrlHandler extends ApiGatewayHandler<Void, PresignedUriResponse> {

    public static final String ERROR_MISSING_FILE_IN_PUBLICATION_FILE_SET = "File not found in publication file set";
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
        return new PresignedUriResponse(getPreSignedUriForFile(fileIdentifier, publication));
    }

    private PublicationResponse getPublicationResponse(String user, String identifier) throws ApiGatewayException {
        PublicationResponse publication = publicationService.getPublication(identifier);
        authorizeIfNotPublished(user, identifier, publication);
        return publication;
    }

    private String getPreSignedUriForFile(UUID fileIdentifier, PublicationResponse publication)
            throws ApiGatewayException {
        File file = fetchFileDescriptor(fileIdentifier, publication);
        return getPresignedDownloadUrl(file);
    }

    private void authorizeIfNotPublished(String user, String identifier, PublicationResponse publication)
            throws ApiGatewayException {
        if (!isPublished(publication)) {
            authorize(identifier, user, publication);
        }
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, PresignedUriResponse output) {
        return HttpStatus.SC_OK;
    }

    private File fetchFileDescriptor(UUID fileIdentifier, PublicationResponse publication)
            throws ApiGatewayException {
        return getValidFile(fileIdentifier, publication.getFileSet());
    }

    private String getPresignedDownloadUrl(File file) throws ApiGatewayException {
        return awsS3Service.createPresignedDownloadUrl(file.getIdentifier().toString(), file.getMimeType());
    }

    private File getValidFile(UUID fileIdentifier, FileSet fileSet) throws ApiGatewayException {

        List<File> files = Optional.ofNullable(fileSet.getFiles()).orElse(Collections.emptyList());

        return files.stream()
            .filter(f -> f.getIdentifier().equals(fileIdentifier))
            .reduce((a, b) -> {
                throw new IllegalStateException(ERROR_DUPLICATE_FILES_IN_PUBLICATION);
            })
            .orElseThrow(() -> new FileNotFoundException(ERROR_MISSING_FILE_IN_PUBLICATION_FILE_SET));
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
