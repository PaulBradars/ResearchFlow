package researchflow.persistence;

import researchflow.domain.Finding;
import researchflow.domain.FindingStatus;
import researchflow.visualization.ChartSpec;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public final class JdbcFindingRepository implements FindingRepository {
    private final ConnectionFactory connections;
    private final TransactionManager transactions;

    public JdbcFindingRepository(ConnectionFactory connections, TransactionManager transactions) {
        this.connections = connections;
        this.transactions = transactions;
    }

    @Override
    public UUID create(UUID studyId, UUID analysisId, UUID datasetVersionId, String text, String evidenceSummary, ChartSpec chart) {
        var id = UUID.randomUUID();
        transactions.inTransaction(connection -> {
            JdbcStudyRepository.requireWritable(connection, studyId);
            var now = Instant.now();
            try (var statement = connection.prepareStatement("""
                    INSERT INTO findings(id, study_id, analysis_id, dataset_version_id, text, status,
                        created_at, updated_at, approved_at, evidence_summary, chart_json)
                    VALUES (?, ?, ?, ?, ?, 'DRAFT', ?, ?, NULL, ?, ?)
                    """)) {
                statement.setString(1, id.toString());
                statement.setString(2, studyId.toString());
                statement.setString(3, analysisId.toString());
                statement.setString(4, datasetVersionId.toString());
                statement.setString(5, text);
                statement.setString(6, now.toString());
                statement.setString(7, now.toString());
                if (evidenceSummary == null) statement.setNull(8, Types.VARCHAR);
                else statement.setString(8, evidenceSummary);
                if (chart == null) statement.setNull(9, Types.VARCHAR);
                else statement.setString(9, chartJson(chart));
                statement.executeUpdate();
            }
            insertAudit(connection, studyId, "FINDING_CREATED", id);
            return null;
        });
        return id;
    }

    @Override
    public void updateText(UUID findingId, String text) {
        transactions.inTransaction(connection -> {
            JdbcStudyRepository.requireWritable(connection, studyIdOf(connection, findingId));
            var now = Instant.now();
            try (var previous = connection.prepareStatement("""
                    INSERT INTO finding_text_revisions(id, finding_id, previous_text, previous_status, previous_approved_at, changed_at)
                    SELECT ?, id, text, status, approved_at, ? FROM findings WHERE id=? AND text<>?
                    """)) {
                previous.setString(1, UUID.randomUUID().toString()); previous.setString(2, now.toString());
                previous.setString(3, findingId.toString()); previous.setString(4, text);
                if (previous.executeUpdate() == 0) return null;
            }
            try (var statement = connection.prepareStatement("""
                    UPDATE findings SET text=?, updated_at=?,
                        status=CASE WHEN status='APPROVED' THEN 'DRAFT' ELSE status END,
                        approved_at=NULL WHERE id=?
                    """)) {
                statement.setString(1, text);
                statement.setString(2, now.toString());
                statement.setString(3, findingId.toString());
                if (statement.executeUpdate() != 1) throw new IllegalArgumentException("The finding no longer exists.");
            }
            insertAudit(connection, studyIdOf(connection, findingId), "FINDING_UPDATED", findingId);
            return null;
        });
    }

    @Override
    public void approve(UUID findingId) {
        setStatus(findingId, "APPROVED", true, "FINDING_APPROVED");
    }

    @Override
    public void reject(UUID findingId) {
        setStatus(findingId, "REJECTED", false, "FINDING_REJECTED");
    }

    private void setStatus(UUID findingId, String status, boolean stampApproval, String auditEvent) {
        transactions.inTransaction(connection -> {
            JdbcStudyRepository.requireWritable(connection, studyIdOf(connection, findingId));
            var now = Instant.now().toString();
            var sql = stampApproval ? "UPDATE findings SET status=?, updated_at=?, approved_at=? WHERE id=?"
                    : "UPDATE findings SET status=?, updated_at=?, approved_at=NULL WHERE id=?";
            try (var statement = connection.prepareStatement(sql)) {
                statement.setString(1, status);
                statement.setString(2, now);
                if (stampApproval) {
                    statement.setString(3, now);
                    statement.setString(4, findingId.toString());
                } else {
                    statement.setString(3, findingId.toString());
                }
                if (statement.executeUpdate() != 1) throw new IllegalArgumentException("The finding no longer exists.");
            }
            insertAudit(connection, studyIdOf(connection, findingId), auditEvent, findingId);
            return null;
        });
    }

    @Override
    public List<Finding> findByStudy(UUID studyId) {
        try (var connection = connections.open(); var statement = connection.prepareStatement(
                "SELECT * FROM findings WHERE study_id=? ORDER BY created_at DESC")) {
            statement.setString(1, studyId.toString());
            try (var rows = statement.executeQuery()) {
                var findings = new ArrayList<Finding>();
                while (rows.next()) findings.add(mapFinding(rows));
                return List.copyOf(findings);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load findings.", exception);
        }
    }

    @Override
    public Optional<Finding> findById(UUID findingId) {
        try (var connection = connections.open();
             var statement = connection.prepareStatement("SELECT * FROM findings WHERE id=?")) {
            statement.setString(1, findingId.toString());
            try (var rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(mapFinding(rows)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load the finding.", exception);
        }
    }

    private static UUID studyIdOf(Connection connection, UUID findingId) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT study_id FROM findings WHERE id=?")) {
            statement.setString(1, findingId.toString());
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw new IllegalArgumentException("The finding no longer exists.");
                return UUID.fromString(rows.getString(1));
            }
        }
    }

    private static void insertAudit(Connection connection, UUID studyId, String eventType, UUID entityId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO audit_logs(id, study_id, event_type, entity_type, entity_id, actor, occurred_at, details_json)
                VALUES (?, ?, ?, 'FINDING', ?, 'local-researcher', ?, '{}')
                """)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, studyId.toString());
            statement.setString(3, eventType);
            statement.setString(4, entityId.toString());
            statement.setString(5, Instant.now().toString());
            statement.executeUpdate();
        }
    }

    private static Finding mapFinding(ResultSet row) throws SQLException {
        var approvedAt = row.getString("approved_at");
        var chartJson = row.getString("chart_json");
        return new Finding(UUID.fromString(row.getString("id")), UUID.fromString(row.getString("study_id")),
                UUID.fromString(row.getString("analysis_id")), UUID.fromString(row.getString("dataset_version_id")),
                row.getString("text"), row.getString("evidence_summary"),
                chartJson == null ? null : parseChart(chartJson), FindingStatus.valueOf(row.getString("status")),
                Instant.parse(row.getString("created_at")), Instant.parse(row.getString("updated_at")),
                approvedAt == null ? null : Instant.parse(approvedAt));
    }

    // --- chart_json codec: every ChartSpec variant is a flat list of primitives, so this stays self-contained. ---

    private static String chartJson(ChartSpec chart) {
        return switch (chart) {
            case ChartSpec.Bar bar -> "{\"type\":\"bar\",\"title\":\"" + esc(bar.title()) + "\",\"xLabel\":\""
                    + esc(bar.xLabel()) + "\",\"yLabel\":\"" + esc(bar.yLabel()) + "\",\"categories\":"
                    + stringArray(bar.categories()) + ",\"values\":" + numberArray(bar.values()) + "}";
            case ChartSpec.Histogram histogram -> "{\"type\":\"histogram\",\"title\":\"" + esc(histogram.title())
                    + "\",\"xLabel\":\"" + esc(histogram.xLabel()) + "\",\"binLabels\":" + stringArray(histogram.binLabels())
                    + ",\"binCounts\":" + intArray(histogram.binCounts()) + "}";
            case ChartSpec.Scatter scatter -> "{\"type\":\"scatter\",\"title\":\"" + esc(scatter.title())
                    + "\",\"xLabel\":\"" + esc(scatter.xLabel()) + "\",\"yLabel\":\"" + esc(scatter.yLabel())
                    + "\",\"xValues\":" + numberArray(scatter.xValues()) + ",\"yValues\":" + numberArray(scatter.yValues()) + "}";
        };
    }

    private static ChartSpec parseChart(String json) {
        var type = stringField(json, "type");
        var title = stringField(json, "title");
        return switch (type) {
            case "bar" -> new ChartSpec.Bar(title, stringField(json, "xLabel"), stringField(json, "yLabel"),
                    stringArrayField(json, "categories"), numberArrayField(json, "values"));
            case "histogram" -> new ChartSpec.Histogram(title, stringField(json, "xLabel"),
                    stringArrayField(json, "binLabels"), intArrayField(json, "binCounts"));
            case "scatter" -> new ChartSpec.Scatter(title, stringField(json, "xLabel"), stringField(json, "yLabel"),
                    numberArrayField(json, "xValues"), numberArrayField(json, "yValues"));
            default -> throw new IllegalStateException("Unknown stored chart type: " + type);
        };
    }

    private static String stringField(String json, String key) {
        var matcher = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        return matcher.find() ? unescape(matcher.group(1)) : null;
    }

    private static String arrayField(String json, String key) {
        var matcher = Pattern.compile("\"" + key + "\"\\s*:\\s*\\[").matcher(json);
        if (!matcher.find()) return "[]";
        var start = matcher.end() - 1;
        var depth = 0;
        for (int index = start; index < json.length(); index++) {
            var character = json.charAt(index);
            if (character == '[') depth++;
            else if (character == ']') {
                depth--;
                if (depth == 0) return json.substring(start, index + 1);
            }
        }
        return "[]";
    }

    private static List<String> stringArrayField(String json, String key) {
        var values = new ArrayList<String>();
        var matcher = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(arrayField(json, key));
        while (matcher.find()) values.add(unescape(matcher.group(1)));
        return values;
    }

    private static List<Double> numberArrayField(String json, String key) {
        var values = new ArrayList<Double>();
        var matcher = Pattern.compile("-?[0-9]+(?:\\.[0-9]+)?").matcher(arrayField(json, key));
        while (matcher.find()) values.add(Double.valueOf(matcher.group()));
        return values;
    }

    private static List<Integer> intArrayField(String json, String key) {
        return numberArrayField(json, key).stream().map(Double::intValue).toList();
    }

    private static String stringArray(List<String> values) {
        var builder = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) builder.append(',');
            builder.append('"').append(esc(values.get(index))).append('"');
        }
        return builder.append(']').toString();
    }

    private static String numberArray(List<Double> values) {
        var builder = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) builder.append(',');
            builder.append(values.get(index));
        }
        return builder.append(']').toString();
    }

    private static String intArray(List<Integer> values) {
        var builder = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) builder.append(',');
            builder.append(values.get(index));
        }
        return builder.append(']').toString();
    }

    private static String esc(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String unescape(String value) {
        return value.replace("\\\"", "\"").replace("\\n", "\n").replace("\\r", "\r").replace("\\\\", "\\");
    }
}
