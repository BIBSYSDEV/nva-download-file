package no.unit.nva.download.publication.file;

import no.unit.nva.download.publication.file.publication.exception.InputException;
import nva.commons.apigateway.RequestInfo;
import nva.commons.core.StringUtils;

import java.util.Optional;
import java.util.UUID;

import static nva.commons.core.attempt.Try.attempt;

public final class RequestUtil {

    public static final String IDENTIFIER = "identifier";
    public static final String FILE_IDENTIFIER = "fileIdentifier";
    public static final String IDENTIFIER_IS_NOT_A_VALID_UUID = "Identifier is not a valid UUID: ";
    public static final String MISSING_RESOURCE_IDENTIFIER = "Missing Resource identifier";
    public static final String MISSING_FILE_IDENTIFIER = "Missing file identifier in request";

    public static final String ANONYMOUS = "anonymous";


    private RequestUtil() {
    }

    /**
     * Get identifier from request path parameters.
     *
     * @param requestInfo requestInfo
     * @return the identifier
     * @throws InputException if the resource identifier is missing
     */
    public static String getIdentifier(RequestInfo requestInfo) throws InputException {
        return Optional.of(requestInfo)
                   .map(RequestInfo::getPathParameters)
                   .map(pathParameters -> pathParameters.get(IDENTIFIER))
                   .orElseThrow(() -> new InputException(MISSING_RESOURCE_IDENTIFIER));
    }

    /**
     * Get file identifier from request path parameters.
     *
     * @param requestInfo requestInfo
     * @return the file identifier
     * @throws InputException if the file identifier is missing or invalid
     */
    public static UUID getFileIdentifier(RequestInfo requestInfo) throws InputException {

        String fileIdentifier = requestInfo.getPathParameters().get(FILE_IDENTIFIER);
        return validateUuid(fileIdentifier);
    }

    private static UUID validateUuid(String candidate) throws InputException {
        return checkUuid(checkMissingFileIdentifier(candidate));
    }

    private static UUID checkUuid(String fileIdentifier) throws InputException {
        try {
            return UUID.fromString(fileIdentifier);
        } catch (Exception e) {
            throw new InputException(IDENTIFIER_IS_NOT_A_VALID_UUID + fileIdentifier, e);
        }
    }

    private static String checkMissingFileIdentifier(String fileIdentifier) throws InputException {
        if (StringUtils.isBlank(fileIdentifier)) {
            throw new InputException(MISSING_FILE_IDENTIFIER);
        }
        return fileIdentifier;
    }

    public static String getUser(RequestInfo requestInfo) {
        return attempt(requestInfo::getUserName).orElse(user -> ANONYMOUS);
    }
}
