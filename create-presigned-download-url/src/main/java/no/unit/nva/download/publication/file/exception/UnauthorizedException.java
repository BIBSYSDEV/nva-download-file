package no.unit.nva.download.publication.file.exception;

import nva.commons.exceptions.ApiGatewayException;
import org.apache.http.HttpStatus;

public class UnauthorizedException extends ApiGatewayException {

    public UnauthorizedException(String message) {
        super(message);
    }

    @Override
    protected Integer statusCode() {
        return HttpStatus.SC_UNAUTHORIZED;
    }
}
