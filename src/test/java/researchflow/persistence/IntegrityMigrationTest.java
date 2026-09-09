package researchflow.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import researchflow.analysis.AnalysisData;
import researchflow.domain.*;
import researchflow.service.AnalysisService;
import researchflow.service.StudyWriteGuard;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IntegrityMigrationTest {
    @TempDir Path directory;
    private final UUID study = UUID.randomUUID();
    private final UUID form = UUID.randomUUID();
    private final UUID question = UUID.randomUUID();
    private final UUID answered = UUID.randomUUID();
    private final UUID blank = UUID.randomUUID();
    private final UUID version = UUID.randomUUID();

    @Test
    void upgradesV004WithoutInventingHistoricalBlankMembershipOrLosingLiveData() throws Exception {
        var connections = legacyDatabase();
        new MigrationRunner(connections).migrate();
        new MigrationRunner(connections).migrate();
        var transactions = new TransactionManager(connections);
        var versions = new JdbcVersionRepository(connections, transactions);
        var analyses = new JdbcAnalysisRepository(connections, transactions);
        var forms = new JdbcFormRepository(connections, transactions);
        var guard = new StudyWriteGuard(new JdbcStudyRepository(connections, transactions));
        var service = new AnalysisService(forms, versions, analyses, guard);

        assertEquals(1, versions.findByStudy(study).size());
        assertFalse(versions.findActive(study).orElseThrow().membershipComplete());
        assertEquals(DatasetFreshness.UNKNOWN_LEGACY, versions.freshness(study));
        assertEquals(1, AnalysisData.group(analyses.loadVersionData(version)).size());
        assertEquals(2, new JdbcResponseRepository(connections, transactions).findByStudy(study).size());
        var evidence = service.run(study, new AnalysisPlan(AnalysisMethod.FREQUENCY, question, null, List.of(), version));
        assertTrue(evidence.warnings().stream().anyMatch(w -> w.contains("legacy snapshot")));
        assertEquals(1, ((AnalysisResult.Frequency) evidence.result()).totalCount());
        var failure = assertThrows(IllegalStateException.class, () -> versions.restore(study, version, "Legacy restore"));
        assertTrue(failure.getMessage().contains("cannot be restored exactly"));
        assertEquals(version, versions.findActive(study).orElseThrow().id());

        var baseline = versions.createSnapshot(study, "Complete baseline after upgrade", "");
        assertTrue(baseline.membershipComplete());
        assertEquals(2, AnalysisData.group(analyses.loadVersionData(baseline.id())).size());
        assertEquals(DatasetFreshness.CURRENT, versions.freshness(study));
        var complete = service.run(study, new AnalysisPlan(AnalysisMethod.FREQUENCY, question, null, List.of(), baseline.id()));
        assertEquals(1, ((AnalysisResult.Frequency) complete.result()).missingCount());
        assertEquals(1, analyses.loadVersionData(version).size()); // Historical rows unchanged.
        try (var connection = connections.open(); var rows = connection.createStatement().executeQuery("PRAGMA foreign_key_check")) {
            assertFalse(rows.next());
        }
    }

    @Test
    void migrationFailureRollsBackSchemaChangesAndCanBeRetried() throws Exception {
        var connections = legacyDatabase();
        try (var connection = connections.open(); var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE version_response_snapshots(blocked INTEGER)");
        }
        assertThrows(PersistenceException.class, () -> new MigrationRunner(connections).migrate());
        try (var connection = connections.open(); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM schema_migrations")) {
                rows.next(); assertEquals(4, rows.getInt(1));
            }
            try (var rows = statement.executeQuery("PRAGMA table_info(responses)")) {
                while (rows.next()) assertNotEquals("in_dataset", rows.getString("name"));
            }
            statement.executeUpdate("DROP TABLE version_response_snapshots");
        }
        new MigrationRunner(connections).migrate();
        assertEquals(2, new JdbcResponseRepository(connections, new TransactionManager(connections)).findByStudy(study).size());
    }

    private ConnectionFactory legacyDatabase() throws Exception {
        var connections = new ConnectionFactory(directory.resolve("legacy.db"));
        try (var connection = connections.open(); var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE schema_migrations(version TEXT PRIMARY KEY, applied_at TEXT NOT NULL)");
            for (var migration : List.of("V001__initial_schema.sql", "V002__dataset_audit_baseline.sql",
                    "V003__quality_review_indexes.sql", "V004__finding_evidence_columns.sql")) {
                try (var resource = getClass().getClassLoader().getResourceAsStream("db/migration/" + migration)) {
                    assertNotNull(resource);
                    for (var sql : new String(resource.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                        if (!sql.isBlank()) statement.execute(sql);
                    }
                }
                try (var insert = connection.prepareStatement("INSERT INTO schema_migrations VALUES (?, CURRENT_TIMESTAMP)")) {
                    insert.setString(1, migration.substring(0, 4)); insert.executeUpdate();
                }
            }
            insert(connection, "INSERT INTO studies(id,title,status,created_at,updated_at) VALUES (?,'Legacy','ACTIVE',?,?)", study, now(), now());
            insert(connection, "INSERT INTO forms(id,study_id,title,status,created_at,updated_at) VALUES (?,?,'Survey','ACTIVE',?,?)", form, study, now(), now());
            var section = UUID.randomUUID();
            insert(connection, "INSERT INTO form_sections(id,form_id,title,position) VALUES (?,?,'Questions',0)", section, form);
            insert(connection, "INSERT INTO questions(id,form_id,section_id,variable_key,label,question_type,position,created_at,updated_at) VALUES (?,?,?,'value','Value','NUMBER',0,?,?)", question, form, section, now(), now());
            insert(connection, "INSERT INTO responses(id,form_id,form_version,status,submitted_at) VALUES (?,?,1,'COMPLETE',?)", answered, form, now());
            insert(connection, "INSERT INTO responses(id,form_id,form_version,status,submitted_at) VALUES (?,?,1,'COMPLETE',?)", blank, form, now());
            var answer = UUID.randomUUID();
            insert(connection, "INSERT INTO answers(id,response_id,question_id,value_number,created_at,updated_at) VALUES (?,?,?,10,?,?)", answer, answered, question, now(), now());
            insert(connection, "INSERT INTO dataset_versions(id,study_id,version_number,reason,created_at,is_active) VALUES (?,?,1,'Legacy snapshot',?,1)", version, study, now());
            insert(connection, "INSERT INTO version_answer_snapshots(dataset_version_id,answer_id,response_id,question_id,value_json,excluded) VALUES (?,?,?,?,?,0)", version, answer, answered, question, "{\"value_number\":10.0}");
        }
        return connections;
    }

    private static String now() { return "2026-09-01T00:00:00Z"; }

    private static void insert(java.sql.Connection connection, String sql, Object... args) throws Exception {
        try (var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) statement.setString(i + 1, args[i].toString());
            statement.executeUpdate();
        }
    }
}
