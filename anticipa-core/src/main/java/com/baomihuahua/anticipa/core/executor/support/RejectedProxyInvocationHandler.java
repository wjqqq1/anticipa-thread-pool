package com.baomihuahua.anticipa.core.executor.support;

import lombok.AllArgsConstructor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 拒绝策略代理处理器
 *
 * <p>
 * 用于通过 JDK 动态代理包装 {@link RejectedExecutionHandler}，统计线程池被拒绝的次数
 * 当调用的是 {@code rejectedExecution} 方法时进行计数
 * </p>
 *
 * <p>
 * 示例用途：用于线程池拒绝报警、拒绝率分析等运行时动态监控
 * </p>
 */
@AllArgsConstructor
public class RejectedProxyInvocationHandler implements InvocationHandler {

    private final Object target;
    private final AtomicLong rejectCount;

    private static final String REJECT_METHOD = "rejectedExecution";

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (REJECT_METHOD.equals(method.getName()) &&
                args != null &&
                args.length == 2 &&
                args[0] instanceof Runnable &&
                args[1] instanceof ThreadPoolExecutor) {
            rejectCount.incrementAndGet();
        }

        if (method.getName().equals("toString") && method.getParameterCount() == 0) {
            return target.getClass().getSimpleName();
        }

        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }
}
