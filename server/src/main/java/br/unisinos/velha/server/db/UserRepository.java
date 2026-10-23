package br.unisinos.velha.server.db;

import br.unisinos.velha.server.auth.PasswordHasher.HashedPassword;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

public class UserRepository {

    public record UserRecord(int id, String username, HashedPassword password) {
    }

    private final Database db;

    public UserRepository(Database db) {
        this.db = db;
    }

    /** Cria o usuário; devolve vazio se o nome já existe. */
    public Optional<Integer> create(String username, HashedPassword pw) {
        return db.with(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO Users(Username, PasswordHash, Salt, Iterations, CreatedAt) VALUES (?,?,?,?,?)")) {
                ps.setString(1, username);
                ps.setString(2, pw.hash());
                ps.setString(3, pw.salt());
                ps.setInt(4, pw.iterations());
                ps.setString(5, Instant.now().toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                if (e.getMessage() != null && e.getMessage().contains("UNIQUE")) {
                    return Optional.empty();
                }
                throw e;
            }
            try (var st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
                rs.next();
                return Optional.of(rs.getInt(1));
            }
        });
    }

    public Optional<UserRecord> findByUsername(String username) {
        return db.with(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT Id, Username, PasswordHash, Salt, Iterations FROM Users WHERE Username = ?")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new UserRecord(rs.getInt(1), rs.getString(2),
                            new HashedPassword(rs.getString(3), rs.getString(4), rs.getInt(5))));
                }
            }
        });
    }

    public void touchLastLogin(int userId) {
        db.with(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE Users SET LastLoginAt = ? WHERE Id = ?")) {
                ps.setString(1, Instant.now().toString());
                ps.setInt(2, userId);
                return ps.executeUpdate();
            }
        });
    }
}
