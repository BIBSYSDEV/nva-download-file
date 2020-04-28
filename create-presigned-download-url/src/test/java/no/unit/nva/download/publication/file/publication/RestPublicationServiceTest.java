package no.unit.nva.download.publication.file.publication;

import no.unit.nva.download.publication.file.publication.exception.NoResponseException;
import no.unit.nva.model.Publication;

import nva.commons.exceptions.ApiGatewayException;
import nva.commons.utils.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static nva.commons.utils.JsonUtils.objectMapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RestPublicationServiceTest {

    public static final String RESOURCE_RESPONSE = "src/test/resources/resource_response.json";

    public static final String SOME_API_KEY = "some api key";
    public static final String API_HOST = "example.org";
    public static final String API_SCHEME = "http";
    public static final String NO_ITEMS = "{ \"Items\": [] }";

    private HttpClient client;
    private HttpResponse<String> response;
    private Environment environment;

    /**
     * Set up environment.
     */
    @BeforeEach
    public void setUp() {
        client = mock(HttpClient.class);
        response = mock(HttpResponse.class);
        environment = mock(Environment.class);
    }

    @Test
    @DisplayName("calling Constructor With All Env")
    public void callingConstructorWithAllEnv() {
        Environment environment = mock(Environment.class);
        when(environment.readEnv(RestPublicationService.API_SCHEME_ENV)).thenReturn(API_SCHEME);
        when(environment.readEnv(RestPublicationService.API_HOST_ENV)).thenReturn(API_HOST);
        new RestPublicationService(environment);
    }

    @Test
    @DisplayName("when client get an error the error is propagated to the response")
    public void getPublicationClientError() throws IOException, InterruptedException {
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(IOException.class);

        RestPublicationService publicationService = new RestPublicationService(client, objectMapper, API_SCHEME,
                API_HOST);

        assertThrows(NoResponseException.class, () -> publicationService.getPublication(
                UUID.randomUUID(),
                SOME_API_KEY
        ));
    }

    @Test
    @DisplayName("when client receives a non empty json object it sends it to the response body")
    public void getPublicationReturnsJsonObject() throws IOException, InterruptedException, ApiGatewayException {
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when((response.body())).thenReturn(getResponse(RESOURCE_RESPONSE));

        RestPublicationService publicationService = new RestPublicationService(client, objectMapper, API_SCHEME,
                API_HOST);

        Publication publication = publicationService.getPublication(
                UUID.randomUUID(),
                SOME_API_KEY
        );

        assertNotNull(publication);
    }

    @Test
    @DisplayName("when publication has no items it returns an empty response")
    public void getPublicationNoItems() throws IOException, InterruptedException, ApiGatewayException {
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when((response.body())).thenReturn(NO_ITEMS);

        RestPublicationService publicationService = new RestPublicationService(client, objectMapper, API_SCHEME,
                API_HOST);

        assertThrows(NoResponseException.class, () -> publicationService.getPublication(
                UUID.randomUUID(),
                SOME_API_KEY
        ));
    }


    private String getResponse(String path) throws IOException {
        return Files.readString(Path.of(path));
    }

}
