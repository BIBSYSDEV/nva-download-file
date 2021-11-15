package no.unit.nva.download.publication.file.publication.exception;


import nva.commons.apigateway.exceptions.ApiGatewayException;
import org.apache.http.HttpStatus;

import java.util.UUID;

public class FileNotFoundException extends ApiGatewayException {

    public static final String ERROR_TEMPLATE = "No file with identifier \"%s\" was found";

    public FileNotFoundException(UUID uuid) {
        super(String.format(ERROR_TEMPLATE, uuid));
    }

    @Override
    protected Integer statusCode() {
        return HttpStatus.SC_NOT_FOUND;
    }
}
