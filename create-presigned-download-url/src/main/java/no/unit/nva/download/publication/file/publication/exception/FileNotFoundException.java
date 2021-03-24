package no.unit.nva.download.publication.file.publication.exception;


import nva.commons.apigateway.exceptions.ApiGatewayException;
import org.apache.http.HttpStatus;

public class FileNotFoundException extends ApiGatewayException {

    public FileNotFoundException(String message) {
        super(message);
    }

    @Override
    protected Integer statusCode() {
        return HttpStatus.SC_NOT_FOUND;
    }
}
