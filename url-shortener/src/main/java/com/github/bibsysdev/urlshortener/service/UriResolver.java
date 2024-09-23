package com.github.bibsysdev.urlshortener.service;

import java.net.URI;
import nva.commons.apigateway.exceptions.ApiGatewayException;

public interface UriResolver {

    URI resolve(URI shortVersion) throws ApiGatewayException;
}
