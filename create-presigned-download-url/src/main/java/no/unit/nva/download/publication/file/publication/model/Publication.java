package no.unit.nva.download.publication.file.publication.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import no.unit.nva.identifiers.SortableIdentifier;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonTypeName("Publication")
public record Publication(SortableIdentifier identifier,
                          PublicationStatus status,
                          ResourceOwner resourceOwner,
                          EntityDescription entityDescription,
                          List<AssociatedArtifact> associatedArtifacts) {

}
