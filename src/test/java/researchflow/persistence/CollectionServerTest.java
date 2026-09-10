package researchflow.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import researchflow.collection.CollectionServer;
import researchflow.domain.*;
import researchflow.service.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class CollectionServerTest {
    @TempDir Path directory;

    @Test void browserCollectionValidatesPersistsAndRejectsClosedForms() throws Exception {
        var connections = TestDatabase.migrated(directory);
        var transactions = new TransactionManager(connections);
        var studies = new JdbcStudyRepository(connections, transactions);
        var study = new StudyService(studies).create("Web study", "", "", "", null, null, List.of());
        var guard = new StudyWriteGuard(studies);
        var repository = new JdbcFormRepository(connections, transactions);
        var forms = new FormService(repository, guard);
        var submissions = new ResponseSubmissionService(repository, new JdbcResponseRepository(connections, transactions), guard);
        var form = forms.create(study.id(), "Intake <script>", "Browser collection");
        var number = Question.create("age", "Age", "", QuestionType.NUMBER, true, 18d, 100d, List.of());
        var choices = Question.create("choices", "Choices", "", QuestionType.MULTIPLE_CHOICE, true, null, null,
                List.of(QuestionOption.create("A"), QuestionOption.create("B")));
        form = forms.updateStructure(form.id(), form.title(), form.description(),
                List.of(form.sections().getFirst().withQuestions(List.of(number, choices))));
        try (var server = new CollectionServer(forms, submissions, connections, new InetSocketAddress("127.0.0.1", 0), "" );
             var client = HttpClient.newHttpClient()) {
            var uri = URI.create("http://127.0.0.1:" + server.port() + "/forms/" + form.id());
            assertEquals(409, client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            forms.activate(form.id());
            var page = client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, page.statusCode());
            assertTrue(page.body().contains("Intake &lt;script&gt;"));
            var matcher = Pattern.compile("name='token' value='([^']+)'").matcher(page.body());
            assertTrue(matcher.find());
            var prefix = "token=" + matcher.group(1) + "&" + choices.id() + "=A&" + choices.id() + "=B&" + number.id() + "=";
            var invalid = post(client, uri, prefix + "12");
            assertEquals(422, invalid.statusCode());
            assertTrue(invalid.body().contains("value='12'"));
            assertEquals(200, post(client, uri, prefix + "24").statusCode());
            assertEquals(200, post(client, uri, prefix + "24").statusCode());
            try (var connection = connections.open(); var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT COUNT(*) FROM responses")) {
                assertTrue(rows.next()); assertEquals(1, rows.getInt(1));
            }
            assertEquals(400, post(client, uri, "token=invalid").statusCode());
            assertEquals(413, post(client, uri, "x=" + "a".repeat(262_144)).statusCode());
            forms.close(form.id());
            assertEquals(409, post(client, uri, prefix + "24").statusCode());
        }
    }

    private static HttpResponse<String> post(HttpClient client, URI uri, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(uri).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
