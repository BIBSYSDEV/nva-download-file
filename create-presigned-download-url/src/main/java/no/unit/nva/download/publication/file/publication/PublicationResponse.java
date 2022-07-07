package no.unit.nva.download.publication.file.publication;

import static java.util.Collections.emptyList;
import static java.util.Objects.nonNull;
import static no.unit.nva.download.publication.file.publication.PublicationStatus.PUBLISHED;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import no.unit.nva.file.model.FileSet;
import no.unit.nva.identifiers.SortableIdentifier;

public class PublicationResponse {

    private final PublicationStatus status;
    private final SortableIdentifier identifier;

    private final ResourceOwner resourceOwner;
    private final FileSet fileSet;

    /**
     * Constructs a minimal usable object from a full publication.
     *
     * @param status        The publication status.
     * @param resourceOwner The owner identifier for the publication.
     * @param fileSet       The set of files associated with the publication.
     */
    @JsonCreator
    public PublicationResponse(@JsonProperty("status") PublicationStatus status,
                               @JsonProperty("identifier") SortableIdentifier identifier,
                               @JsonProperty("resourceOwner") ResourceOwner resourceOwner,
                               @JsonProperty("fileSet") FileSet fileSet) {
        this.status = status;
        this.identifier = identifier;
        this.resourceOwner = resourceOwner;
        this.fileSet = fileSet;
    }

    public SortableIdentifier getIdentifier() {
        return identifier;
    }

    public FileSet getFileSet() {
        return nonNull(fileSet) ? fileSet : new FileSet(emptyList());
    }

    public boolean isOwner(String user) {
        return Optional.of(resourceOwner)
            .map(ResourceOwner::getOwner)
            .map(owner -> owner.equals(user))
            .orElse(false);
    }

    public boolean isPublished() {
        return PUBLISHED.equals(status);
    }
}
