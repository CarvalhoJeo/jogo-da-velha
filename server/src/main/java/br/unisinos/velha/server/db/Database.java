package br.unisinos.velha.server.db;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Acesso ao SQLite. Uma única conexão, usada com exclusão mútua: o SQLite
 * serializa escritas de qualquer forma, e assim não há erros de "database is locked".
 */
public class Database implements AutoCloseable {

    @FunctionalInterface
    public interface SqlWork<T> {
        T run(Connection c) throws SQLException;
    }

    private final Connection connection;

    private Database(Connection connection) {
        this.connection = connection;
    }

    public static Database open(Path file) throws SQLException {
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
        Database db = new Database(c);
        db.migrate();
        return db;
    }

    public synchronized <T> T with(SqlWork<T> work) {
        try {
            return work.run(connection);
        } catch (SQLException e) {
            throw new DataAccessException(e);
        }
    }

    private void migrate() throws SQLException {
        try (Statement s = connection.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA foreign_keys=ON");
            s.execute("""
                    CREATE TABLE IF NOT EXISTS Users (
                        Id           INTEGER PRIMARY KEY AUTOINCREMENT,
                        Username     TEXT NOT NULL UNIQUE COLLATE NOCASE,
                        PasswordHash TEXT NOT NULL,
                        Salt         TEXT NOT NULL,
                        Iterations   INTEGER NOT NULL,
                        CreatedAt    TEXT NOT NULL,
                        LastLoginAt  TEXT
                    )""");
            s.execute("""
                    CREATE TABLE IF NOT EXISTS Games (
                        Id        INTEGER PRIMARY KEY AUTOINCREMENT,
                        PlayerXId INTEGER NOT NULL REFERENCES Users(Id),
                        PlayerOId INTEGER REFERENCES Users(Id),
                        BotLevel  TEXT,
                        Status    TEXT NOT NULL,
                        WinnerId  INTEGER REFERENCES Users(Id),
                        EndReason TEXT,
                        StartedAt TEXT NOT NULL,
                        EndedAt   TEXT
                    )""");
            s.execute("""
                    CREATE TABLE IF NOT EXISTS Moves (
                        Id         INTEGER PRIMARY KEY AUTOINCREMENT,
                        GameId     INTEGER NOT NULL REFERENCES Games(Id),
                        PlayerId   INTEGER NOT NULL REFERENCES Users(Id),
                        Cell       INTEGER NOT NULL CHECK (Cell BETWEEN 0 AND 8),
                        MoveNumber INTEGER NOT NULL,
                        CreatedAt  TEXT NOT NULL
                    )""");
            s.execute("""
                    CREATE TABLE IF NOT EXISTS ServerEvents (
                        Id        INTEGER PRIMARY KEY AUTOINCREMENT,
                        Timestamp TEXT NOT NULL,
                        Level     TEXT NOT NULL,
                        Type      TEXT NOT NULL,
                        UserId    INTEGER,
                        GameId    TEXT,
                        Details   TEXT
                    )""");
            s.execute("CREATE INDEX IF NOT EXISTS IdxMovesGame ON Moves(GameId)");
            s.execute("CREATE INDEX IF NOT EXISTS IdxEventsType ON ServerEvents(Type)");
        }
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // encerrando
        }
    }

    public static class DataAccessException extends RuntimeException {
        public DataAccessException(SQLException cause) {
            super(cause);
        }
    }
}
