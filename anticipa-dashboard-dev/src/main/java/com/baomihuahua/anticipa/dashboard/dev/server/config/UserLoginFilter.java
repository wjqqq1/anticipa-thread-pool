package com.baomihuahua.anticipa.dashboard.dev.server.config;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
@RequiredArgsConstructor
public class UserLoginFilter implements Filter {

    private static final String AI_API_PREFIX = "/api/ai";

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String requestUri = request.getRequestURI();

        // 静态资源和 AI 接口开放访问
        if (requestUri.startsWith("/static/")
                || requestUri.startsWith("/ai/")
                || requestUri.startsWith(AI_API_PREFIX)
                || requestUri.equals("/") && request.getMethod().equals("GET")) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        // Sa-Token 登录校验
        if (StpUtil.isLogin()) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        // 未登录，返回 401
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write("{\"code\":401,\"message\":\"未登录\"}");
    }
}
