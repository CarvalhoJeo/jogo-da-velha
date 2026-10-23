package br.unisinos.velha.server.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class EventRepository {

    public record EventRow(String timestamp, String level, String type, Integer userId, String gameId, String details) {
    }

    private final Database db;

    public EventRepository(Database db) {
        this.db = db;
    }

    public void insert(Instant ts, String level, String type, Integer userId, String gameId, String detailsJson) {
        db.with(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO ServerEvents(Timestamp, Level, Type, UserId, GameId, Details) VALUES (?,?,?,?,?,?)")) {
                ps.setString(1, ts.toString());
                ps.setString(2, level);
                ps.setString(3, type);
                if (userId == null) {
                    ps.setNull(4, Types.INTEGER);
                } else {
                    ps.setInt(4, userId);
                }
                ps.setString(5, gameId);
                ps.setString(6, detailsJson);
                return ps.executeUpdate();
            }
        });
    }

    public List<EventRow> byType(String type) {
        return db.with(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT Timestamp, Level, Type, UserId, GameId, Details FROM ServerEvents WHERE Type = ? ORDER BY Id")) {
                ps.setString(1, type);
                List<EventRow> rows = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int uid = rs.getInt(4);
                        rows.add(new EventRow(rs.getString(1), rs.getString(2), rs.getString(3),
                                rs.wasNull() ? null : uid, rs.getString(5), rs.getString(6)));
                    }
                }
                return rows;
            }
        });
    }
}
