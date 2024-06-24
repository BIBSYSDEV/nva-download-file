package com.github.bibsysdev.urlshortener.handler.utils;

import com.github.bibsysdev.urlshortener.service.UriResolver;
import java.net.URI;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.NotFoundException;

public class FakeUriResolverThrowingException implements UriResolver {

    @Override
    public URI resolve(URI shortVersion) throws ApiGatewayException {
        throw new NotFoundException("Not found");
    }
}
