package com.github.bibsysdev.urlshortener.service.exceptions;

public class TransactionFailedException extends RuntimeException {

    public TransactionFailedException(Exception exception) {
        super(exception);
    }
}
