package no.unit.nva.download.publication.file;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import nva.commons.core.JacocoGenerated;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public class PresignedUri {

    public static final URI context = URI.create("https://nva.unit.no/context/nva");
    public static final String EXPIRES = "expires";
    public static final String ID = "id";
    public static final String CONTEXT = "@context";
    public static final String SHORTENED_VERSION = "shortenedVersion";
    @JsonProperty(ID)
    private final String id;
    @JsonProperty(EXPIRES)
    private final Instant expires;

    @JsonProperty(SHORTENED_VERSION)
    private final String shortenedVersion;

    @JsonCreator
    public PresignedUri(@JsonProperty(ID) String id, @JsonProperty(EXPIRES) Instant expires,
                        @JsonProperty(SHORTENED_VERSION) String shortenedVersion) {
        this.id = id;
        this.expires = expires;
        this.shortenedVersion = shortenedVersion;
    }

    /**
     * This method is replaced by {@link #getId() getId}, which makes sense semantically.
     * It is maintained to keep compatibility with the API.
     * @return String
     */
    @JsonGetter
    @Deprecated
    public String getPresignedDownloadUrl() {
        return id;
    }

    @JsonGetter
    public String getId() {
        return id;
    }

    @JacocoGenerated
    @JsonGetter
    public String getShortenedVersion() {
        return shortenedVersion;
    }

    @JsonGetter
    public Instant getExpires() {
        return expires;
    }

    @JsonGetter(CONTEXT)
    public URI getContext() {
        return context;
    }

    @JacocoGenerated
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PresignedUri that)) {
            return false;
        }
        return Objects.equals(id, that.id)
               && Objects.equals(expires, that.expires)
               && Objects.equals(shortenedVersion, that.shortenedVersion);
    }

    @JacocoGenerated
    @Override
    public int hashCode() {
        return Objects.hash(id, expires, shortenedVersion);
    }
}
