package no.unit.nva.download.publication.file.publication;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import no.unit.nva.file.model.FileSet;

public class PublicationResponse {

    private final PublicationStatus status;
    private final String owner;
    private final FileSet fileSet;

    /**
     * Constructs a minimal usable object from a full publication.
     * @param status The publication status.
     * @param owner The owner identifier for the publication.
     * @param fileSet The set of files associated with the publication.
     */
    @JsonCreator
    public PublicationResponse(@JsonProperty("status") PublicationStatus status,
                               @JsonProperty("owner") String owner,
                               @JsonProperty("fileSet") FileSet fileSet) {
        this.status = status;
        this.owner = owner;
        this.fileSet = fileSet;
    }

    public PublicationStatus getStatus() {
        return status;
    }

    public String getOwner() {
        return owner;
    }

    public FileSet getFileSet() {
        return fileSet;
    }
}
