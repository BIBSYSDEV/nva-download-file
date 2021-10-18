package no.unit.nva.download.publication.file;

import com.amazonaws.services.lambda.runtime.Context;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import no.unit.nva.download.publication.file.aws.s3.AwsS3Service;
import no.unit.nva.download.publication.file.exception.NotFoundException;
import no.unit.nva.download.publication.file.publication.RestPublicationService;
import no.unit.nva.download.publication.file.publication.exception.FileNotFoundException;
import no.unit.nva.model.File;
import no.unit.nva.model.FileSet;
import no.unit.nva.model.Publication;
import no.unit.nva.model.PublicationStatus;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.ioutils.IoUtils;
import org.apache.http.HttpStatus;

public class CreatePresignedDownloadUrlHandler extends ApiGatewayHandler<Void, CreatePresignedDownloadUrlResponse> {

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
    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        var inputx = IoUtils.streamToString(input);
        System.out.println(inputx);
        super.handleRequest(IoUtils.stringToStream(inputx), output, context);
    }

    @Override
    protected CreatePresignedDownloadUrlResponse processInput(Void input, RequestInfo requestInfo, Context context)
        throws ApiGatewayException {

        String identifier = RequestUtil.getIdentifier(requestInfo);
        Publication publication = publicationService.getPublication(identifier);
        authorizeIfNotPublished(requestInfo, publication);

        File file = fetchFileDescriptor(requestInfo, publication);
        String presignedDownloadUrl = getPresignedDownloadUrl(file);

        return new CreatePresignedDownloadUrlResponse(presignedDownloadUrl);
    }

    private void authorizeIfNotPublished(RequestInfo requestInfo, Publication publication) throws ApiGatewayException {
        if (!isPublished(publication)) {
            String identifier = RequestUtil.getIdentifier(requestInfo);
            String userId = RequestUtil.getUserIdOptional(requestInfo).orElse(null);
            authorize(identifier, userId, publication);
        }
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, CreatePresignedDownloadUrlResponse output) {
        return HttpStatus.SC_OK;
    }

    private File fetchFileDescriptor(RequestInfo requestInfo, Publication publication) throws ApiGatewayException {
        UUID fileIdentifier = RequestUtil.getFileIdentifier(requestInfo);
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

    private void authorize(String identifier, String userId, Publication publication) throws ApiGatewayException {
        if (isPublished(publication) || userIsOwner(userId, publication)) {
            return;
        }
        throw new NotFoundException(identifier);
    }

    private boolean userIsOwner(String userId, Publication publication) {
        return publication.getOwner().equals(userId);
    }

    private boolean isPublished(Publication publication) {
        return publication.getStatus().equals(PublicationStatus.PUBLISHED);
    }
}
