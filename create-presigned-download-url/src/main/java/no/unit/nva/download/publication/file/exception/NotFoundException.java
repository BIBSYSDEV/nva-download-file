package no.unit.nva.download.publication.file.exception;

import nva.commons.apigateway.exceptions.ApiGatewayException;
import org.apache.http.HttpStatus;

public class NotFoundException extends ApiGatewayException {

    public static final String ERROR_TEMPLATE = "The requested resource was not found";

    public NotFoundException()  {
        super(ERROR_TEMPLATE);
    }

    @Override
    protected Integer statusCode() {
        return HttpStatus.SC_NOT_FOUND;
    }
}
