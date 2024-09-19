package no.unit.nva.download.publication.file.publication.model;

import java.util.Set;

public record PublicationInstance(String type) {

    private static final Set<String> THESIS_TYPES = Set.of("DegreeBachelor", "DegreeMaster", "DegreePhd");

    public boolean isThesis() {
        return THESIS_TYPES.contains(type);
    }
}
