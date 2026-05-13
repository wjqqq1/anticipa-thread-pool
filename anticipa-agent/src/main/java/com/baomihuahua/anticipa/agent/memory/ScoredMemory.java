package com.baomihuahua.anticipa.agent.memory;

/**
 * 带得分的记忆搜索结果。
 */
public class ScoredMemory {
    private MemoryEntry entry;
    private double score;

    public ScoredMemory() {}

    public ScoredMemory(MemoryEntry entry, double score) {
        this.entry = entry;
        this.score = score;
    }

    public MemoryEntry getEntry() { return entry; }
    public void setEntry(MemoryEntry entry) { this.entry = entry; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
}
