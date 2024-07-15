package com.github.bibsysdev.urlshortener.handler;

import static org.apache.http.HttpHeaders.LOCATION;
import com.amazonaws.services.lambda.runtime.Context;
import com.github.bibsysdev.urlshortener.service.UriResolver;
import com.github.bibsysdev.urlshortener.service.UriResolverImpl;
import java.net.URI;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(ResolveShortenedUrlHandler.class);
    private final UriResolver uriResolver;

    @JacocoGenerated
    public ResolveShortenedUrlHandler() {
        this(new Environment(), UriResolverImpl.createDefault());
    }

    public ResolveShortenedUrlHandler(Environment environment, UriResolver uriResolver) {
        super(Void.class, environment);
        this.uriResolver = uriResolver;
    }

    @Override
    protected Void processInput(Void unused, RequestInfo requestInfo, Context context) throws ApiGatewayException {
        LOGGER.info(requestInfo.getRequestUri().toString());
        var shortenedUri = requestInfo.getRequestUri();
        var longUri = uriResolver.resolve(shortenedUri);
        addAdditionalHeaders(() -> addLocationHeader(longUri));
        return null;
    }

    private Map<String, String> addLocationHeader(URI longUri) {
        Map<String, String> headers = new HashMap<>();
        headers.put(LOCATION, longUri.toString());
        return headers;
    }

    @JacocoGenerated
    @Override
    protected Integer getSuccessStatusCode(Void unused, Void o) {
        return 301;
    }
}
