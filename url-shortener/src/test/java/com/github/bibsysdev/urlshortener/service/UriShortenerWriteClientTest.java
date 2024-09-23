package com.github.bibsysdev.urlshortener.service;

import static no.unit.nva.testutils.RandomDataGenerator.randomInstant;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import com.github.bibsysdev.urlshortener.service.exceptions.TransactionFailedException;
import com.github.bibsysdev.urlshortener.service.model.UriMap;
import com.github.bibsysdev.urlshortener.service.utils.UriShortenerLocalDynamoDb;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UriShortenerWriteClientTest extends UriShortenerLocalDynamoDb {

    private static final String TABLE_NAME = "url_shortener";

    private UriShortenerWriteClient uriShortenerWriteClient;

    @BeforeEach
    void initialize() {
        super.init(TABLE_NAME);
        this.uriShortenerWriteClient = new UriShortenerWriteClient(client, TABLE_NAME);
    }

    @Test
    void shouldPreventSeveralIdenticalShortUriBeingPersisted() {
        var shortUri = randomUri();
        var uriMap = new UriMap(shortUri, randomUri(), randomInstant(), randomInstant().getEpochSecond());
        var uriMap2 = new UriMap(shortUri, randomUri(), randomInstant(), randomInstant().getEpochSecond());
        uriShortenerWriteClient.insertUriMap(uriMap2);
        assertThrows(TransactionFailedException.class, () -> uriShortenerWriteClient.insertUriMap(uriMap));
    }

    @Test
    void shouldAllowSeveralIdenticalLongUriBeingPersisted() {
        var longUri = randomUri();
        var uriMap = new UriMap(randomUri(), longUri, randomInstant(), randomInstant().getEpochSecond());
        var uriMap2 = new UriMap(randomUri(), longUri, randomInstant(), randomInstant().getEpochSecond());
        uriShortenerWriteClient.insertUriMap(uriMap);
        assertDoesNotThrow(() -> uriShortenerWriteClient.insertUriMap(uriMap2));
    }
}
