package no.unit.nva.download.publication.file.publication.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonTypeName("NullAssociatedArtifact")
public record NullAssociatedArtifact() implements AssociatedArtifact {

}
