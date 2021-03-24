package no.unit.nva.download.publication.file.publication.exception;

public class InputException extends RuntimeException {

    public InputException(String message) {
        super(message);
    }

    public InputException(String message, Exception exception) {
        super(message, exception);
    }
}
