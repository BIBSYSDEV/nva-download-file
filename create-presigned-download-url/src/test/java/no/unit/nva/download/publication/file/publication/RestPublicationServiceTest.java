package no.unit.nva.download.publication.file.publication;


import static nva.commons.core.JsonUtils.objectMapper;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import no.unit.nva.download.publication.file.publication.exception.NoResponseException;
import no.unit.nva.download.publication.file.publication.exception.NotFoundException;
import no.unit.nva.model.Publication;

import nva.commons.apigateway.exceptions.ApiGatewayException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.zalando.problem.Problem;

@ExtendWith(MockitoExtension.class)
public class RestPublicationServiceTest {

    public static final String PUBLICATION_RESPONSE = "src/test/resources/publication_response.json";

    public static final String SOME_API_KEY = "some api key";
    public static final String API_HOST = "example.org";
    public static final String API_SCHEME = "http";
    public static final String NOT_FOUND_ERROR_MESSAGE = "NotFoundErrorMessage";

    @Mock
    private HttpClient client;
    @Mock
    private HttpResponse<String> response;

    @Test
    @DisplayName("getPublication throws NoResponseException when publication could not be retrieved")
    public void getPublicationClientError() throws IOException, InterruptedException {
        when(client.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenThrow(IOException.class);

        RestPublicationService publicationService = new RestPublicationService(client, objectMapper, API_SCHEME,
            API_HOST);

        assertThrows(NoResponseException.class, () -> publicationService.getPublication(UUID.randomUUID()));
    }

    @Test
    @DisplayName("getPublication returns a nonEmpty publication when it receives a non empty json object")
    public void getPublicationReturnsJsonObject() throws IOException, InterruptedException, ApiGatewayException {
        when(client.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);
        when((response.body())).thenReturn(getResponse(PUBLICATION_RESPONSE));

        RestPublicationService publicationService = new RestPublicationService(client, objectMapper, API_SCHEME,
            API_HOST);

        Publication publication = publicationService.getPublication(UUID.randomUUID());

        assertNotNull(publication);
    }

    @Test
    @DisplayName("getPublication throws NotFoundException when publication is not found")
    public void getPublicationThrowsNotFoundExceptionWhenPublicationIsNotFound()
        throws IOException, InterruptedException {

        clientReceivesNotFoundError();

        RestPublicationService publicationService = new RestPublicationService(client, objectMapper, API_SCHEME,
            API_HOST);

        assertThrows(NotFoundException.class, () -> publicationService.getPublication(UUID.randomUUID()));
    }

    @Test
    @DisplayName("getPublication adds the response details to the NotFoundException message when"
        + " publication is not found")
    public void getPublicationAddsTheResponseDetailsToTheNotFoundExceptionMessage()
        throws IOException, InterruptedException {

        clientReceivesNotFoundError();

        RestPublicationService publicationService = new RestPublicationService(client, objectMapper, API_SCHEME,
            API_HOST);

        NotFoundException actualException = assertThrows(NotFoundException.class,
            () -> publicationService.getPublication(UUID.randomUUID()));

        assertThat(actualException.getMessage(), containsString(NOT_FOUND_ERROR_MESSAGE));
    }

    @Test
    @DisplayName("getPublication returns the error message of the publication service when it receives a NotFoundError")
    public void getPublicationReturnsTheErrorMessageOfThePublicaitonServiceWhenItReceivesNotFound() {

    }

    private void clientReceivesNotFoundError() throws IOException, InterruptedException {
        when(client.send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response);
        when((response.statusCode())).thenReturn(SC_NOT_FOUND);
        when(response.body()).thenReturn(problemString());
    }

    private String problemString() throws JsonProcessingException {
        Problem problem = Problem.builder().withDetail(NOT_FOUND_ERROR_MESSAGE).build();
        return objectMapper.writeValueAsString(problem);
    }

    private String getResponse(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
