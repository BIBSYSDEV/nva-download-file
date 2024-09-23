package com.github.bibsysdev.urlshortener.service;

import static no.unit.nva.testutils.RandomDataGenerator.randomInstant;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import com.github.bibsysdev.urlshortener.service.exceptions.TransactionFailedException;
import com.github.bibsysdev.urlshortener.service.utils.UriShortenerLocalDynamoDb;
import java.net.URI;
import java.time.Instant;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UriShortenerServiceImplTest extends UriShortenerLocalDynamoDb {

    private static final String TABLE_NAME = "url_shortener";
    private static final URI DOMAIN = UriWrapper.fromUri("https://example.com").getUri();

    private UriShortenerImpl uriShortener;

    @BeforeEach
    void initialize() {
        super.init(TABLE_NAME);
        this.uriShortener = new UriShortenerImpl(DOMAIN, new UriShortenerWriteClient(client, TABLE_NAME));
    }

    @Test
    void shouldThrowExceptionIfCannotPersistInDatabase() {
        var longUri = randomUri();
        var expiration = randomInstant();
        var mockUriShortenerWriteClient = mock(UriShortenerWriteClient.class);
        uriShortener = new UriShortenerImpl(DOMAIN, mockUriShortenerWriteClient);
        doThrow(new TransactionFailedException("Transaction failed")).when(mockUriShortenerWriteClient)
            .insertUriMap(any());
        assertThrows(TransactionFailedException.class, () -> uriShortener.shorten(longUri, expiration));
    }

    @Test
    void shouldThrowExceptionIfLongUriIsEmpty() {
        var longUri = UriWrapper.fromUri("").getUri();
        var expiration = randomInstant();
        assertThrows(IllegalArgumentException.class, () -> uriShortener.shorten(longUri, expiration));
    }

    @Test
    void shouldThrowExceptionIfLongUriIsNull() {
        URI longUri = null;
        var expiration = randomInstant();
        assertThrows(IllegalArgumentException.class, () -> uriShortener.shorten(longUri, expiration));
    }

    @Test
    void shouldThrowExceptionIfExpirationIsNull() {
        URI longUri = randomUri();
        Instant expiration = null;
        assertThrows(IllegalArgumentException.class, () -> uriShortener.shorten(longUri, expiration));
    }

    @Test
    void shouldReturnUriWhenNothingFails() {
        var longUri = randomUri();
        var expiration = randomInstant();
        var shortUri = uriShortener.shorten(longUri, expiration);
        assertThat(shortUri.toString(), containsString(DOMAIN.toString()));
    }
}
