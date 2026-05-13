package com.baomihuahua.anticipa.dashboard.dev.server.controller;

import com.baomihuahua.anticipa.dashboard.dev.server.common.PageDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.common.PageReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.common.Result;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.WebThreadPoolDetailRespDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.WebThreadPoolListReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.dto.WebThreadPoolUpdateReqDTO;
import com.baomihuahua.anticipa.dashboard.dev.server.service.WebThreadPoolManagerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class WebThreadPoolManagerController {

    private final WebThreadPoolManagerService webThreadPoolManagerService;

    /**
     * 查询线程池集合
     */
    @GetMapping("/api/anticipa-dashboard/web/thread-pools")
    public Result<PageDTO<WebThreadPoolDetailRespDTO>> listThreadPoolPage(@Valid WebThreadPoolListReqDTO requestParam, PageReqDTO pageReq) {
        return Result.success(webThreadPoolManagerService.listThreadPoolPage(requestParam, pageReq));
    }

    /**
     * 更新线程池
     */
    @PutMapping("/api/anticipa-dashboard/web/thread-pool")
    public Result<Void> updateGlobalThreadPool(@RequestBody @Valid WebThreadPoolUpdateReqDTO requestParam) {
        webThreadPoolManagerService.updateGlobalThreadPool(requestParam);
        return Result.success();
    }
}
