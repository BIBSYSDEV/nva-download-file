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

import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RestPublicationServiceTest {

    public static final String PUBLICATION_RESPONSE = "src/test/resources/publication_response.json";

    public static final String SOME_API_KEY = "some api key";
    public static final String API_HOST = "example.org";
    public static final String API_SCHEME = "http";

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
    @DisplayName("getPublication throws NoResponseException when publication could not be retrieved")
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
    @DisplayName("getPublication returns a nonEmpty publication when it receives a non empty json object")
    public void getPublicationReturnsJsonObject() throws IOException, InterruptedException, ApiGatewayException {
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when((response.body())).thenReturn(getResponse(PUBLICATION_RESPONSE));

        RestPublicationService publicationService = new RestPublicationService(client, objectMapper, API_SCHEME,
                API_HOST);

        Publication publication = publicationService.getPublication(
                UUID.randomUUID(),
                SOME_API_KEY
        );

        assertNotNull(publication);
    }

    @Test
    @DisplayName("getPublication throws NoResponseException when publication is not found")
    public void getPublicationNotFound() throws IOException, InterruptedException {
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when((response.statusCode())).thenReturn(SC_NOT_FOUND);

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
