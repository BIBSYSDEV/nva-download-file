package no.unit.nva.download.publication.file;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PresignedUriResponse {

    public static final String PRESIGNED_DOWNLOAD_URL = "presignedDownloadUrl";
    @JsonProperty(PRESIGNED_DOWNLOAD_URL)
    private final String presignedDownloadUrl;

    @JsonCreator
    public PresignedUriResponse(@JsonProperty(PRESIGNED_DOWNLOAD_URL) String presignedDownloadUrl) {
        this.presignedDownloadUrl = presignedDownloadUrl;
    }

    public String getPresignedDownloadUrl() {
        return presignedDownloadUrl;
    }
}
