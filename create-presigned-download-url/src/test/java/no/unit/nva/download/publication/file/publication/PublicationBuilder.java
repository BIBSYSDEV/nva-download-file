package no.unit.nva.download.publication.file.publication;

import nva.commons.core.ioutils.IoUtils;

import java.nio.file.Path;
import java.util.UUID;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

public class PublicationBuilder {
    public static final String APPLICATION_PDF = "application/pdf";
    public static final String STRING = "\"";
    private final String template;
    private PublicationStatus status;
    private String owner;
    private String organization;
    private String fileIdentifier;
    private String identifier;
    private String mimeType;

    public PublicationBuilder(String template) {
        this.template = template;
    }

    public PublicationBuilder withStatus(PublicationStatus status) {
        this.status = status;
        return this;
    }

    public PublicationBuilder withOwner(String owner) {
        this.owner = owner;
        return this;
    }

    public PublicationBuilder withIdentifier(UUID identifier) {
        this.identifier = identifier.toString();
        return this;
    }

    public PublicationBuilder withOrganization(String organization) {
        this.organization = organization;
        return this;
    }

    public PublicationBuilder withFileIdentifier(UUID fileIdentifier) {
        this.fileIdentifier = nonNull(fileIdentifier) ? fileIdentifier.toString() : "";
        return this;
    }

    public PublicationBuilder withMimeType(String mimeType) {
        this.mimeType = isNull(mimeType) ? "null" : STRING + mimeType + STRING;
        return this;
    }

    /**
     * Constructs a publication-service response for use in testing.
     * @return JSON string representing an API response.
     */
    public String build() {
        return getTemplateAsString()
                .replace("__STATUS__", status.name())
                .replace("__IDENTIFIER__", identifier)
                .replace("__OWNER__", owner)
                .replace("__ORGANIZATION__", organization)
                .replace("__FILE_IDENTIFIER__", fileIdentifier)
                .replace("\"__MIME_TYPE__\"", mimeType);
    }

    private String getTemplateAsString() {
        return IoUtils.stringFromResources(Path.of(template));
    }
}
