package no.unit.nva.download.publication.file.utils;

import com.github.bibsysdev.urlshortener.service.UriShortener;
import com.github.bibsysdev.urlshortener.service.exceptions.TransactionFailedException;
import java.net.URI;
import java.time.Instant;

public class FakeUriShortenerThrowingException implements UriShortener {

    @Override
    public URI shorten(URI longUri, Instant expirationDate) {
        throw new TransactionFailedException("Transaction failed");
    }
}
