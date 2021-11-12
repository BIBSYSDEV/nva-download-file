package no.unit.nva.download.publication.file.publication;

import com.fasterxml.jackson.annotation.JsonProperty;
import no.unit.nva.file.model.FileSet;

public class Event {
    @JsonProperty("type")
    private static final String type = "Publication";
    @JsonProperty("owner")
    private final String owner;
    @JsonProperty("status")
    private final PublicationStatus status;
    @JsonProperty("fileSet")
    private final FileSet fileSet;

    /**
     * Constructs a fake event sent to the handler.
     * @param owner The file owner
     * @param status The status of the publication
     * @param fileSet The set of files associated with the publication
     */
    public Event(String owner, PublicationStatus status, FileSet fileSet) {
        this.owner = owner;
        this.status = status;
        this.fileSet = fileSet;
    }
}
