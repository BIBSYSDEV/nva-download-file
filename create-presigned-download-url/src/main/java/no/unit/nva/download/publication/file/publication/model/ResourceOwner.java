package no.unit.nva.download.publication.file.publication.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import java.net.URI;

@JsonTypeInfo(use = Id.NAME, property = "type")
public record ResourceOwner(String owner, URI ownerAffiliation) {

}
