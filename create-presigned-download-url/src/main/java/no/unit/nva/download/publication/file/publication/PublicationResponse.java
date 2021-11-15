package no.unit.nva.download.publication.file.publication;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import no.unit.nva.file.model.FileSet;

import java.util.UUID;

import static java.util.Collections.emptyList;
import static java.util.Objects.nonNull;
import static no.unit.nva.download.publication.file.publication.PublicationStatus.PUBLISHED;

public class PublicationResponse {

    private final PublicationStatus status;
    private final UUID identifier;
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
                               @JsonProperty("identifier") UUID identifier,
                               @JsonProperty("owner") String owner,
                               @JsonProperty("fileSet") FileSet fileSet) {
        this.status = status;
        this.identifier = identifier;
        this.owner = owner;
        this.fileSet = fileSet;
    }

    public UUID getIdentifier() {
        return identifier;
    }

    public FileSet getFileSet() {
        return nonNull(fileSet) ? fileSet : new FileSet(emptyList());
    }

    public boolean isOwner(String user) {
        return nonNull(user) && owner.equals(user);
    }

    public boolean isPublished() {
        return PUBLISHED.equals(status);
    }
}
