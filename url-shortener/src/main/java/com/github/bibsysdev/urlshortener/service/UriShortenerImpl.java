package com.github.bibsysdev.urlshortener.service;

import com.github.bibsysdev.urlshortener.service.model.UriMap;
import java.net.URI;
import java.time.Instant;
import nva.commons.core.JacocoGenerated;

public class UriShortenerImpl implements UriShortener {


    private final String domain;
    private final UriShortenerWriteClient uriShortenerWriteClient;

    public UriShortenerImpl(String domain, UriShortenerWriteClient uriShortenerWriteClient) {
        this.domain = domain;
        this.uriShortenerWriteClient = uriShortenerWriteClient;
    }

    @Override
    public URI shorten(URI longUri, Instant expiration) {
        var uriMap = UriMap.create(longUri, expiration, domain);
        uriShortenerWriteClient.insertUriMap(uriMap);
        return uriMap.shortenedUri();
    }

    @JacocoGenerated
    @Override
    public URI resolve(URI uri) {
        return null;
    }
}
