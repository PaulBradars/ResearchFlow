package researchflow.persistence;

import researchflow.domain.Answer;
import researchflow.domain.AnalysisMethod;
import researchflow.domain.AnalysisPlan;
import researchflow.domain.AnalysisResult;
import researchflow.domain.AnalysisSummary;
import researchflow.domain.ModelMetadata;
import researchflow.domain.StoredAnalysis;
import researchflow.domain.VersionAnswer;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcAnalysisRepository implements AnalysisRepository {
    private static final String CHOICE_SEPARATOR = "";
    private final ConnectionFactory connections;
    private final TransactionManager transactions;

    public JdbcAnalysisRepository(ConnectionFactory connections, TransactionManager transactions) {
        this.connections = connections;
        this.transactions = transactions;
    }

    @Override
    public List<VersionAnswer> loadVersionData(UUID datasetVersionId) {
        try (var connection = connections.open();
             var statement = connection.prepareStatement("""
                     SELECT m.response_id, s.question_id, s.value_json, m.excluded, q.question_type
                     FROM version_response_snapshots m
                     LEFT JOIN version_answer_snapshots s ON s.dataset_version_id=m.dataset_version_id AND s.response_id=m.response_id
                     LEFT JOIN questions q ON q.id=s.question_id
                     WHERE m.dataset_version_id = ?
                     """)) {
            statement.setString(1, datasetVersionId.toString());
            try (var rows = statement.executeQuery()) {
                var results = new ArrayList<VersionAnswer>();
                while (rows.next()) {
                    var responseId = UUID.fromString(rows.getString("response_id"));
                    if (rows.getString("question_id") == null) {
                        results.add(new VersionAnswer(responseId, null, null, rows.getInt("excluded") == 1));
                        continue;
                    }
                    var questionId = UUID.fromString(rows.getString("question_id"));
                    var value = SnapshotJson.parse(rows.getString("value_json"));
                    var answer = toAnswer(questionId, rows.getString("question_type"), value);
                    results.add(new VersionAnswer(responseId, questionId, answer, rows.getInt("excluded") == 1));
                }
                return List.copyOf(results);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load dataset version data for analysis.", exception);
        }
    }

    @Override
    public UUID save(UUID studyId, UUID datasetVersionId, AnalysisPlan plan, AnalysisResult result, int sampleSize,
                     List<String> warnings, String source, ModelMetadata modelMetadata) {
        var id = UUID.randomUUID();
        transactions.inTransaction(connection -> {
            JdbcStudyRepository.requireWritable(connection, studyId);
            try (var statement = connection.prepareStatement("""
                    INSERT INTO analyses(id, study_id, dataset_version_id, method, plan_json, result_json,
                        sample_size, warnings_json, source, model_metadata_json, created_at, evidence_schema_version, variables_json)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
                    """)) {
                statement.setString(1, id.toString());
                statement.setString(2, studyId.toString());
                statement.setString(3, datasetVersionId.toString());
                statement.setString(4, plan.method().name());
                statement.setString(5, planJson(plan));
                statement.setString(6, resultJson(result));
                statement.setInt(7, sampleSize);
                statement.setString(8, jsonArray(warnings));
                statement.setString(9, source);
                if (modelMetadata == null) statement.setNull(10, java.sql.Types.VARCHAR);
                else statement.setString(10, modelMetadataJson(modelMetadata));
                statement.setString(11, Instant.now().toString());
                statement.setString(12, variableJson(connection, plan));
                statement.executeUpdate();
            }
            insertAudit(connection, studyId, id, plan.method());
            return null;
        });
        return id;
    }

    @Override
    public List<AnalysisSummary> findByStudy(UUID studyId) {
        try (var connection = connections.open(); var statement = connection.prepareStatement("""
                SELECT a.id, a.method, a.dataset_version_id, v.version_number, a.sample_size, a.source, a.created_at
                FROM analyses a JOIN dataset_versions v ON v.id = a.dataset_version_id
                WHERE a.study_id = ? ORDER BY a.created_at DESC
                """)) {
            statement.setString(1, studyId.toString());
            try (var rows = statement.executeQuery()) {
                var results = new ArrayList<AnalysisSummary>();
                while (rows.next()) results.add(new AnalysisSummary(UUID.fromString(rows.getString("id")),
                        AnalysisMethod.valueOf(rows.getString("method")), UUID.fromString(rows.getString("dataset_version_id")),
                        rows.getInt("version_number"), rows.getInt("sample_size"), rows.getString("source"),
                        Instant.parse(rows.getString("created_at"))));
                return List.copyOf(results);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load analysis history.", exception);
        }
    }

    @Override
    public Optional<StoredAnalysis> findById(UUID analysisId) {
        try (var connection = connections.open();
             var statement = connection.prepareStatement("SELECT * FROM analyses WHERE id=?")) {
            statement.setString(1, analysisId.toString());
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                return Optional.of(new StoredAnalysis(UUID.fromString(rows.getString("id")),
                        UUID.fromString(rows.getString("study_id")), UUID.fromString(rows.getString("dataset_version_id")),
                        AnalysisMethod.valueOf(rows.getString("method")), rows.getString("plan_json"),
                        rows.getString("result_json"), rows.getInt("sample_size"), rows.getString("warnings_json"),
                        rows.getString("source"), Instant.parse(rows.getString("created_at")),
                        rows.getInt("evidence_schema_version"), rows.getString("variables_json"), rows.getString("model_metadata_json")));
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load the analysis.", exception);
        }
    }

    private static void insertAudit(Connection connection, UUID studyId, UUID analysisId, AnalysisMethod method) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO audit_logs(id, study_id, event_type, entity_type, entity_id, actor, occurred_at, details_json)
                VALUES (?, ?, 'ANALYSIS_RUN', 'ANALYSIS', ?, 'local-researcher', ?, ?)
                """)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, studyId.toString());
            statement.setString(3, analysisId.toString());
            statement.setString(4, Instant.now().toString());
            statement.setString(5, "{\"method\":\"" + method.name() + "\"}");
            statement.executeUpdate();
        }
    }

    private static Answer toAnswer(UUID questionId, String questionType, SnapshotJson.Value value) {
        if (value.isMissing()) return null;
        var id = UUID.randomUUID();
        var now = Instant.EPOCH;
        if (value.text() != null) {
            if (isChoiceType(questionType)) {
                return new Answer.Choice(id, questionId, Arrays.asList(value.text().split(CHOICE_SEPARATOR, -1)), now);
            }
            return new Answer.Text(id, questionId, value.text(), now);
        }
        if (value.number() != null) return new Answer.Number(id, questionId, value.number(), now);
        if (value.bool() != null) return new Answer.BooleanValue(id, questionId, value.bool() == 1, now);
        if (value.date() != null) return new Answer.DateValue(id, questionId, LocalDate.parse(value.date()), now);
        return null;
    }

    private static boolean isChoiceType(String questionType) {
        return questionType.equals("SINGLE_CHOICE") || questionType.equals("MULTIPLE_CHOICE")
                || questionType.equals("LIKERT") || questionType.equals("RATING");
    }

    private static String escape(String text) {
        var quoted = researchflow.util.Json.quote(text); return quoted.substring(1, quoted.length() - 1);
    }

    private static String variableJson(Connection connection, AnalysisPlan plan) throws SQLException {
        var ids = new ArrayList<UUID>(); ids.add(plan.primaryVariableId());
        if (plan.secondaryVariableId() != null) ids.add(plan.secondaryVariableId());
        var values = new ArrayList<String>();
        for (var id : ids) {
            try (var statement = connection.prepareStatement("SELECT label FROM questions WHERE id=?")) {
                statement.setString(1, id.toString());
                try (var rows = statement.executeQuery()) {
                    if (!rows.next()) throw new SQLException("Analysis variable no longer exists.");
                    values.add("{\"id\":" + researchflow.util.Json.quote(id.toString()) + ",\"label\":"
                            + researchflow.util.Json.quote(rows.getString(1)) + "}");
                }
            }
        }
        return "[" + String.join(",", values) + "]";
    }

    private static String modelMetadataJson(ModelMetadata metadata) {
        return "{\"model\":\"" + escape(metadata.model()) + "\",\"runtime\":\""
                + escape(metadata.runtime()) + "\",\"promptVersion\":\""
                + escape(metadata.promptVersion()) + "\"}";
    }

    private static String planJson(AnalysisPlan plan) {
        var builder = new StringBuilder("{\"method\":\"").append(plan.method().name()).append('"')
                .append(",\"primaryVariableId\":\"").append(plan.primaryVariableId()).append('"');
        if (plan.secondaryVariableId() != null) {
            builder.append(",\"secondaryVariableId\":\"").append(plan.secondaryVariableId()).append('"');
        }
        builder.append(",\"filters\":[");
        for (int index = 0; index < plan.filters().size(); index++) {
            var filter = plan.filters().get(index);
            if (index > 0) builder.append(',');
            builder.append("{\"questionId\":\"").append(filter.questionId()).append("\",\"operator\":\"")
                    .append(filter.operator().name()).append("\",\"value\":\"").append(escape(
                            filter.value() == null ? "" : filter.value())).append("\"}");
        }
        return builder.append("]}").toString();
    }

    private static String resultJson(AnalysisResult result) {
        return switch (result) {
            case AnalysisResult.Frequency value -> frequencyJson(value);
            case AnalysisResult.NumericSummary value -> "{\"type\":\"numeric_summary\",\"count\":" + value.count()
                    + ",\"missingCount\":" + value.missingCount() + ",\"mean\":" + value.mean() + ",\"median\":" + value.median()
                    + ",\"standardDeviation\":" + value.standardDeviation() + ",\"minimum\":" + value.minimum()
                    + ",\"maximum\":" + value.maximum() + "}";
            case AnalysisResult.Correlation value -> "{\"type\":\"correlation\",\"count\":" + value.count()
                    + ",\"coefficient\":" + value.coefficient() + "}";
            case AnalysisResult.CrossTabulation value -> crossTabulationJson(value);
            case AnalysisResult.GroupComparison value -> groupComparisonJson(value);
        };
    }

    private static String frequencyJson(AnalysisResult.Frequency value) {
        var builder = new StringBuilder("{\"type\":\"frequency\",\"totalCount\":").append(value.totalCount())
                .append(",\"missingCount\":").append(value.missingCount()).append(",\"categories\":[");
        for (int index = 0; index < value.categories().size(); index++) {
            var category = value.categories().get(index);
            if (index > 0) builder.append(',');
            builder.append("{\"value\":\"").append(escape(category.value())).append("\",\"count\":")
                    .append(category.count()).append(",\"percentage\":").append(category.percentage()).append('}');
        }
        return builder.append("]}").toString();
    }

    private static String crossTabulationJson(AnalysisResult.CrossTabulation value) {
        var builder = new StringBuilder("{\"type\":\"cross_tabulation\",\"rowLabels\":").append(jsonArray(value.rowLabels()))
                .append(",\"columnLabels\":").append(jsonArray(value.columnLabels())).append(",\"totalCount\":")
                .append(value.totalCount()).append(",\"counts\":[");
        for (int index = 0; index < value.counts().size(); index++) {
            if (index > 0) builder.append(',');
            builder.append(value.counts().get(index));
        }
        return builder.append("]}").toString();
    }

    private static String groupComparisonJson(AnalysisResult.GroupComparison value) {
        return "{\"type\":\"group_comparison\",\"groupALabel\":\"" + escape(value.groupALabel())
                + "\",\"groupACount\":" + value.groupACount() + ",\"groupAMean\":" + value.groupAMean()
                + ",\"groupASD\":" + value.groupASD() + ",\"groupBLabel\":\"" + escape(value.groupBLabel())
                + "\",\"groupBCount\":" + value.groupBCount() + ",\"groupBMean\":" + value.groupBMean()
                + ",\"groupBSD\":" + value.groupBSD() + ",\"meanDifference\":" + value.meanDifference()
                + ",\"tStatistic\":" + value.tStatistic() + ",\"degreesOfFreedom\":" + value.degreesOfFreedom()
                + ",\"cohensD\":" + value.cohensD() + "}";
    }

    private static String jsonArray(List<String> values) {
        var builder = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) builder.append(',');
            builder.append('"').append(escape(values.get(index))).append('"');
        }
        return builder.append(']').toString();
    }
}
