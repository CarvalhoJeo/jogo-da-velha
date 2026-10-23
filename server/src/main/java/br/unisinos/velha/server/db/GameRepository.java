package br.unisinos.velha.server.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class GameRepository {

    public record LeaderboardRow(String username, int wins, int draws, int losses, int games) {
    }

    private final Database db;

    public GameRepository(Database db) {
        this.db = db;
    }

    public int create(int playerXId, int playerOId) {
        return db.with(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO Games(PlayerXId, PlayerOId, Status, StartedAt) VALUES (?,?,'IN_PROGRESS',?)")) {
                ps.setInt(1, playerXId);
                ps.setInt(2, playerOId);
                ps.setString(3, Instant.now().toString());
                ps.executeUpdate();
            }
            try (var st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
                rs.next();
                return rs.getInt(1);
            }
        });
    }

    public void addMove(int gameId, int playerId, int cell, int moveNumber) {
        db.with(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO Moves(GameId, PlayerId, Cell, MoveNumber, CreatedAt) VALUES (?,?,?,?,?)")) {
                ps.setInt(1, gameId);
                ps.setInt(2, playerId);
                ps.setInt(3, cell);
                ps.setInt(4, moveNumber);
                ps.setString(5, Instant.now().toString());
                return ps.executeUpdate();
            }
        });
    }

    public void updateStatus(int gameId, String status) {
        db.with(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE Games SET Status = ? WHERE Id = ?")) {
                ps.setString(1, status);
                ps.setInt(2, gameId);
                return ps.executeUpdate();
            }
        });
    }

    /** Encerra a partida. {@code winnerId} nulo significa empate ou partida sem vencedor. */
    public void finish(int gameId, Integer winnerId, String endReason) {
        db.with(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE Games SET Status = 'FINISHED', WinnerId = ?, EndReason = ?, EndedAt = ? WHERE Id = ?")) {
                if (winnerId == null) {
                    ps.setNull(1, Types.INTEGER);
                } else {
                    ps.setInt(1, winnerId);
                }
                ps.setString(2, endReason);
                ps.setString(3, Instant.now().toString());
                ps.setInt(4, gameId);
                return ps.executeUpdate();
            }
        });
    }

    /** Na inicialização: partidas que estavam em andamento quando o servidor caiu ficam registradas como abortadas. */
    public int abortUnfinished() {
        return db.with(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE Games SET Status = 'ABORTED', EndReason = 'SERVER_RESTART', EndedAt = ? "
                            + "WHERE Status IN ('IN_PROGRESS', 'PAUSED_DISCONNECTED')")) {
                ps.setString(1, Instant.now().toString());
                return ps.executeUpdate();
            }
        });
    }

    public String status(int gameId) {
        return db.with(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT Status FROM Games WHERE Id = ?")) {
                ps.setInt(1, gameId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }
        });
    }

    public List<Integer> moves(int gameId) {
        return db.with(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT Cell FROM Moves WHERE GameId = ? ORDER BY MoveNumber")) {
                ps.setInt(1, gameId);
                List<Integer> cells = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        cells.add(rs.getInt(1));
                    }
                }
                return cells;
            }
        });
    }

    /** Placar calculado sobre Games (docs/06-modelo-de-dados.md), sem tabela própria. */
    public List<LeaderboardRow> leaderboard(int limit) {
        return db.with(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT u.Username,
                           SUM(CASE WHEN g.WinnerId = u.Id THEN 1 ELSE 0 END) AS vitorias,
                           SUM(CASE WHEN g.EndReason = 'DRAW' THEN 1 ELSE 0 END) AS empates,
                           SUM(CASE WHEN g.WinnerId IS NOT NULL AND g.WinnerId <> u.Id THEN 1 ELSE 0 END) AS derrotas,
                           COUNT(*) AS partidas
                    FROM Users u
                    JOIN Games g ON g.PlayerXId = u.Id OR g.PlayerOId = u.Id
                    WHERE g.Status = 'FINISHED'
                    GROUP BY u.Id
                    ORDER BY vitorias DESC, empates DESC, u.Username
                    LIMIT ?""")) {
                ps.setInt(1, limit);
                List<LeaderboardRow> rows = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new LeaderboardRow(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5)));
                    }
                }
                return rows;
            }
        });
    }
}
