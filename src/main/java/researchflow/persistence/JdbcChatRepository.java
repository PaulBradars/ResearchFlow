package researchflow.persistence;

import researchflow.domain.ChatMessage;
import researchflow.domain.ChatRole;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class JdbcChatRepository implements ChatRepository {
    private final ConnectionFactory connections;

    public JdbcChatRepository(ConnectionFactory connections) {
        this.connections = connections;
    }

    @Override
    public void save(ChatMessage message) {
        new TransactionManager(connections).inTransaction(connection -> {
            JdbcStudyRepository.requireWritable(connection, message.studyId());
            try (var statement = connection.prepareStatement("""
                     INSERT INTO chat_references(id, study_id, analysis_id, role, content, created_at)
                     VALUES (?, ?, ?, ?, ?, ?)
                     """)) {
                statement.setString(1, message.id().toString());
                statement.setString(2, message.studyId().toString());
                if (message.analysisId() == null) statement.setNull(3, Types.VARCHAR);
                else statement.setString(3, message.analysisId().toString());
                statement.setString(4, message.role().name());
                statement.setString(5, message.content());
                statement.setString(6, message.createdAt().toString());
                statement.executeUpdate();
            }
            return null;
        });
    }


    @Override
    public List<ChatMessage> findByStudy(UUID studyId, int limit) {
        var bounded = Math.max(1, Math.min(500, limit));
        try (var connection = connections.open();
             var statement = connection.prepareStatement(
                     "SELECT * FROM chat_references WHERE study_id=? ORDER BY created_at ASC LIMIT ?")) {
            statement.setString(1, studyId.toString());
            statement.setInt(2, bounded);
            try (var rows = statement.executeQuery()) {
                var messages = new ArrayList<ChatMessage>();
                while (rows.next()) messages.add(mapMessage(rows));
                return List.copyOf(messages);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load the chat history.", exception);
        }
    }

    private static ChatMessage mapMessage(ResultSet row) throws SQLException {
        var analysisId = row.getString("analysis_id");
        return new ChatMessage(UUID.fromString(row.getString("id")), UUID.fromString(row.getString("study_id")),
                analysisId == null ? null : UUID.fromString(analysisId), ChatRole.valueOf(row.getString("role")),
                row.getString("content"), Instant.parse(row.getString("created_at")));
    }
}
