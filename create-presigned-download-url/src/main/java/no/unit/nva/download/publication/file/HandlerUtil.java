package no.unit.nva.download.publication.file;

import no.unit.nva.download.publication.file.exception.UnauthorizedException;
import no.unit.nva.download.publication.file.publication.exception.FileNotFoundException;
import no.unit.nva.model.File;
import no.unit.nva.model.FileSet;
import no.unit.nva.model.Publication;
import no.unit.nva.model.PublicationStatus;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.handlers.RequestInfo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class HandlerUtil {

    public static final String ERROR_MISSING_FILE_IN_PUBLICATION_FILE_SET = "File not found in publication file set";
    public static final String ERROR_DUPLICATE_FILES_IN_PUBLICATION = "Publication contains duplicate files";
    public static final String ERROR_MISSING_FILES_IN_PUBLICATION = "Publication does not have any associated files";
    public static final String ERROR_UNAUTHORIZED = "Unauthorized";

    private HandlerUtil() {
    }

    /**
     * Get valid file from publication.
     *
     * @param fileIdentifier fileIdentifier
     * @param fileSet fileSet
     * @return valid file
     * @throws ApiGatewayException exception thrown if valid file not present
     */
    public static File getValidFile(UUID fileIdentifier, FileSet fileSet) throws ApiGatewayException {

        Optional<List<File>> files = Optional.ofNullable(fileSet.getFiles());

        if (files.isPresent()) {
            return files.get().stream()
                    .filter(f -> f.getIdentifier().equals(fileIdentifier))
                    .reduce((a, b) -> {
                        throw new IllegalStateException(ERROR_DUPLICATE_FILES_IN_PUBLICATION);
                    })
                    .orElseThrow(() -> new FileNotFoundException(ERROR_MISSING_FILE_IN_PUBLICATION_FILE_SET));
        }
        throw new FileNotFoundException(ERROR_MISSING_FILES_IN_PUBLICATION);
    }

    /**
     * Authorize request if publication is published or user is publication owner.
     *
     * @param requestInfo requestInfo
     * @param publication publication
     * @throws ApiGatewayException when authorization fails.
     */
    public static void authorize(RequestInfo requestInfo, Publication publication) throws ApiGatewayException {
        if (publication.getStatus().equals(PublicationStatus.PUBLISHED)) {
            return;
        } else if (RequestUtil.getOwner(requestInfo).equalsIgnoreCase(publication.getOwner())) {
            return;
        }
        throw new UnauthorizedException(ERROR_UNAUTHORIZED);
    }

}
