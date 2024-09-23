package no.unit.nva.download.publication.file.publication.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(File.class),
    @JsonSubTypes.Type(name = "AssociatedLink", value = AssociatedLink.class),
    @JsonSubTypes.Type(name = "NullAssociatedArtifact", value = NullAssociatedArtifact.class)
})
public interface AssociatedArtifact {

}
