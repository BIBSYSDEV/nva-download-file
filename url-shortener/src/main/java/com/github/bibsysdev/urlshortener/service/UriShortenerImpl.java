package com.github.bibsysdev.urlshortener.service;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.github.bibsysdev.urlshortener.service.model.UriMap;
import java.net.URI;
import java.time.Instant;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;

public class UriShortenerImpl implements UriShortener {

    private static final String TABLE_NAME_ENVIRONMENT_VARIABLE = "SHORTENED_URI_TABLE_NAME";
    private final String domain;
    private final UriShortenerWriteClient uriShortenerWriteClient;

    public UriShortenerImpl(String domain, UriShortenerWriteClient uriShortenerWriteClient) {
        this.domain = domain;
        this.uriShortenerWriteClient = uriShortenerWriteClient;
    }

    @JacocoGenerated
    public static UriShortenerImpl createDefault() {
        return new UriShortenerImpl("https://" + new Environment().readEnv("API_HOST"), new UriShortenerWriteClient(
            AmazonDynamoDBClientBuilder.defaultClient(), new Environment().readEnv(TABLE_NAME_ENVIRONMENT_VARIABLE)));
    }

    @Override
    public URI shorten(URI longUri, Instant expiration) {
        var uriMap = UriMap.create(longUri, expiration, domain);
        uriShortenerWriteClient.insertUriMap(uriMap);
        return uriMap.shortenedUri();
    }
}
