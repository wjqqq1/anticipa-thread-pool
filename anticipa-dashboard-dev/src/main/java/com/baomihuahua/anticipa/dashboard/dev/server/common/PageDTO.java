package com.baomihuahua.anticipa.dashboard.dev.server.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageDTO<T> {

    private List<T> records;

    private long total;

    public static <T> PageDTO<T> of(List<T> records, long total) {
        return new PageDTO<>(records, total);
    }
}
