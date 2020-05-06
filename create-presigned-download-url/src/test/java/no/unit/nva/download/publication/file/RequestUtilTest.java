package no.unit.nva.download.publication.file;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import no.unit.nva.download.publication.file.RequestUtil;
import no.unit.nva.download.publication.file.exception.UnauthorizedException;
import no.unit.nva.download.publication.file.publication.exception.InputException;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.handlers.RequestInfo;
import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static nva.commons.utils.JsonUtils.objectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RequestUtilTest {

    public static final String VALUE = "value";
    public static final String AUTHORIZER = "authorizer";
    public static final String CLAIMS = "claims";


    @Test
    public void getIdentifierReturnsIdentifierFromRequestWhenPresent() throws ApiGatewayException {
        UUID uuid = UUID.randomUUID();
        RequestInfo requestInfo = new RequestInfo();
        requestInfo.setPathParameters(Map.of(RequestUtil.IDENTIFIER, uuid.toString()));

        UUID identifier = RequestUtil.getIdentifier(requestInfo);

        assertEquals(uuid, identifier);
    }

    @Test
    public void getIdentifierThrowsExceptionWhenIdentifierNotPresent() {
        RequestInfo requestInfo = new RequestInfo();
        assertThrows(InputException.class, () -> RequestUtil.getIdentifier(requestInfo));
    }

    @Test
    public void getFileIdentifierThrowsExceptionWhenFileIdentifierNotPresent() {
        RequestInfo requestInfo = new RequestInfo();
        assertThrows(InputException.class, () -> RequestUtil.getFileIdentifier(requestInfo));
    }

    @Test
    public void getAuthorizationReturnsTheValueOfAuthorizationHeader() throws ApiGatewayException {
        RequestInfo requestInfo = new RequestInfo();
        requestInfo.setHeaders(Map.of(HttpHeaders.AUTHORIZATION, VALUE));

        String authorization = RequestUtil.getAuthorization(requestInfo);

        assertEquals(VALUE, authorization);
    }

    @Test
    public void getAuthorizationThrowsExceptionWhenAuthorizationNotPresent() {
        RequestInfo requestInfo = new RequestInfo();
        assertThrows(InputException.class, () -> RequestUtil.getAuthorization(requestInfo));
    }


    @Test
    public void getOwnerReturnsOwnerFromRequestWhenPresent() throws Exception {
        RequestInfo requestInfo = new RequestInfo();
        requestInfo.setRequestContext(getRequestContextForClaim(RequestUtil.CUSTOM_FEIDE_ID, VALUE));

        String owner = RequestUtil.getOwner(requestInfo);

        assertEquals(VALUE, owner);
    }

    @Test
    public void getOwnerThrowsExceptionWhenMissingClaimsNode() throws Exception {
        RequestInfo requestInfo = new RequestInfo();
        requestInfo.setRequestContext(getRequestContextWithMissingNode());

        assertThrows(InputException.class, () -> RequestUtil.getOwner(requestInfo));
    }

    @Test
    public void getOwnerThrowsExceptionWhenOwnerNotPresent() {
        RequestInfo requestInfo = new RequestInfo();
        assertThrows(InputException.class, () -> RequestUtil.getOwner(requestInfo));
    }

    private JsonNode getRequestContextWithMissingNode() throws JsonProcessingException {
        Map<String, Map<String, JsonNode>> map = Map.of(
                AUTHORIZER, Map.of(
                        CLAIMS, objectMapper.createObjectNode().nullNode()
                )
        );
        return objectMapper.readTree(objectMapper.writeValueAsString(map));
    }

    private JsonNode getRequestContextForClaim(String key, String value) throws JsonProcessingException {
        Map<String, Map<String, Map<String,String>>> map = Map.of(
            AUTHORIZER, Map.of(
                CLAIMS, Map.of(
                        key, value
                )
            )
        );
        return objectMapper.readTree(objectMapper.writeValueAsString(map));
    }

}
