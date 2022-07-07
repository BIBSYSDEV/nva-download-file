package no.unit.nva.download.publication.file.publication;

import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import no.unit.nva.file.model.FileSet;

public class Event {

    @JsonProperty("type")
    private static final String type = "Publication";
    @JsonProperty("identifier")
    public final UUID identifier;
    @JsonProperty("resourceOwner")
    private final ResourceOwner resourceOwner;
    @JsonProperty("status")
    private final PublicationStatus status;
    @JsonProperty("fileSet")
    private final FileSet fileSet;

    /**
     * Constructs a fake event sent to the handler.
     *
     * @param resourceOwner The file owner
     * @param status        The status of the publication
     * @param fileSet       The set of files associated with the publication
     */
    public Event(ResourceOwner resourceOwner, UUID identifier, PublicationStatus status, FileSet fileSet) {
        this.resourceOwner = resourceOwner;
        this.identifier = identifier;
        this.status = status;
        this.fileSet = fileSet;
    }

    public Event(String resourceOwner, UUID identifier, PublicationStatus status, FileSet fileSet) {
        this(new ResourceOwner(resourceOwner, randomUri()), identifier, status, fileSet);
    }
}
