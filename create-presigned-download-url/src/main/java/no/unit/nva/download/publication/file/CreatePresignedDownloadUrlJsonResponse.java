package no.unit.nva.download.publication.file;

public class CreatePresignedDownloadUrlJsonResponse {

    private final String presignedDownloadUrl;

    public CreatePresignedDownloadUrlJsonResponse(String presignedDownloadUrl) {
        this.presignedDownloadUrl = presignedDownloadUrl;
    }

    public String getPresignedDownloadUrl() {
        return presignedDownloadUrl;
    }
}
