package no.unit.nva.download.publication.file;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import no.unit.nva.download.publication.file.publication.exception.InputException;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.handlers.RequestInfo;
import org.apache.http.HttpHeaders;

import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RequestUtil {

    public static final String IDENTIFIER = "identifier";
    public static final String FILE_IDENTIFIER = "fileIdentifier";
    public static final String MISSING_AUTHORIZATION_IN_HEADERS = "Missing Authorization in Headers";
    public static final String IDENTIFIER_IS_NOT_A_VALID_UUID = "Identifier is not a valid UUID: ";
    public static final String AUTHORIZER_CLAIMS = "/authorizer/claims/";
    public static final String CUSTOM_FEIDE_ID = "custom:feideId";
    public static final String MISSING_CLAIM_IN_REQUEST_CONTEXT =
        "Missing claim in requestContext: ";

    public static final Logger logger = LoggerFactory.getLogger(RequestUtil.class);

    private RequestUtil() {
    }

    /**
     * Get Authorization header from request.
     *
     * @param requestInfo requestInfo
     * @return value of Authorization header
     * @throws ApiGatewayException exception thrown if value is missing
     */
    public static String getAuthorization(RequestInfo requestInfo) throws ApiGatewayException {
        try {
            String authorization = requestInfo.getHeaders().get(HttpHeaders.AUTHORIZATION);
            Objects.requireNonNull(authorization);
            return authorization;
        } catch (Exception e) {
            throw new InputException(MISSING_AUTHORIZATION_IN_HEADERS, e);
        }
    }

    /**
     * Get identifier from request path parameters.
     *
     * @param requestInfo requestInfo
     * @return the identifier
     * @throws ApiGatewayException exception thrown if value is missing
     */
    public static UUID getIdentifier(RequestInfo requestInfo) throws ApiGatewayException {
        String identifier = null;
        try {
            identifier = requestInfo.getPathParameters().get(IDENTIFIER);
            return UUID.fromString(identifier);
        } catch (Exception e) {
            throw new InputException(IDENTIFIER_IS_NOT_A_VALID_UUID + identifier, e);
        }
    }

    /**
     * Get file identifier from request path parameters.
     *
     * @param requestInfo requestInfo
     * @return the file identifier
     * @throws ApiGatewayException exception thrown if value is missing
     */
    public static UUID getFileIdentifier(RequestInfo requestInfo) throws ApiGatewayException {
        String fileIdentifier = null;
        try {
            fileIdentifier = requestInfo.getPathParameters().get(FILE_IDENTIFIER);
            return UUID.fromString(fileIdentifier);
        } catch (Exception e) {
            throw new InputException(IDENTIFIER_IS_NOT_A_VALID_UUID + fileIdentifier, e);
        }
    }

    /**
     * Get userId from requestContext authorizer claims.
     *
     * @param requestInfo requestInfo.
     * @return the userId
     */
    public static Optional<String> getUserId(RequestInfo requestInfo) {
        JsonNode jsonNode = requestInfo.getRequestContext().at(AUTHORIZER_CLAIMS + CUSTOM_FEIDE_ID);
        if (!jsonNode.isMissingNode()) {
            return Optional.ofNullable(jsonNode.textValue());
        }
        logger.warn(MISSING_CLAIM_IN_REQUEST_CONTEXT + CUSTOM_FEIDE_ID);
        return Optional.empty();
    }

}
