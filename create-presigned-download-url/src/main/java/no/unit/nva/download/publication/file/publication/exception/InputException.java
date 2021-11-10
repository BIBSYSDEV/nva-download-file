package no.unit.nva.download.publication.file.publication.exception;

import nva.commons.apigateway.exceptions.ApiGatewayException;

public class InputException extends ApiGatewayException {

    public InputException(String message) {
        super(message);
    }

    @Override
    protected Integer statusCode() {
        return 400;
    }

    public InputException(String message, Exception exception) {
        super(exception, message);
    }
}
