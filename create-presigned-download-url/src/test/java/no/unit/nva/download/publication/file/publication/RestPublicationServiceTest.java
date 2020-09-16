package no.unit.nva.download.publication.file.publication;

import static nva.commons.utils.JsonUtils.objectMapper;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Callable;
import no.unit.nva.download.publication.file.publication.exception.NoResponseException;
import no.unit.nva.model.Publication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

public class RestPublicationServiceTest {

    public static final String PUBLICATION_RESPONSE = "src/test/resources/publication_response.json";

    public static final String SOME_API_KEY = "some api key";
    public static final String API_HOST = "example.org";
    public static final String API_SCHEME = "http";

    private HttpClient client;
    private HttpResponse<String> response;

    private RestPublicationService publicationService;

    /**
     * Set up environment.
     */
    @BeforeEach
    public void setUp() {
        client = mock(HttpClient.class);
        response = mock(HttpResponse.class);
        publicationService = new RestPublicationService(client, objectMapper, API_SCHEME,
            API_HOST);
    }

    @Test
    @DisplayName("getPublicationWithAuthorizationToken throws NoResponseException when publication could not be "
        + "retrieved")
    public void getPublicationWithAuthorizationTokenThrowsNoResponseExceptionOnClientError()
        throws IOException, InterruptedException {
        Executable action = () -> publicationService.getPublicationWithAuthorizationToken(
            UUID.randomUUID(),
            SOME_API_KEY);
        throwsNoResponseExceptionWhencClientThrowsException(action);
    }

    @Test
    @DisplayName("getPublicationWithoutAuthorizationToken throws NoResponseException when publication could not be "
        + "retrieved")
    public void getPublicationWithoutAuthorizationTokenThrowsNoResponseExceptionOnClientError()
        throws IOException, InterruptedException {
        Executable action = () -> publicationService.getPublicationWithoutAuthorizationToken(
            UUID.randomUUID());
        throwsNoResponseExceptionWhencClientThrowsException(action);
    }

    @Test
    @DisplayName("getPublicationWithAuthorizationToken returns a nonEmpty publication when "
        + "it receives a non empty json object")
    public void getPublicationReturnsJsonObject() throws Exception {

        Callable<Publication> action = () -> publicationService.getPublicationWithAuthorizationToken(
            UUID.randomUUID(), SOME_API_KEY);

        returnsNonEmptyObjectWhenClientReturnsNonEmptyJsonObject(action);
    }

    @Test
    @DisplayName("getPublicationWithoutAuthToken returns a nonEmpty publication "
        + "when it receives a non empty json object")
    public void getPublicationWithoutAuthTokenReturnsJsonObject() throws Exception {

        Callable<Publication> action = () -> publicationService.getPublicationWithoutAuthorizationToken(
            UUID.randomUUID());
        returnsNonEmptyObjectWhenClientReturnsNonEmptyJsonObject(action);
    }

    @Test
    @DisplayName("getPublicationWithAuthorizationToken throws NoResponseException when publication is not found")
    public void getPublicationWithAuthorizationTokenNotFound() throws IOException, InterruptedException {

        Executable action = () -> publicationService.getPublicationWithAuthorizationToken(
            UUID.randomUUID(), SOME_API_KEY);

        //TODO: fix code to return NotFoundException when client returns NotFound.
        throwsNoResponseExceptionWhenClientReturnsNotFound(action);
    }

    @Test
    @DisplayName("getPublicationWithoutAuthorizationToken throws NoResponseException when publication is not found")
    public void getPublicationWithoutAuthorizationTokenNotFound() throws IOException, InterruptedException {
        Executable action = () -> publicationService.getPublicationWithoutAuthorizationToken(UUID.randomUUID());
        throwsNoResponseExceptionWhenClientReturnsNotFound(action);
    }

    private void throwsNoResponseExceptionWhenClientReturnsNotFound(Executable action)
        throws IOException, InterruptedException {
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when((response.statusCode())).thenReturn(SC_NOT_FOUND);
        assertThrows(NoResponseException.class, action);
    }

    private void returnsNonEmptyObjectWhenClientReturnsNonEmptyJsonObject(Callable<Publication> action)
        throws Exception {
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when((response.body())).thenReturn(getResponse(PUBLICATION_RESPONSE));

        assertNotNull(action.call());
    }

    private void throwsNoResponseExceptionWhencClientThrowsException(Executable action)
        throws IOException, InterruptedException {
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(IOException.class);
        assertThrows(NoResponseException.class, action);
    }

    private String getResponse(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
