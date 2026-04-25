package com.baomihuahua.anticipa.dashboard.dev.server.config;

import com.baomihuahua.anticipa.dashboard.dev.server.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(value = RuntimeException.class)
    public Result<String> runtimeExceptionHandler(RuntimeException ex) {
        log.error("Runtime exception: {}", ex.getMessage(), ex);
        return Result.failure(500, ex.getMessage());
    }

    @ExceptionHandler(value = Exception.class)
    public Result<String> exceptionHandler(Exception ex) {
        log.error("Exception: {}", ex.getMessage(), ex);
        return Result.failure(500, ex.getMessage());
    }
}
