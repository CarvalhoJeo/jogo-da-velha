package br.unisinos.velha.server.events;

import br.unisinos.velha.common.protocol.Json;
import br.unisinos.velha.server.db.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Requisito 9: registra cada evento relevante em arquivo (Logback, logs/server.log)
 * e na tabela ServerEvents.
 */
public class EventLogger {

    public enum Level { INFO, WARN, ERROR }

    private static final Logger LOG = LoggerFactory.getLogger("events");

    private final EventRepository repo;

    public EventLogger(EventRepository repo) {
        this.repo = repo;
    }

    public void info(String type, Integer userId, String gameId, Object... details) {
        log(Level.INFO, type, userId, gameId, details);
    }

    public void warn(String type, Integer userId, String gameId, Object... details) {
        log(Level.WARN, type, userId, gameId, details);
    }

    public void error(String type, Integer userId, String gameId, Object... details) {
        log(Level.ERROR, type, userId, gameId, details);
    }

    /** {@code details} é uma lista de pares chave/valor: "username", "ana", "cell", 4. */
    public void log(Level level, String type, Integer userId, String gameId, Object... details) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < details.length; i += 2) {
            map.put(String.valueOf(details[i]), details[i + 1]);
        }
        String json = Json.toJson(map);
        String line = "{} user={} game={} {}";
        switch (level) {
            case INFO -> LOG.info(line, type, userId, gameId, json);
            case WARN -> LOG.warn(line, type, userId, gameId, json);
            case ERROR -> LOG.error(line, type, userId, gameId, json);
        }
        try {
            repo.insert(Instant.now(), level.name(), type, userId, gameId, json);
        } catch (RuntimeException e) {
            // o log em arquivo continua valendo mesmo se o banco falhar
            LOG.error("Falha ao gravar evento {} no banco", type, e);
        }
    }
}
