package no.unit.nva.download.publication.file.exception;

import nva.commons.exceptions.ApiGatewayException;
import org.apache.http.HttpStatus;

public class NotFoundException extends ApiGatewayException {

    public static final String RESOURCE_NOT_FOUND = "Resource not found: ";

    public NotFoundException(String identifier) {
        super(RESOURCE_NOT_FOUND + identifier);
    }

    @Override
    protected Integer statusCode() {
        return HttpStatus.SC_NOT_FOUND;
    }
}
