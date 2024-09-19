package no.unit.nva.download.publication.file.exception;


import no.unit.nva.identifiers.SortableIdentifier;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import org.apache.http.HttpStatus;

import java.util.UUID;

public class NotFoundException extends ApiGatewayException {

    public static final String ERROR_TEMPLATE = "The requested resource \"%s/files/%s\" was not found";

    public NotFoundException(SortableIdentifier resource, UUID file)  {
        super(String.format(ERROR_TEMPLATE, resource, file));
    }

    @Override
    protected Integer statusCode() {
        return HttpStatus.SC_NOT_FOUND;
    }
}
