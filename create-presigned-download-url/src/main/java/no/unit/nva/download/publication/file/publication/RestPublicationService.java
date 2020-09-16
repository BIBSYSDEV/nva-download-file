package no.unit.nva.download.publication.file.publication;

import static org.apache.http.HttpStatus.SC_NOT_FOUND;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mikael.urlbuilder.UrlBuilder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.unit.nva.download.publication.file.publication.exception.NoResponseException;
import no.unit.nva.download.publication.file.publication.exception.NotFoundException;
import no.unit.nva.model.Publication;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.utils.Environment;
import nva.commons.utils.JacocoGenerated;
import nva.commons.utils.JsonUtils;
import org.apache.http.HttpHeaders;

public class RestPublicationService {

    public static final String PATH = "/publication/";
    public static final String APPLICATION_JSON = "application/json";

    public static final String API_HOST_ENV = "API_HOST";
    public static final String API_SCHEME_ENV = "API_SCHEME";
    public static final String ERROR_COMMUNICATING_WITH_REMOTE_SERVICE = "Error communicating with remote service: ";
    public static final String ERROR_PUBLICATION_NOT_FOUND_FOR_IDENTIFIER = "Publication not found for identifier: ";

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
        this(HttpClient.newHttpClient(), JsonUtils.objectMapper, environment.readEnv(API_SCHEME_ENV),
            environment.readEnv(API_HOST_ENV));
    }

    /**
     * Retrieve publication metadata.
     *
     * @param identifier         identifier
     * @param authorizationToken authorization
     * @return A publication
     * @throws ApiGatewayException exception thrown if value is missing
     */
    public Publication getPublication(UUID identifier, String authorizationToken)
        throws ApiGatewayException {

        URI uri = buildUriToPublicationService(identifier);
        HttpRequest httpRequest = buildHttpRequest(uri, headers(authorizationToken));
        return fetchPublicationFromService(identifier, uri, httpRequest);
    }

    private Map<String, String> headers(String authorization) {
        return Map.of(HttpHeaders.ACCEPT, APPLICATION_JSON,
            HttpHeaders.AUTHORIZATION, authorization);
    }

    private Publication fetchPublicationFromService(UUID identifier, URI uri, HttpRequest httpRequest)
        throws NoResponseException {
        try {
            return fetchPublicationFromService(identifier, httpRequest);
        } catch (Exception e) {
            throw new NoResponseException(ERROR_COMMUNICATING_WITH_REMOTE_SERVICE + uri.toString(), e);
        }
    }

    private Publication fetchPublicationFromService(UUID identifier, HttpRequest httpRequest)
        throws java.io.IOException, InterruptedException, NotFoundException {

        HttpResponse<String> httpResponse = sendHttpRequest(httpRequest);
        if (httpResponse.statusCode() == SC_NOT_FOUND) {
            throw new NotFoundException(ERROR_PUBLICATION_NOT_FOUND_FOR_IDENTIFIER + identifier);
        }

        return parseJsonObjectToPublication(httpResponse);
    }

    private HttpResponse<String> sendHttpRequest(HttpRequest httpRequest)
        throws java.io.IOException, InterruptedException {
        return client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    }

    private Publication parseJsonObjectToPublication(HttpResponse<String> httpResponse) throws JsonProcessingException {
        JsonNode jsonNode = objectMapper.readTree(httpResponse.body());
        return objectMapper.convertValue(jsonNode, Publication.class);
    }

    private HttpRequest buildHttpRequest(URI uri, Map<String, String> headers) {
        return HttpRequest.newBuilder()
            .uri(uri)
            .headers(serializeHeaders(headers))
            .GET()
            .build();
    }

    private String[] serializeHeaders(Map<String, String> headers) {
        List<String> mapEntriesAsList = headers.entrySet()
            .stream()
            .flatMap(this::keyValuePairAsStream)
            .collect(Collectors.toList());
        return toArray(mapEntriesAsList);
    }

    private String[] toArray(List<String> x) {
        String[] result = new String[x.size()];
        x.toArray(result);
        return result;
    }

    private Stream<String> keyValuePairAsStream(Map.Entry<String, String> entry) {
        return Arrays.asList(entry.getKey(), entry.getValue()).stream();
    }

    private URI buildUriToPublicationService(UUID identifier) {
        return UrlBuilder.empty()
            .withScheme(apiScheme)
            .withHost(apiHost)
            .withPath(PATH + identifier.toString())
            .toUri();
    }
}
