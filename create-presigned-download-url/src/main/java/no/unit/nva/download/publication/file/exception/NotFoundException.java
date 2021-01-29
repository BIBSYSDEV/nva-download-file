package no.unit.nva.download.publication.file.exception;

import nva.commons.exceptions.ApiGatewayException;
import org.apache.http.HttpStatus;

import java.util.UUID;

public class NotFoundException extends ApiGatewayException {

    public static final String RESOURCE_NOT_FOUND = "Resource not found: ";

    public NotFoundException(UUID identifier) {
        super(RESOURCE_NOT_FOUND + identifier);
    }

    @Override
    protected Integer statusCode() {
        return HttpStatus.SC_NOT_FOUND;
    }
}
