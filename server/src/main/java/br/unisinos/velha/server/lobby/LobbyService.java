package br.unisinos.velha.server.lobby;

import br.unisinos.velha.common.protocol.ErrorCode;
import br.unisinos.velha.common.protocol.Json;
import br.unisinos.velha.common.protocol.Message;
import br.unisinos.velha.common.protocol.MessageType;
import br.unisinos.velha.server.events.EventLogger;
import br.unisinos.velha.server.game.GameManager;
import br.unisinos.velha.server.net.ConnectionHandler;
import br.unisinos.velha.server.presence.PresenceService;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Convites: criar, aceitar, recusar e expirar (requisitos 4 e 5). */
public class LobbyService {

    private record Invite(String id, String from, String to, ScheduledFuture<?> expiry) {
    }

    private final PresenceService presence;
    private final GameManager games;
    private final EventLogger events;
    private final ScheduledExecutorService scheduler;
    private final Duration timeout;
    private final Map<String, Invite> invites = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    public LobbyService(PresenceService presence, GameManager games, EventLogger events,
                        ScheduledExecutorService scheduler, Duration timeout) {
        this.presence = presence;
        this.games = games;
        this.events = events;
        this.scheduler = scheduler;
        this.timeout = timeout;
    }

    public void invite(ConnectionHandler from, long seq, String target) {
        String me = from.username();
        ConnectionHandler to = presence.get(target);
        if (target.equalsIgnoreCase(me)) {
            from.send(Message.error(seq, ErrorCode.BAD_REQUEST, "Não é possível convidar a si mesmo"));
            return;
        }
        if (to == null) {
            from.send(Message.error(seq, ErrorCode.USER_OFFLINE, "Usuário não está online"));
            return;
        }
        if (games.activeGameOf(me) != null || games.activeGameOf(to.username()) != null) {
            from.send(Message.error(seq, ErrorCode.USER_BUSY, "Jogador já está em uma partida"));
            return;
        }
        boolean duplicate = invites.values().stream()
                .anyMatch(i -> i.from().equalsIgnoreCase(me) && i.to().equalsIgnoreCase(to.username()));
        if (duplicate) {
            from.send(Message.error(seq, ErrorCode.INVALID_INVITE, "Já existe um convite pendente para esse jogador"));
            return;
        }

        String id = "i-" + ids.incrementAndGet();
        ScheduledFuture<?> expiry = scheduler.schedule(() -> expire(id), timeout.toMillis(), TimeUnit.MILLISECONDS);
        invites.put(id, new Invite(id, me, to.username(), expiry));

        long secs = timeout.toSeconds();
        from.send(new Message(MessageType.INVITE_SENT, seq, null,
                Json.object().put("inviteId", id).put("to", to.username()).put("expiresInSeconds", secs)));
        to.send(Message.of(MessageType.INVITE_RECEIVED,
                Json.object().put("inviteId", id).put("from", me).put("expiresInSeconds", secs)));
        events.info("INVITE_SENT", from.userId(), null, "inviteId", id, "from", me, "to", to.username());
    }

    public void reply(ConnectionHandler conn, long seq, String inviteId, boolean accept) {
        Invite inv = invites.get(inviteId);
        if (inv == null || !inv.to().equalsIgnoreCase(conn.username()) || !invites.remove(inviteId, inv)) {
            conn.send(Message.error(seq, ErrorCode.INVALID_INVITE, "Convite inexistente ou expirado"));
            return;
        }
        inv.expiry().cancel(false);
        ConnectionHandler inviter = presence.get(inv.from());

        if (!accept) {
            events.info("INVITE_DECLINED", conn.userId(), null, "inviteId", inviteId, "from", inv.from());
            sendResult(inviter, inv, "DECLINED");
            return;
        }
        if (inviter == null) {
            conn.send(Message.error(seq, ErrorCode.USER_OFFLINE, "Quem convidou saiu"));
            return;
        }
        if (games.start(inviter, conn) == null) {
            conn.send(Message.error(seq, ErrorCode.USER_BUSY, "Um dos jogadores já está em uma partida"));
            sendResult(inviter, inv, "CANCELLED");
            return;
        }
        events.info("INVITE_ACCEPTED", conn.userId(), null, "inviteId", inviteId, "from", inv.from());
        // Outros convites pendentes desses dois jogadores perdem o sentido
        cancelInvitesOf(inv.from());
        cancelInvitesOf(inv.to());
    }

    private void expire(String id) {
        Invite inv = invites.remove(id);
        if (inv == null) {
            return;
        }
        events.info("INVITE_EXPIRED", null, null, "inviteId", id, "from", inv.from(), "to", inv.to());
        sendResult(presence.get(inv.from()), inv, "EXPIRED");
        sendResult(presence.get(inv.to()), inv, "EXPIRED");
    }

    /** Cancela os convites que envolvem o usuário (saiu, caiu ou entrou em partida). */
    public void cancelInvitesOf(String username) {
        for (Invite inv : invites.values()) {
            if (inv.from().equalsIgnoreCase(username) || inv.to().equalsIgnoreCase(username)) {
                if (invites.remove(inv.id(), inv)) {
                    inv.expiry().cancel(false);
                    sendResult(presence.get(inv.from()), inv, "CANCELLED");
                    sendResult(presence.get(inv.to()), inv, "CANCELLED");
                }
            }
        }
    }

    private static void sendResult(ConnectionHandler target, Invite inv, String status) {
        if (target != null) {
            target.send(Message.of(MessageType.INVITE_RESULT, Json.object()
                    .put("inviteId", inv.id()).put("from", inv.from()).put("to", inv.to()).put("status", status)));
        }
    }
}
