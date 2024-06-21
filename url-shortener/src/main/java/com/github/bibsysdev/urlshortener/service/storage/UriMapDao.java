package com.github.bibsysdev.urlshortener.service.storage;

import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemUtils;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.github.bibsysdev.urlshortener.service.model.UriMap;
import java.util.Map;
import no.unit.nva.commons.json.JsonUtils;

public class UriMapDao {

    private final UriMap uriMap;

    public UriMapDao(UriMap uriMap) {
        this.uriMap = uriMap;
    }

    public UriMapDao(Map<String, AttributeValue> valuesMap) {
        this.uriMap = fromDynamoFormat(valuesMap);
    }

    public UriMap getUriMap() {
        return uriMap;
    }

    public Map<String, AttributeValue> toDynamoFormat() {
        var item = attempt(() -> Item.fromJSON(
            JsonUtils.dynamoObjectMapper.writeValueAsString(this.getUriMap()))).orElseThrow();
        return ItemUtils.toAttributeValues(item);
    }

    private static UriMap fromDynamoFormat(Map<String, AttributeValue> valuesMap) {
        var item = ItemUtils.toItem(valuesMap);
        return attempt(() -> JsonUtils.dynamoObjectMapper
                                 .readValue(item.toJSON(), UriMap.class))
                   .orElseThrow();
    }
}
