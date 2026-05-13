package com.baomihuahua.anticipa.agent.scheduled;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 定时任务 CRUD 服务。
 * <p>
 * 使用内存存储 + JSON 文件持久化，确保重启后任务不丢失。
 * 执行日志采用 JSON Lines 格式按日期分文件存储，与 ThreadPoolLogStore 架构一致。
 * 后续可替换为数据库实现。
 * </p>
 */
public class ScheduledTaskService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskService.class);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final Map<String, ScheduledTask> taskMap = new ConcurrentHashMap<>();
    private final Map<String, List<TaskExecutionLog>> executionLogMap = new ConcurrentHashMap<>();
    private final String storePath;
    private final String taskLogPath;
    private final int taskLogRetentionDays;

    public ScheduledTaskService(String storePath, int taskLogRetentionDays) {
        this.storePath = storePath;
        this.taskLogPath = storePath + File.separator + "task-logs";
        this.taskLogRetentionDays = taskLogRetentionDays;
        loadFromDisk();
        loadExecutionLogsFromDisk();
    }

    // ========== 任务 CRUD ==========

    public ScheduledTask create(ScheduledTask task) {
        validateRequiredFields(task);
        validateTaskNameForCreate(task);
        task.setTaskId(IdUtil.fastUUID());
        task.setStatus(task.isEnabled() ? ScheduledTask.TaskStatus.ENABLED : ScheduledTask.TaskStatus.DISABLED);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMap.put(task.getTaskId(), task);
        saveToDisk();
        log.info("[ScheduledTaskService] created task: id={}, name={}, threadPoolId={}, source={}",
                task.getTaskId(), task.getTaskName(), task.getThreadPoolId(), task.getSource());
        return task;
    }

    public ScheduledTask update(ScheduledTask task) {
        validateRequiredFields(task);
        task.setUpdatedAt(LocalDateTime.now());
        // 始终以 status 为准同步 enabled，防止 enabled/status 不一致
        task.setEnabled(task.getStatus() == ScheduledTask.TaskStatus.ENABLED
                || task.getStatus() == ScheduledTask.TaskStatus.RUNNING);
        taskMap.put(task.getTaskId(), task);
        saveToDisk();
        return task;
    }

    public ScheduledTask get(String taskId) {
        return taskMap.get(taskId);
    }

    public List<ScheduledTask> list() {
        List<ScheduledTask> tasks = List.copyOf(taskMap.values());
        log.info("[ScheduledTaskService] list() called, total tasks in memory: {}", tasks.size());
        return tasks;
    }

    public List<ScheduledTask> listEnabled() {
        return taskMap.values().stream()
                .filter(t -> t.getStatus() == ScheduledTask.TaskStatus.ENABLED)
                .collect(Collectors.toList());
    }

    public void delete(String taskId) {
        ScheduledTask existing = taskMap.get(taskId);
        if (existing == null) {
            return;
        }
        if (existing.getStatus() != ScheduledTask.TaskStatus.DISABLED) {
            throw new IllegalStateException(
                    "仅停用的任务可删除，当前状态: " + existing.getStatus() + "；请先停用后再删除");
        }
        taskMap.remove(taskId);
        executionLogMap.remove(taskId);
        saveToDisk();
        // 注意：不删除已持久化的执行日志文件，保留历史记录供审计
    }

    /**
     * 按任务 ID 或任务名称解析唯一任务（供 AI 工具等调用）。
     * <p>
     * {@code task_id} 与 {@code task_name} 二选一；若同时提供，以 {@code task_id} 为准。
     * 名称匹配规则：首尾空白 trim 后精确匹配；存在多个同名任务时抛出异常。
     * </p>
     */
    public ScheduledTask resolveTask(String taskId, String taskName) {
        if (taskId != null && !taskId.isBlank()) {
            ScheduledTask byId = get(taskId.trim());
            if (byId == null) {
                throw new IllegalArgumentException("未找到任务 ID: " + taskId.trim());
            }
            return byId;
        }
        if (taskName != null && !taskName.isBlank()) {
            return findSingleByTaskNameOrThrow(taskName.trim());
        }
        throw new IllegalArgumentException("请提供 task_id 或 task_name（二选一）");
    }

    /**
     * 停用：取消 Cron 调度意图下的「运行中」配置，状态为 {@link ScheduledTask.TaskStatus#DISABLED}。
     */
    public ScheduledTask disable(String taskId) {
        ScheduledTask task = taskMap.get(taskId);
        if (task == null) {
            return null;
        }
        if (task.getStatus() == ScheduledTask.TaskStatus.DISABLED) {
            return task;
        }
        task.setStatus(ScheduledTask.TaskStatus.DISABLED);
        task.setEnabled(false);
        task.setNextExecTime(null);
        task.setUpdatedAt(LocalDateTime.now());
        saveToDisk();
        return task;
    }

    /**
     * 启用：状态为 {@link ScheduledTask.TaskStatus#ENABLED}，由 {@link TaskScheduler#startTask} 负责挂上调度。
     */
    public ScheduledTask enable(String taskId) {
        ScheduledTask task = taskMap.get(taskId);
        if (task == null) {
            return null;
        }
        if (task.getStatus() == ScheduledTask.TaskStatus.ENABLED) {
            return task;
        }
        if (task.getStatus() == ScheduledTask.TaskStatus.RUNNING) {
            return task;
        }
        task.setStatus(ScheduledTask.TaskStatus.ENABLED);
        task.setEnabled(true);
        task.setUpdatedAt(LocalDateTime.now());
        saveToDisk();
        return task;
    }

    public ScheduledTask toggle(String taskId) {
        ScheduledTask task = taskMap.get(taskId);
        if (task == null) {
            return null;
        }
        if (task.getStatus() == ScheduledTask.TaskStatus.ENABLED
                || task.getStatus() == ScheduledTask.TaskStatus.RUNNING) {
            return disable(taskId);
        }
        return enable(taskId);
    }

    // ========== 执行日志 ==========

    private void validateRequiredFields(ScheduledTask task) {
        if (task.getThreadPoolId() == null || task.getThreadPoolId().isBlank()) {
            throw new IllegalArgumentException("threadPoolId 为必填参数");
        }
        if (task.getInstanceId() == null || task.getInstanceId().isBlank()) {
            throw new IllegalArgumentException("instanceId（实例地址 ip:port）为必填参数");
        }
    }

    private void validateTaskNameForCreate(ScheduledTask task) {
        if (task.getTaskName() == null || task.getTaskName().isBlank()) {
            throw new IllegalArgumentException("taskName（任务名称）为必填参数");
        }
        String normalized = task.getTaskName().trim();
        task.setTaskName(normalized);
        List<ScheduledTask> sameName = findAllByTrimmedTaskName(normalized);
        if (!sameName.isEmpty()) {
            throw new IllegalArgumentException("任务名称已存在: " + normalized);
        }
    }

    private List<ScheduledTask> findAllByTrimmedTaskName(String trimmedName) {
        return taskMap.values().stream()
                .filter(t -> trimmedName.equals(trimTaskName(t.getTaskName())))
                .collect(Collectors.toList());
    }

    private static String trimTaskName(String name) {
        return name == null ? "" : name.trim();
    }

    private ScheduledTask findSingleByTaskNameOrThrow(String trimmedName) {
        List<ScheduledTask> matches = findAllByTrimmedTaskName(trimmedName);
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("未找到名称为「" + trimmedName + "」的任务");
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException(
                    "存在多个同名任务「" + trimmedName + "」，请改用 task_id 指定唯一任务");
        }
        return matches.get(0);
    }

    public void addExecutionLog(TaskExecutionLog logEntry) {
        logEntry.setLogId(IdUtil.fastUUID());
        logEntry.setCreatedAt(LocalDateTime.now());
        executionLogMap.computeIfAbsent(logEntry.getTaskId(), k -> Collections.synchronizedList(new ArrayList<>())).add(logEntry);
        appendExecutionLogToDisk(logEntry);
    }

    public List<TaskExecutionLog> getExecutionLogs(String taskId) {
        return executionLogMap.getOrDefault(taskId, Collections.emptyList());
    }

    public List<TaskExecutionLog> getRecentExecutionLogs(String taskId, int limit) {
        List<TaskExecutionLog> logs = executionLogMap.getOrDefault(taskId, Collections.emptyList());
        int size = logs.size();
        if (size <= limit) {
            return List.copyOf(logs);
        }
        return List.copyOf(logs.subList(size - limit, size));
    }

    // ========== 任务定义持久化 ==========

    /**
     * 原子写入：先写临时文件再原子重命名，避免写入中断导致 JSON 文件损坏。
     */
    private void saveToDisk() {
        try {
            Path path = Paths.get(storePath, "scheduled-tasks.json");
            Files.createDirectories(path.getParent());
            String json = JSON.toJSONString(List.copyOf(taskMap.values()));

            Path tempFile = Paths.get(storePath, "scheduled-tasks.json.tmp");
            Files.writeString(tempFile, json, StandardCharsets.UTF_8);
            Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("[ScheduledTaskService] failed to save tasks", e);
        }
    }

    private void loadFromDisk() {
        Path path = Paths.get(storePath, "scheduled-tasks.json");
        if (!Files.exists(path)) {
            return;
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            List<ScheduledTask> tasks = JSON.parseArray(json, ScheduledTask.class);
            boolean migratedPaused = false;
            for (ScheduledTask task : tasks) {
                if (task.getStatus() == ScheduledTask.TaskStatus.PAUSED) {
                    task.setStatus(ScheduledTask.TaskStatus.DISABLED);
                    task.setEnabled(false);
                    task.setNextExecTime(null);
                    migratedPaused = true;
                }
                taskMap.put(task.getTaskId(), task);
            }
            if (migratedPaused) {
                saveToDisk();
                log.info("[ScheduledTaskService] migrated legacy PAUSED tasks to DISABLED");
            }
            log.info("[ScheduledTaskService] loaded {} tasks from disk", tasks.size());
        } catch (IOException e) {
            log.error("[ScheduledTaskService] failed to load tasks", e);
        }
    }

    // ========== 执行日志持久化（JSON Lines） ==========

    /**
     * 追加一条执行日志到磁盘。
     * <p>
     * 目录结构：{taskLogPath}/{taskId}/exec_{yyyyMMdd}.log
     * 每行一条 JSON 记录，与 ThreadPoolLogStore 的存储格式一致。
     * </p>
     */
    private void appendExecutionLogToDisk(TaskExecutionLog logEntry) {
        try {
            String dateKey = logEntry.getCreatedAt() != null
                    ? logEntry.getCreatedAt().format(DATE_FORMAT)
                    : LocalDate.now().format(DATE_FORMAT);
            Path filePath = getExecutionLogFilePath(logEntry.getTaskId(), dateKey);
            Files.createDirectories(filePath.getParent());

            String line = JSON.toJSONString(logEntry);
            Files.writeString(filePath, line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("[ScheduledTaskService] failed to append execution log to disk", e);
        }
    }

    /**
     * 从磁盘加载所有任务的执行日志到内存。
     * <p>
     * 仅加载保留天数内的日志文件，超期文件在 {@link #cleanExpiredLogs()} 中清理。
     * </p>
     */
    private void loadExecutionLogsFromDisk() {
        Path logBaseDir = Paths.get(taskLogPath);
        if (!Files.exists(logBaseDir)) {
            return;
        }

        LocalDate cutoffDate = LocalDate.now().minusDays(taskLogRetentionDays);
        String cutoffStr = cutoffDate.format(DATE_FORMAT);

        try {
            List<Path> taskDirs = Files.list(logBaseDir)
                    .filter(Files::isDirectory)
                    .collect(Collectors.toList());

            for (Path taskDir : taskDirs) {
                String taskId = taskDir.getFileName().toString();
                List<Path> logFiles = Files.list(taskDir)
                        .filter(p -> p.toString().endsWith(".log"))
                        .collect(Collectors.toList());

                List<TaskExecutionLog> allLogs = Collections.synchronizedList(new ArrayList<>());
                for (Path logFile : logFiles) {
                    // 仅加载保留天数内的文件
                    String fileName = logFile.getFileName().toString();
                    String fileDate = extractDateFromFileName(fileName);
                    if (fileDate != null && fileDate.compareTo(cutoffStr) < 0) {
                        continue;
                    }
                    loadLogsFromFile(logFile, allLogs);
                }

                // 按创建时间排序
                allLogs.sort(Comparator.comparing(TaskExecutionLog::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())));

                if (!allLogs.isEmpty()) {
                    executionLogMap.put(taskId, allLogs);
                    log.info("[ScheduledTaskService] loaded {} execution logs for task {}",
                            allLogs.size(), taskId);
                }
            }
        } catch (IOException e) {
            log.error("[ScheduledTaskService] failed to load execution logs from disk", e);
        }
    }

    private void loadLogsFromFile(Path logFile, List<TaskExecutionLog> targetList) {
        try {
            List<String> lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.isBlank()) {
                    continue;
                }
                try {
                    TaskExecutionLog logEntry = JSON.parseObject(line, TaskExecutionLog.class);
                    if (logEntry != null) {
                        targetList.add(logEntry);
                    }
                } catch (Exception e) {
                    log.warn("[ScheduledTaskService] failed to parse log line: {}", line, e);
                }
            }
        } catch (IOException e) {
            log.error("[ScheduledTaskService] failed to read log file: {}", logFile, e);
        }
    }

    /**
     * 清理超过保留天数的旧执行日志文件。
     */
    public void cleanExpiredLogs() {
        Path logBaseDir = Paths.get(taskLogPath);
        if (!Files.exists(logBaseDir)) {
            return;
        }

        LocalDate cutoffDate = LocalDate.now().minusDays(taskLogRetentionDays);
        String cutoffStr = cutoffDate.format(DATE_FORMAT);
        int cleanedCount = 0;

        try {
            List<Path> taskDirs = Files.list(logBaseDir)
                    .filter(Files::isDirectory)
                    .collect(Collectors.toList());

            for (Path taskDir : taskDirs) {
                List<Path> logFiles = Files.list(taskDir)
                        .filter(p -> p.toString().endsWith(".log"))
                        .collect(Collectors.toList());

                for (Path logFile : logFiles) {
                    String fileName = logFile.getFileName().toString();
                    String fileDate = extractDateFromFileName(fileName);
                    if (fileDate != null && fileDate.compareTo(cutoffStr) < 0) {
                        Files.deleteIfExists(logFile);
                        cleanedCount++;
                        log.info("[ScheduledTaskService] cleaned expired execution log: {}", logFile);
                    }
                }

                // 删除空目录
                if (Files.list(taskDir).findAny().isEmpty()) {
                    Files.deleteIfExists(taskDir);
                }
            }
        } catch (IOException e) {
            log.error("[ScheduledTaskService] failed to clean expired logs", e);
        }

        if (cleanedCount > 0) {
            log.info("[ScheduledTaskService] cleaned {} expired execution log files", cleanedCount);
        }
    }

    private Path getExecutionLogFilePath(String taskId, String dateKey) {
        return Paths.get(taskLogPath, taskId, "exec_" + dateKey + ".log");
    }

    /**
     * 从文件名中提取日期部分。
     * <p>
     * 文件名格式：exec_{yyyyMMdd}.log → 提取 yyyyMMdd
     * </p>
     */
    private String extractDateFromFileName(String fileName) {
        // exec_20260506.log → 20260506
        String name = fileName.replace(".log", "");
        int underscoreIdx = name.lastIndexOf('_');
        if (underscoreIdx >= 0 && underscoreIdx < name.length() - 1) {
            return name.substring(underscoreIdx + 1);
        }
        return null;
    }
}
