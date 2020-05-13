package no.unit.nva.download.publication.file;

public class CreatePresignedDownloadUrlResponse {

    private final String presignedDownloadUrl;

    public CreatePresignedDownloadUrlResponse(String presignedDownloadUrl) {
        this.presignedDownloadUrl = presignedDownloadUrl;
    }

    public String getPresignedDownloadUrl() {
        return presignedDownloadUrl;
    }
}
