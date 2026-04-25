package com.baomihuahua.anticipa.dashboard.dev.server.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Results {

    private Integer status;

    private String message;

    private Object data;

    public static Results success() {
        return Results.builder().status(200).message("success").build();
    }

    public static Results success(Object data) {
        return Results.builder().status(200).message("success").data(data).build();
    }

    public static Results failure(Integer status, String message) {
        return Results.builder().status(status).message(message).build();
    }
}
