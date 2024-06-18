package com.github.bibsysdev.urlshortener.service.model;

import static java.util.Objects.isNull;
import java.net.URI;
import java.time.Instant;
import no.unit.nva.identifiers.SortableIdentifier;
import nva.commons.core.StringUtils;
import nva.commons.core.paths.UriWrapper;

public record UriMap (URI shortenedUri, URI longUri, Instant createdDate, Instant expiresDate) {

    private final static String PATH = "download";
    private final static String SHORTENED_PATH = "short";


    public static UriMap create(URI longVersion, Instant expiresDate, String domain) {
        validateRequest(longVersion, expiresDate);
        return new UriMap(createNewShortVersion(domain), longVersion, Instant.now(), expiresDate);
    }

    private static void validateRequest(URI longVersion, Instant expiresDate) {
        if (isNull(longVersion) || StringUtils.isBlank(longVersion.toString()) || isNull(expiresDate)) {
            throw new IllegalArgumentException("Missing required parameters");
        }
    }

    private static URI createNewShortVersion(String domain) {
        return UriWrapper.fromUri(domain)
                   .addChild(PATH)
                   .addChild(SHORTENED_PATH)
                   .addChild(SortableIdentifier.next().toString())
                   .getUri();
    }
}
