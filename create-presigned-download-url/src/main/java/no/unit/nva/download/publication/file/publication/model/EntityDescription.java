package no.unit.nva.download.publication.file.publication.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import java.util.List;

@JsonTypeInfo(use = Id.NAME, property = "type")
public record EntityDescription(Reference reference, List<Contributor> contributors) {

}
