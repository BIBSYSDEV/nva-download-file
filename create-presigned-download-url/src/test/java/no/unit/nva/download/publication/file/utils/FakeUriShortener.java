package no.unit.nva.download.publication.file.utils;

import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import com.github.bibsysdev.urlshortener.service.UriShortener;
import java.net.URI;
import java.time.Instant;

public class FakeUriShortener implements UriShortener {

    @Override
    public URI shorten(URI longUri, Instant expirationDate) {
        return randomUri();
    }
}
