package com.baomihuahua.anticipa.dashboard.dev.starter.core;

/**
 * 返回结果构造工具类
 * 提供统一的成功/失败返回构造方法，简化 Result 对象的创建
 */
public final class Results {

    /**
     * 创建一个无数据的成功响应
     *
     * @return 包含成功状态码的 Result 对象，data 为 null
     */
    public static Result<Void> success() {
        return new Result<Void>()
                .setCode(Result.SUCCESS_CODE);
    }

    /**
     * 创建一个包含数据的成功响应
     *
     * @param data 返回数据
     * @param <T>  数据类型
     * @return 包含成功状态码和数据的 Result 对象
     */
    public static <T> Result<T> success(T data) {
        return new Result<T>()
                .setCode(Result.SUCCESS_CODE)
                .setData(data);
    }

    /**
     * 创建一个默认错误码（-1）和 Internal error 消息的失败响应
     *
     * @return 包含默认错误码和错误信息的 Result 对象
     */
    public static Result<Void> failure() {
        return failure("Internal error");
    }

    /**
     * 创建一个默认错误码（-1）的失败响应
     *
     * @param message 错误提示信息
     * @return 包含默认错误码和错误信息的 Result 对象
     */
    public static Result<Void> failure(String message) {
        // 默认 -1 为异常码
        return new Result<Void>().setCode("-1")
                .setMessage(message);
    }

    /**
     * 创建一个自定义错误码和信息的失败响应
     *
     * @param code    自定义错误码
     * @param message 错误提示信息
     * @return 包含指定错误码和信息的 Result 对象
     */
    public static Result<Void> failure(String code, String message) {
        return new Result<Void>().setCode(code)
                .setMessage(message);
    }
}
