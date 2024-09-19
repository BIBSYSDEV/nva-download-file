package no.unit.nva.download.publication.file;

import static no.unit.nva.download.publication.file.RequestUtil.getPersonCristinId;
import static no.unit.nva.download.publication.file.RequestUtil.getUser;
import static nva.commons.apigateway.AccessRight.MANAGE_DEGREE_EMBARGO;
import static nva.commons.apigateway.AccessRight.MANAGE_RESOURCES_STANDARD;
import java.util.UUID;
import java.util.stream.Stream;
import no.unit.nva.download.publication.file.exception.NotFoundException;
import no.unit.nva.download.publication.file.publication.model.File;
import no.unit.nva.download.publication.file.publication.model.Publication;
import no.unit.nva.download.publication.file.publication.model.PublicationStatusConstants;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ForbiddenException;

public class FileAccessValidationUtil {

    private final UUID fileIdentifier;
    private final Publication publication;

    public FileAccessValidationUtil(UUID fileIdentifier, Publication publication) {
        this.fileIdentifier = fileIdentifier;
        this.publication = publication;
    }

    public static FileAccessValidationUtil create(UUID fileIdentifier, Publication publication) {
        return new FileAccessValidationUtil(fileIdentifier, publication);
    }

    public void validateAccess(RequestInfo requestInfo) throws NotFoundException, ForbiddenException {
        if (!hasAccess(requestInfo)) {
            throw new ForbiddenException();
        }
    }

    private boolean hasAccess(RequestInfo requestInfo) throws NotFoundException {
        var file = getFile();
        return file.hasActiveEmbargo()
                   ? hasAccessToFileWithActiveEmbargo(requestInfo)
                   : hasAccessToFile(file, requestInfo);
    }

    private boolean hasAccessToFile(File file, RequestInfo requestInfo) {
        return Stream.of(isOwner(publication, requestInfo),
                         isContributor(publication, requestInfo),
                         isEditor(requestInfo),
                         fileIsVisibleForNonOwner(file, publication))
                   .anyMatch(Boolean::booleanValue);
    }

    private boolean isContributor(Publication publication, RequestInfo requestInfo) {
        return getPersonCristinId(requestInfo).map(publication::hasContributorWithId).orElse(false);
    }

    private boolean isOwner(Publication publication, RequestInfo requestInfo) {
        return publication.resourceOwner().owner().equals(getUser(requestInfo));
    }

    private boolean isEditor(RequestInfo requestInfo) {
        return requestInfo.userIsAuthorized(MANAGE_RESOURCES_STANDARD);
    }

    private boolean fileIsVisibleForNonOwner(File file, Publication publication) {
        return PublicationStatusConstants.PUBLISHED.equals(publication.status()) && file.isVisibleForNonOwner();
    }

    private boolean hasAccessToFileWithActiveEmbargo(RequestInfo requestInfo) {
        return Stream.of(isOwner(publication, requestInfo),
                         isContributor(publication, requestInfo),
                         canManageDegreeWithEmbargo(requestInfo))
                   .anyMatch(Boolean::booleanValue);
    }

    private boolean canManageDegreeWithEmbargo(RequestInfo requestInfo) {
        return publication.isThesis() && requestInfo.userIsAuthorized(MANAGE_DEGREE_EMBARGO);
    }

    private File getFile() throws NotFoundException {
        return publication.associatedArtifacts()
                   .stream()
                   .filter(File.class::isInstance)
                   .map(File.class::cast)
                   .filter(file -> file.getIdentifier().equals(this.fileIdentifier))
                   .findFirst()
                   .orElseThrow(() -> new NotFoundException(publication.identifier(), fileIdentifier));
    }
}
