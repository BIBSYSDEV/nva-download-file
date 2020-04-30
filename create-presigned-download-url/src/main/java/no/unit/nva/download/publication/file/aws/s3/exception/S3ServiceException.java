package no.unit.nva.download.publication.file.aws.s3.exception;

import nva.commons.exceptions.ApiGatewayException;
import org.apache.http.HttpStatus;

public class S3ServiceException extends ApiGatewayException {

    public S3ServiceException(String message, Exception exception) {
        super(exception, message);
    }

    @Override
    protected Integer statusCode() {
        return HttpStatus.SC_SERVICE_UNAVAILABLE;
    }
}