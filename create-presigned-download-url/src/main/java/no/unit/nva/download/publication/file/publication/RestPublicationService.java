package no.unit.nva.download.publication.file.publication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mikael.urlbuilder.UrlBuilder;
import no.unit.nva.download.publication.file.publication.exception.NoResponseException;
import no.unit.nva.download.publication.file.publication.exception.NotFoundException;
import no.unit.nva.model.Publication;

import nva.commons.exceptions.ApiGatewayException;
import nva.commons.utils.Environment;
import nva.commons.utils.JsonUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

public class RestPublicationService {

    public static final String PATH = "/resource/";
    public static final String ACCEPT = "Accept";
    public static final String APPLICATION_JSON = "application/json";
    public static final String AUTHORIZATION = "Authorization";
    public static final String API_HOST_ENV = "API_HOST";
    public static final String API_SCHEME_ENV = "API_SCHEME";
    public static final String ITEMS_0 = "/Items/0";
    public static final String ERROR_COMMUNICATING_WITH_REMOTE_SERVICE = "Error communicating with remote service: ";
    public static final String ERROR_PUBLICATION_NOT_FOUND_FOR_IDENTIFIER = "Publication not found for identifier: ";

    private final ObjectMapper objectMapper;
    private final HttpClient client;
    private final String apiScheme;
    private final String apiHost;

    /**
     * Constructor for RestPublicationService.
     *
     * @param objectMapper objectMapper
     * @param client       client
     * @param apiScheme    apiScheme
     * @param apiHost      apiHost
     */
    public RestPublicationService(HttpClient client, ObjectMapper objectMapper, String apiScheme, String apiHost) {
        this.objectMapper = objectMapper;
        this.client = client;
        this.apiScheme = apiScheme;
        this.apiHost = apiHost;
    }

    /**
     * Constructor for RestPublicationService.
     *
     * @param environment environment
     */
    public RestPublicationService(Environment environment) {
        this(HttpClient.newHttpClient(), JsonUtils.objectMapper, environment.readEnv(API_SCHEME_ENV),
                environment.readEnv(API_HOST_ENV));
    }

    /**
     * Retrieve publication metadata.
     *
     * @param identifier    identifier
     * @param authorization authorization
     * @return A presigned download URL
     * @throws ApiGatewayException exception thrown if value is missing
     */
    public Publication getPublication(UUID identifier, String authorization) throws ApiGatewayException {
        URI uri = UrlBuilder.empty()
                .withScheme(apiScheme)
                .withHost(apiHost)
                .withPath(PATH + identifier.toString())
                .toUri();

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(uri)
                .header(ACCEPT, APPLICATION_JSON)
                .header(AUTHORIZATION, authorization)
                .GET()
                .build();

        try {
            HttpResponse<String> httpResponse = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            JsonNode jsonNode = objectMapper.readTree(httpResponse.body());
            JsonNode item0 = jsonNode.at(ITEMS_0);
            if (item0.isMissingNode()) {
                throw new NotFoundException(ERROR_PUBLICATION_NOT_FOUND_FOR_IDENTIFIER + identifier);
            }

            return objectMapper.readValue(objectMapper.writeValueAsString(item0), Publication.class);
        } catch (Exception e) {
            throw new NoResponseException(ERROR_COMMUNICATING_WITH_REMOTE_SERVICE + uri.toString(), e);
        }
    }

}
