package no.unit.nva.download.publication.file.publication.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(name = PublishedFile.TYPE, value = PublishedFile.class),
    @JsonSubTypes.Type(names = UnpublishedFile.TYPE, value = UnpublishedFile.class),
    @JsonSubTypes.Type(name = UnpublishableFile.TYPE, value = UnpublishableFile.class)
})
public abstract class File implements AssociatedArtifact {

    private final UUID identifier;
    private final String mimeType;
    private final Instant embargoDate;
    private final boolean administrativeAgreement;

    @JsonCreator
    public File(@JsonProperty("identifier") UUID identifier,
                @JsonProperty("mimeType") String mimeType,
                @JsonProperty("embargoDate") Instant embargoDate,
                @JsonProperty("administrativeAgreement") boolean administrativeAgreement) {
        this.identifier = identifier;
        this.mimeType = mimeType;
        this.embargoDate = embargoDate;
        this.administrativeAgreement = administrativeAgreement;
    }

    @JsonProperty("identifier")
    public UUID getIdentifier() {
        return identifier;
    }

    @JsonProperty("mimeType")
    public String getMimeType() {
        return mimeType;
    }

    @JsonProperty("embargoDate")
    public Instant getEmbargoDate() {
        return embargoDate;
    }

    @JsonProperty("administrativeAgreement")
    public boolean isAdministrativeAgreement() {
        return administrativeAgreement;
    }

    @JsonIgnore
    public boolean hasActiveEmbargo() {
        return Optional.ofNullable(embargoDate).map(date -> Instant.now().isBefore(date)).orElse(false);
    }

    public abstract boolean isVisibleForNonOwner();

    @Override
    public String toString() {
        return "File{"
               + "identifier=" + identifier
               + ", mimeType='" + mimeType + '\''
               + ", embargoDate=" + embargoDate
               + ", administrativeAgreement=" + administrativeAgreement
               + '}';
    }
}
