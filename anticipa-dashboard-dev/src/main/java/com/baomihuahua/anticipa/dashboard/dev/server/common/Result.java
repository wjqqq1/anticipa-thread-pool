package com.baomihuahua.anticipa.dashboard.dev.server.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    public static final Integer SUCCESS_CODE = 0;

    private Integer code;

    private String message;

    private T data;

    public static <T> Result<T> success() {
        return Result.<T>builder().code(SUCCESS_CODE).message("success").build();
    }

    public static <T> Result<T> success(T data) {
        return Result.<T>builder().code(SUCCESS_CODE).message("success").data(data).build();
    }

    public static <T> Result<T> failure(Integer code, String message) {
        return Result.<T>builder().code(code).message(message).build();
    }
}
