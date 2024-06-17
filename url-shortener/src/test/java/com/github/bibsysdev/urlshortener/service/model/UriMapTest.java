package com.github.bibsysdev.urlshortener.service.model;

import static no.unit.nva.testutils.RandomDataGenerator.randomInstant;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import org.junit.jupiter.api.Test;

public class UriMapTest {

    private static final String DOMAIN = "https://api.sandbox.nva.aws.unit.no";
    private static final String ID_NAMESPACE = "https://api.sandbox.nva.aws.unit.no/download";

    @Test
    void shouldConvertUrlWithCorrectNamespace() {
        var longUri = randomUri();
        var uriMap = UriMap.create(longUri, randomInstant(), DOMAIN);

        assertThat(uriMap.longUri(), is(equalTo(longUri)));
        assertThat(uriMap.shortenedUri().toString(), containsString(ID_NAMESPACE));
    }
}
