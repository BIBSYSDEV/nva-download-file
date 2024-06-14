package no.unit.nva.download.publication.file.aws.s3.exception;

import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.JacocoGenerated;
import org.apache.http.HttpStatus;

@JacocoGenerated
public class S3ServiceException extends ApiGatewayException {

    public S3ServiceException(String message, Exception exception) {
        super(exception, message);
    }

    @Override
    protected Integer statusCode() {
        return HttpStatus.SC_BAD_GATEWAY;
    }
}