package no.unit.nva.download.publication.file.exception;

import java.util.UUID;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import org.apache.http.HttpStatus;

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
