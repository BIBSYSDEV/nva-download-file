package com.github.bibsysdev.urlshortener.service;

import static no.unit.nva.testutils.RandomDataGenerator.randomInstant;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.junit.Assert.assertThrows;
import com.github.bibsysdev.urlshortener.service.exceptions.TransactionFailedException;
import com.github.bibsysdev.urlshortener.service.model.UriMap;
import com.github.bibsysdev.urlshortener.service.utils.UriShortenerLocalDynamoDb;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UriShortenerWriteClientTest extends UriShortenerLocalDynamoDb {


    private final String TABLE_NAME = "url_shortener";


    private UriShortenerWriteClient uriShortenerWriteClient;

    @BeforeEach
    void initialize() {
        super.init(TABLE_NAME);
        this.uriShortenerWriteClient = new UriShortenerWriteClient(client, TABLE_NAME);
    }

    @Test
    void shouldPreventSeveralShortUriBeingPersisted(){
        var uriMap = UriMap.create(randomUri(), randomInstant(), "https://example.com");
        uriShortenerWriteClient.insertUriMap(uriMap);
        assertThrows(TransactionFailedException.class, ()-> uriShortenerWriteClient.insertUriMap(uriMap));
    }

}
