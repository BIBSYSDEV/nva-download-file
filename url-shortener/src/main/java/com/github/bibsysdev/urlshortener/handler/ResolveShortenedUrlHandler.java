package com.github.bibsysdev.urlshortener.handler;

import com.amazonaws.services.lambda.runtime.Context;
import java.util.HashMap;
import java.util.Map;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class ResolveShortenedUrlHandler extends ApiGatewayHandler<Void, Void> {

    private final Logger LOGGER = LoggerFactory.getLogger(ResolveShortenedUrlHandler.class);

    @JacocoGenerated
    public ResolveShortenedUrlHandler() {
        this(new Environment());
    }

    public ResolveShortenedUrlHandler(Environment environment) {
        super(Void.class, environment);
    }

    @Override
    protected Void processInput(Void unused, RequestInfo requestInfo, Context context) throws ApiGatewayException {
        LOGGER.info(requestInfo.getPathParameter("identifier"));
        LOGGER.info(requestInfo.getRequestUri().toString());
        addAdditionalHeaders(this::createAdditionalHeader);
        return null;
    }

    private Map<String, String> createAdditionalHeader() {
        final Map<String, String> headers = new HashMap<>();
        headers.put("Location", "www.example.com");
        return headers;
    }

    @Override
    protected Integer getSuccessStatusCode(Void unused, Void o) {
        return 301;
    }
}
