package com.baomihuahua.anticipa.dashboard.dev.server.controller;

import com.baomihuahua.anticipa.dashboard.dev.server.common.PageDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.common.PageReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.common.Result;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.ThreadPoolDetailRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.ThreadPoolListReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.ThreadPoolUpdateReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.service.ThreadPoolManagerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ThreadPoolManagerController {

    private final ThreadPoolManagerService threadPoolManagerService;

    /**
     * 查询线程池集合
     */
    @GetMapping("/api/anticipa-dashboard/thread-pools")
    public Result<PageDTO<ThreadPoolDetailRespDTO>> listThreadPoolPage(@Valid ThreadPoolListReqDTO requestParam, PageReqDTO pageReq) {
        return Result.success(threadPoolManagerService.listThreadPoolPage(requestParam, pageReq));
    }

    /**
     * 更新线程池
     */
    @PutMapping("/api/anticipa-dashboard/thread-pool")
    public Result<Void> updateGlobalThreadPool(@RequestBody @Valid ThreadPoolUpdateReqDTO requestParam) {
        threadPoolManagerService.updateGlobalThreadPool(requestParam);
        return Result.success();
    }
}
