package com.baomihuahua.anticipa.dashboard.dev.server.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageReqDTO {

    @Builder.Default
    private Integer page = 1;

    @Builder.Default
    private Integer size = 10;

    public int getPage() {
        return page == null || page < 1 ? 1 : page;
    }

    public int getSize() {
        return size == null || size < 1 ? 10 : size;
    }

    public int getOffset() {
        return (getPage() - 1) * getSize();
    }
}
