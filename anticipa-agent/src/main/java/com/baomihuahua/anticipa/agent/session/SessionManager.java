package com.baomihuahua.anticipa.agent.session;

import com.alibaba.fastjson2.JSON;
import com.baomihuahua.anticipa.agent.config.AgentProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Slf4j
public class SessionManager {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final String storePath;

    public SessionManager(AgentProperties agentProperties) {
        this.storePath = agentProperties.getStorePath();
        loadFromDisk();
    }

    public Session createSession(String userId, String title) {
        Session session = new Session();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUserId(userId);
        session.setTitle(title);
        session.setCreateTime(System.currentTimeMillis());
        sessions.put(session.getSessionId(), session);
        saveToDisk();
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
        saveToDisk();
    }

    /**
     * 持久化到磁盘，在每次增删改后调用。
     */
    public void saveToDisk() {
        try {
            Path path = Paths.get(storePath, "chat-sessions.json");
            Files.createDirectories(path.getParent());
            String json = JSON.toJSONString(sessions.values());
            Files.writeString(path, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("[SessionManager] 保存会话到磁盘失败", e);
        }
    }

    /**
     * 从磁盘加载，在构造时调用。
     */
    private void loadFromDisk() {
        try {
            Path path = Paths.get(storePath, "chat-sessions.json");
            if (!Files.exists(path)) {
                return;
            }
            String json = Files.readString(path, StandardCharsets.UTF_8);
            List<Session> list = JSON.parseArray(json, Session.class);
            if (list != null) {
                for (Session s : list) {
                    if (s.getSessionId() != null) {
                        sessions.put(s.getSessionId(), s);
                    }
                }
            }
            log.info("[SessionManager] 从磁盘加载了 {} 个会话", sessions.size());
        } catch (Exception e) {
            log.warn("[SessionManager] 从磁盘加载会话失败", e);
        }
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

        public Message() {}

        public Message(String role, String content, long timestamp) {
            this.role = role;
            this.content = content;
            this.timestamp = timestamp;
        }
    }
}
