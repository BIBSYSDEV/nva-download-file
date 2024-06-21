package com.github.bibsysdev.urlshortener.service;

import static java.util.Objects.isNull;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.github.bibsysdev.urlshortener.service.model.UriMap;
import com.github.bibsysdev.urlshortener.service.storage.UriMapDao;
import java.net.URI;
import nva.commons.apigateway.exceptions.GatewayResponseSerializingException;
import org.slf4j.Logger;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.BadGatewayException;
import nva.commons.apigateway.exceptions.NotFoundException;
import org.slf4j.LoggerFactory;

public class UriResolverImpl implements UriResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(UriResolverImpl.class);
    public static final String COULD_NOT_RESOLVE_MESSAGE = "could not resolve %s";

    private final AmazonDynamoDB client;
    private final String tableName;

    public UriResolverImpl(AmazonDynamoDB client, String tableName) {
        this.client = client;
        this.tableName = tableName;
    }

    @Override
    public URI resolve(URI shortenedUri) throws ApiGatewayException {
        var uriMap = findUriMapById(shortenedUri);
        return uriMap.longUri();
    }

    private static UriMap parseResultToUriMap(GetItemResult getItemResult) throws GatewayResponseSerializingException {
        try {
            return new UriMapDao(getItemResult.getItem()).getUriMap();
        } catch (Exception e) {
            throw new GatewayResponseSerializingException(e);
        }
    }

    private UriMap findUriMapById(URI shortenedUri) throws ApiGatewayException {
        var getItemResult = queryDatabase(shortenedUri);
        if (isNull(getItemResult.getItem())) {
            throw new NotFoundException(String.format(COULD_NOT_RESOLVE_MESSAGE, shortenedUri.toString()));
        }
        return parseResultToUriMap(getItemResult);
    }

    private GetItemResult queryDatabase(URI shortenedUri) throws ApiGatewayException {
        try {
            return client.getItem(createGetItemRequest(shortenedUri));
        } catch (Exception e) {
            LOGGER.error("DynamoDb exception: ", e);
            throw new BadGatewayException(String.format(COULD_NOT_RESOLVE_MESSAGE, shortenedUri));
        }
    }

    private GetItemRequest createGetItemRequest(URI shortenedUri) {
        return new GetItemRequest().withTableName(tableName).withKey(UriMapDao.createKey(shortenedUri));
    }
}
