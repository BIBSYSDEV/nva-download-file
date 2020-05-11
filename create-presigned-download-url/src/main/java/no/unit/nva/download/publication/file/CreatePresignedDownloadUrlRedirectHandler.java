package no.unit.nva.download.publication.file;

import com.amazonaws.services.lambda.runtime.Context;
import no.unit.nva.download.publication.file.aws.s3.AwsS3Service;
import no.unit.nva.download.publication.file.publication.RestPublicationService;
import no.unit.nva.model.File;
import no.unit.nva.model.Publication;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.handlers.ApiGatewayHandler;
import nva.commons.handlers.RequestInfo;
import nva.commons.utils.Environment;
import nva.commons.utils.JacocoGenerated;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class CreatePresignedDownloadUrlRedirectHandler extends ApiGatewayHandler<Void, Void> {

    private final RestPublicationService publicationService;
    private final AwsS3Service awsS3Service;

    /**
     * Constructor for CreatePresignedDownloadUrlHandler.
     *
     * @param publicationService publicationService
     * @param environment        environment
     */
    public CreatePresignedDownloadUrlRedirectHandler(RestPublicationService publicationService,
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
    public CreatePresignedDownloadUrlRedirectHandler() {
        this(new RestPublicationService(new Environment()), new AwsS3Service(new Environment()), new Environment());
    }

    @Override
    protected Void processInput(Void input, RequestInfo requestInfo, Context context)
            throws ApiGatewayException {
        Publication publication = publicationService.getPublication(
                RequestUtil.getIdentifier(requestInfo),
                RequestUtil.getAuthorization(requestInfo));

        UUID fileIdentifier = RequestUtil.getFileIdentifier(requestInfo);

        HandlerUtil.authorize(requestInfo, publication);

        File file = HandlerUtil.getValidFile(fileIdentifier, publication.getFileSet());

        String presignedDownloadUrl = awsS3Service.createPresignedDownloadUrl(file.getIdentifier().toString(),
                file.getMimeType());

        setAdditionalHeadersSupplier(() -> Map.of(HttpHeaders.LOCATION, presignedDownloadUrl));

        return input;
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, Void output) {
        return HttpStatus.SC_MOVED_TEMPORARILY;
    }

}
