package com.github.bibsysdev.urlshortener.handler;

import static no.unit.nva.commons.json.JsonUtils.dtoObjectMapper;
import static nva.commons.apigateway.ApiGatewayHandler.ALLOWED_ORIGIN_ENV;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import no.unit.nva.identifiers.SortableIdentifier;
import no.unit.nva.testutils.HandlerRequestBuilder;
import no.unit.nva.testutils.TestHeaders;
import nva.commons.core.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.amazonaws.services.lambda.runtime.Context;

public class ResolveShortenedUrlHandlerTest {

    private final String IDENTIFIER = "identifier";
    private final Context CONTEXT = mock(Context.class);
    private ResolveShortenedUrlHandler handler;
    private ByteArrayOutputStream output;
    private static final String ANY_ORIGIN = "*";

    @BeforeEach
    void setUp() {
        handler = new ResolveShortenedUrlHandler(new Environment());
        output = new ByteArrayOutputStream();
    }

    @Test
    void dummyTest() throws  IOException {
        handler = new ResolveShortenedUrlHandler(mockEnvironment());
        handler.handleRequest(
            createRequest(SortableIdentifier.next().toString()),
            output,
            CONTEXT);

    }

    private InputStream createRequest(String identifier)
        throws IOException {
        return new HandlerRequestBuilder<Void>(dtoObjectMapper)
                   .withHeaders(TestHeaders.getRequestHeaders())
                   .withPathParameters(Map.of(IDENTIFIER, identifier.toString()))
                   .build();
    }



    private Environment mockEnvironment() {
        Environment environment = mock(Environment.class);
        when(environment.readEnv(ALLOWED_ORIGIN_ENV)).thenReturn(ANY_ORIGIN);
        return environment;
    }
}
