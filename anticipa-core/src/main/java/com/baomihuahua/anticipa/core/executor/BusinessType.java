package com.baomihuahua.anticipa.core.executor;

/**
 * 线程池业务场景类型。
 * <p>
 * 支持预设枚举（CPU_INTENSIVE / IO_INTENSIVE 等）和用户自定义描述两种模式。
 * 当 type=CUSTOM 时，customDescription 存储用户输入的具体业务描述。
 * </p>
 */
public class BusinessType {

    /** 预设的通用类型 */
    public enum Type {
        CPU_INTENSIVE,
        IO_INTENSIVE,
        MIXED,
        SCHEDULED_TASK,
        HIGH_CONCURRENCY_SHORT_TASK,
        CUSTOM
    }

    private Type type;
    private String customDescription;

    public BusinessType() {
    }

    public BusinessType(Type type) {
        this.type = type;
    }

    public BusinessType(String customDescription) {
        this.type = Type.CUSTOM;
        this.customDescription = customDescription;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getCustomDescription() {
        return customDescription;
    }

    public void setCustomDescription(String customDescription) {
        this.customDescription = customDescription;
    }

    /**
     * 供 AI 理解业务场景的完整文本。
     * 自定义描述的优先级高于预设枚举。
     */
    public String toDisplayString() {
        if (type == Type.CUSTOM && customDescription != null && !customDescription.isEmpty()) {
            return customDescription;
        }
        if (type == null) {
            return "";
        }
        return switch (type) {
            case CPU_INTENSIVE -> "CPU 密集型";
            case IO_INTENSIVE -> "IO 密集型";
            case MIXED -> "混合型";
            case SCHEDULED_TASK -> "定时任务";
            case HIGH_CONCURRENCY_SHORT_TASK -> "高并发短任务";
            case CUSTOM -> customDescription != null ? customDescription : "自定义";
        };
    }

    @Override
    public String toString() {
        return toDisplayString();
    }
}
