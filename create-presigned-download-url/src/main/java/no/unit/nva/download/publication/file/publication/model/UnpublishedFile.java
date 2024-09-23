package no.unit.nva.download.publication.file.publication.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import java.time.Instant;
import java.util.UUID;

@JsonTypeInfo(use = Id.NAME, property = "type")
public class UnpublishedFile extends File {
    public static final String TYPE = "UnpublishedFile";

    public UnpublishedFile(@JsonProperty("identifier") UUID identifier,
                           @JsonProperty("mimeType") String mimeType,
                           @JsonProperty("embargoDate") Instant embargoDate,
                           @JsonProperty("administrativeAgreement") boolean administrativeAgreement) {
        super(identifier, mimeType, embargoDate, administrativeAgreement);
    }

    @Override
    @JsonIgnore
    public boolean isVisibleForNonOwner() {
        return false;
    }
}
