package com.baomihuahua.anticipa.config.common.starter.refresher;

import lombok.Data;

/**
 * 动态刷新线程池配置并发安全刷新测试
 */
@Data
public class ThreadPoolExecutorProperties {

    private String threadPoolId;

    /**
     * 如果大家对手动 new String（强制创建新对象）有疑惑
     * 可以把这行代码加到动态线程池配置刷新的流程里，查看每一次相同的 threadPoolId HashCode 值是否相同
     * System.out.println(System.identityHashCode(threadPoolId));
     * <p>
     * 因为每次配置中心的字符串都是重新创建的，所以这里为了贴合实际场景，所以是直接 new String
     */
    public ThreadPoolExecutorProperties(String threadPoolId) {
        // 模拟内容相同，但引用不同
        this.threadPoolId = new String(threadPoolId);
    }
}
