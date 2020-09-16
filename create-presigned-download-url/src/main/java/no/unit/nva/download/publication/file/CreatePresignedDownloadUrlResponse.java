package no.unit.nva.download.publication.file;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CreatePresignedDownloadUrlResponse {

    private final String presignedDownloadUrl;

    @JsonCreator
    public CreatePresignedDownloadUrlResponse(
        @JsonProperty("presignedDownloadUrl") String presignedDownloadUrl) {
        this.presignedDownloadUrl = presignedDownloadUrl;
    }

    public String getPresignedDownloadUrl() {
        return presignedDownloadUrl;
    }
}
