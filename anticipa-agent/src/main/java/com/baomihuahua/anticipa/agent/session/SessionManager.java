package com.baomihuahua.anticipa.agent.session;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Component
public class SessionManager {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public Session createSession(String userId, String title) {
        Session session = new Session();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUserId(userId);
        session.setTitle(title);
        session.setCreateTime(System.currentTimeMillis());
        sessions.put(session.getSessionId(), session);
        return session;
    }

    public Session getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public List<Session> getUserSessions(String userId) {
        return sessions.values().stream()
                .filter(s -> s.getUserId().equals(userId))
                .sorted((a, b) -> Long.compare(b.getCreateTime(), a.getCreateTime()))
                .collect(Collectors.toList());
    }

    public void deleteSession(String sessionId) {
        sessions.remove(sessionId);
    }

    @Data
    public static class Session {
        private String sessionId;
        private String userId;
        private String title;
        private long createTime;
        private List<Message> messages = new CopyOnWriteArrayList<>();
    }

    @Data
    public static class Message {
        private String role; // "user" / "assistant" / "system"
        private String content;
        private long timestamp;
    }
}
