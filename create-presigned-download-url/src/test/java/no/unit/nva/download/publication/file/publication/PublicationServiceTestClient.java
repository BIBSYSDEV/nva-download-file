package no.unit.nva.download.publication.file.publication;

import static nva.commons.core.attempt.Try.attempt;
import java.net.http.HttpClient;
import java.util.List;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.download.publication.file.publication.model.Publication;

public class PublicationServiceTestClient {

    private final RestPublicationService publicationService;
    private final List<String> identifiers = List.of("01907a92eba1-8ad9bf15-e95f-4fc4-9ee5-bfafa83706db",
                                                     "01907a92dc21-ab287e6f-383f-4c86-9915-4c4bed2aef21",
                                                     "019092161f3c-c8f6436f-dace-4c17-8e63-57d144696182");

    public PublicationServiceTestClient() {
        var httpClient = HttpClient.newHttpClient();
        var apiHost = "api.test.nva.aws.unit.no";
        this.publicationService = new RestPublicationService(httpClient, JsonUtils.dtoObjectMapper, "https", apiHost);
    }

    public void fetchPublication() {
        var publications = identifiers.stream()
                               .map(this::fetchPublication)
                               .toList();

        System.out.println(publications);
    }

    private Publication fetchPublication(String identifier) {
        return attempt(() -> this.publicationService.getPublication(identifier)).orElseThrow();
    }

    public static void main(String[] args) {
        var client = new PublicationServiceTestClient();

        client.fetchPublication();
    }
}
