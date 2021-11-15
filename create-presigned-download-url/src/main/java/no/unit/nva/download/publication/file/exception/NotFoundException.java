package no.unit.nva.download.publication.file.exception;


import nva.commons.apigateway.exceptions.ApiGatewayException;
import org.apache.http.HttpStatus;

import java.util.UUID;

public class NotFoundException extends ApiGatewayException {

    public static final String ERROR_TEMPLATE = "Requested resource \"%s/files/%s\" was found";

    public NotFoundException(UUID resource, UUID file)  {
        super(String.format(ERROR_TEMPLATE, resource, file));
    }

    @Override
    protected Integer statusCode() {
        return HttpStatus.SC_NOT_FOUND;
    }
}
