package com.baomihuahua.anticipa.dashboard.dev.starter.toolkit;

import cn.hutool.core.util.StrUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;

/**
 * 内存工具类
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MemoryUtil {

    /**
     * 内存使用对象
     */
    private static MemoryUsage HEAP_MEMORY_USAGE = ManagementFactory
            .getMemoryMXBean()
            .getHeapMemoryUsage();

    /**
     * 获取使用内存
     */
    public static long heapMemoryUsed() {
        return HEAP_MEMORY_USAGE.getUsed();
    }

    /**
     * 获取最大内存
     */
    public static long heapMemoryMax() {
        return HEAP_MEMORY_USAGE.getMax();
    }

    /**
     * 获取空闲内存
     */
    public static String getFreeMemory() {
        long used = MemoryUtil.heapMemoryUsed();
        long max = MemoryUtil.heapMemoryMax();
        return ByteConvertUtil.getPrintSize(Math.subtractExact(max, used));
    }

    /**
     * 获取内存占比
     */
    public static String getMemoryProportion() {
        long used = MemoryUtil.heapMemoryUsed();
        long max = MemoryUtil.heapMemoryMax();
        return StrUtil.format(
                "Allocation: {} / Maximum available: {}",
                ByteConvertUtil.getPrintSize(used),
                ByteConvertUtil.getPrintSize(max)
        );
    }
}
