package no.unit.nva.download.publication.file.publication;

import static nva.commons.core.attempt.Try.attempt;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mikael.urlbuilder.UrlBuilder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import no.unit.nva.download.publication.file.publication.exception.NoResponseException;
import no.unit.nva.download.publication.file.publication.exception.NotFoundException;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.JsonUtils;
import org.apache.http.HttpHeaders;
import org.zalando.problem.Problem;

public class RestPublicationService {

    public static final String PATH = "/publication/";
    public static final String APPLICATION_JSON = "application/json";

    public static final String API_HOST_ENV = "API_HOST";
    public static final String API_SCHEME_ENV = "API_SCHEME";
    public static final String ERROR_COMMUNICATING_WITH_REMOTE_SERVICE = "Error communicating with remote service: ";
    public static final String ERROR_PUBLICATION_NOT_FOUND_FOR_IDENTIFIER = "Publication not found for identifier: ";
    public static final String EXTERNAL_ERROR_MESSAGE_DECORATION = "Error fetching downloading link for publication:";
    public static final String ERROR_MESSAGE_DELIMITER = " ";

    private final ObjectMapper objectMapper;
    private final HttpClient client;
    private final String apiScheme;
    private final String apiHost;

    /**
     * Constructor for RestPublicationService.
     *
     * @param client       client
     * @param objectMapper objectMapper
     * @param apiScheme    apiScheme
     * @param apiHost      apiHost
     */
    public RestPublicationService(HttpClient client, ObjectMapper objectMapper, String apiScheme, String apiHost) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.apiScheme = apiScheme;
        this.apiHost = apiHost;
    }

    /**
     * Constructor for RestPublicationService.
     *
     * @param environment environment
     */
    @JacocoGenerated
    public RestPublicationService(Environment environment) {
        this(HttpClient.newHttpClient(), JsonUtils.dtoObjectMapper, environment.readEnv(API_SCHEME_ENV),
             environment.readEnv(API_HOST_ENV));
    }

    /**
     * Retrieve publication metadata.
     *
     * @param identifier           identifier
     * @return A publication
     * @throws ApiGatewayException exception thrown if value is missing
     */
    public PublicationResponse getPublication(String identifier)
        throws ApiGatewayException {

        URI uri = buildUriToPublicationService(identifier);
        HttpRequest httpRequest = buildHttpRequest(uri);
        return fetchPublicationFromService(identifier, uri, httpRequest);
    }

    private PublicationResponse fetchPublicationFromService(String identifier, URI uri, HttpRequest httpRequest)
        throws ApiGatewayException {

        try {
            return fetchPublicationFromService(identifier, httpRequest);
        } catch (Exception e) {
            throw handleException(uri, e);
        }
    }

    private PublicationResponse fetchPublicationFromService(String identifier, HttpRequest httpRequest)
            throws java.io.IOException, InterruptedException, NotFoundException {

        HttpResponse<String> httpResponse = sendHttpRequest(httpRequest);
        if (httpResponse.statusCode() == SC_NOT_FOUND) {
            String externalErrorMessage = extractExternalErrorMessage(identifier, httpResponse);
            throw new NotFoundException(externalErrorMessage);
        }
        return parseJsonObjectToPublication(httpResponse);
    }

    private ApiGatewayException handleException(URI uri, Exception exception) {
        if (exception instanceof NotFoundException) {
            return (NotFoundException) exception;
        }
        return new NoResponseException(ERROR_COMMUNICATING_WITH_REMOTE_SERVICE + uri.toString(), exception);
    }

    private String extractExternalErrorMessage(String identifier, HttpResponse<String> httpResponse) {
        String externalErrorMessage = parseResponseBody(httpResponse.body())
            .map(Problem::getDetail)
            .orElse(ERROR_PUBLICATION_NOT_FOUND_FOR_IDENTIFIER + identifier);
        return decorateExternalErrorMessage(identifier, externalErrorMessage);
    }

    private String decorateExternalErrorMessage(String identifier, String externalErrorMessage) {
        return String.join(ERROR_MESSAGE_DELIMITER, externalErrorDecoration(identifier), externalErrorMessage);
    }

    private String externalErrorDecoration(String identifier) {
        return EXTERNAL_ERROR_MESSAGE_DECORATION + identifier;
    }

    private Optional<Problem> parseResponseBody(String body) {
        return attempt(() -> objectMapper.readValue(body, Problem.class))
            .toOptional();
    }

    private HttpResponse<String> sendHttpRequest(HttpRequest httpRequest)
        throws java.io.IOException, InterruptedException {
        return client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    }

    private PublicationResponse parseJsonObjectToPublication(HttpResponse<String> httpResponse)
            throws JsonProcessingException {
        return objectMapper.readValue(httpResponse.body(), PublicationResponse.class);
    }

    private HttpRequest buildHttpRequest(URI uri) {
        return HttpRequest.newBuilder()
                .uri(uri)
                .header(HttpHeaders.ACCEPT, APPLICATION_JSON)
                .GET()
                .build();
    }

    private URI buildUriToPublicationService(String identifier) {
        return UrlBuilder.empty()
            .withScheme(apiScheme)
            .withHost(apiHost)
            .withPath(PATH + identifier)
            .toUri();
    }
}
