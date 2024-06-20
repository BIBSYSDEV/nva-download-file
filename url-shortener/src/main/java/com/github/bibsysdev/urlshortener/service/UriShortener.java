package com.github.bibsysdev.urlshortener.service;

import java.net.URI;
import java.time.Instant;

public interface UriShortener {

    URI shorten(URI longUri, Instant expirationDate);

}
