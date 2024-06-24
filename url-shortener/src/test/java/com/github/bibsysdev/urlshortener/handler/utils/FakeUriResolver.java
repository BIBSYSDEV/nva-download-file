package com.github.bibsysdev.urlshortener.handler.utils;

import com.github.bibsysdev.urlshortener.service.UriResolver;
import java.net.URI;
import nva.commons.apigateway.exceptions.ApiGatewayException;

public class FakeUriResolver implements UriResolver {

    private final URI longUri;

    public FakeUriResolver(URI longUri) {
        this.longUri = longUri;
    }

    @Override
    public URI resolve(URI shortVersion) throws ApiGatewayException {
        return longUri;
    }
}
