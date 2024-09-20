package no.unit.nva.download.publication.file.publication.model;

import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import no.unit.nva.identifiers.SortableIdentifier;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public record Publication(SortableIdentifier identifier,
                          String status,
                          ResourceOwner resourceOwner,
                          EntityDescription entityDescription,
                          List<AssociatedArtifact> associatedArtifacts) {

    public boolean isThesis() {
        return attempt(() -> entityDescription.reference().publicationInstance()).toOptional()
                   .map(PublicationInstance::isThesis)
                   .orElse(false);
    }

    public File getFile(UUID fileIdentifier) {
        return associatedArtifacts
                   .stream()
                   .filter(File.class::isInstance)
                   .map(File.class::cast)
                   .filter(f -> f.getIdentifier().equals(fileIdentifier))
                   .findFirst()
                   .orElseThrow();
    }

    public boolean hasContributorWithId(URI id) {
        return entityDescription.contributors().stream()
                   .map(Contributor::identity)
                   .map(Identity::id)
                   .anyMatch(id::equals);
    }
}
