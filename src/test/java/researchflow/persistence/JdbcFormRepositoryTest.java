package researchflow.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import researchflow.domain.Question;
import researchflow.domain.QuestionType;
import researchflow.service.FormService;
import researchflow.service.StudyService;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcFormRepositoryTest {
    @TempDir Path temporaryDirectory;

    @Test
    void draftStructureAndLifecyclePersistWithStableQuestionIdAndAudit() throws Exception {
        var connections = TestDatabase.migrated(temporaryDirectory);
        var transactions = new TransactionManager(connections);
        var writeGuard = new researchflow.service.StudyWriteGuard(new JdbcStudyRepository(connections, transactions));
        var studies = new StudyService(new JdbcStudyRepository(connections, transactions));
        var study = studies.create("Study", "", "", "", null, null, List.of());
        var forms = new FormService(new JdbcFormRepository(connections, transactions), writeGuard);
        var form = forms.create(study.id(), "Intake", "Baseline");
        var question = Question.create("age", "Age", "", QuestionType.NUMBER, true, 18d, 100d, List.of());
        var section = form.sections().getFirst().withQuestions(List.of(question));
        form = forms.updateStructure(form.id(), form.title(), form.description(), List.of(section));
        assertEquals(question.id(), forms.require(form.id()).questions().getFirst().id());

        form = forms.activate(form.id());
        var active = form;
        assertThrows(IllegalStateException.class, () -> forms.updateStructure(active.id(), "Changed", "", active.sections()));
        forms.close(active.id());

        try (var connection = connections.open();
             var statement = connection.prepareStatement("SELECT COUNT(*) FROM audit_logs WHERE entity_id = ?")) {
            statement.setString(1, form.id().toString());
            try (var rows = statement.executeQuery()) { rows.next(); assertEquals(4, rows.getInt(1)); }
        }
    }
}
