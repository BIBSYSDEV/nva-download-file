package no.unit.nva.download.publication.file;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.net.URI;
import java.time.Instant;
import java.util.Date;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public class PresignedUri {

    public static final URI context = URI.create("https://nva.unit.no/context/nva");
    public static final String EXPIRES = "expires";
    public static final String ID = "id";
    public static final String CONTEXT = "@context";
    @JsonProperty(ID)
    private final String id;
    @JsonProperty(EXPIRES)
    private final Date expires;

    public PresignedUri(@JsonProperty(ID) String id, @JsonProperty(EXPIRES) Date expires) {
        this.id = id;
        this.expires = expires;
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

    @JsonGetter
    public Instant getExpires() {
        return expires.toInstant();
    }

    @JsonGetter(CONTEXT)
    public URI getContext() {
        return context;
    }
}
