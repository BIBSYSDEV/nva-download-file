package no.unit.nva.download.publication.file.publication.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import no.unit.nva.identifiers.SortableIdentifier;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public record Publication(SortableIdentifier identifier,
                          String status,
                          ResourceOwner resourceOwner,
                          EntityDescription entityDescription,
                          List<AssociatedArtifact> associatedArtifacts) {

}
