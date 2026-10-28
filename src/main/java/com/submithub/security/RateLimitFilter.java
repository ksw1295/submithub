package com.submithub.security;

import com.submithub.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 로그인(IP당), 과제 제출(계정당), AI 판정(계정당) 요청 빈도 제한 필터.
 * 제한 초과 시 429 상태코드와 함께 안내 화면을 렌더링한다.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final AppProperties properties;

    public RateLimitFilter(RateLimiterService rateLimiterService, AppProperties properties) {
        this.rateLimiterService = rateLimiterService;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        String method = request.getMethod();

        if ("POST".equalsIgnoreCase(method)) {
            if ("/login".equals(uri)) {
                // 1) 로그인 시도: IP당 1분 limit
                String clientIp = rateLimiterService.resolveClientIp(request);
                int limit = properties.rateLimit().loginPerMinute();
                if (!rateLimiterService.tryAcquire("login:" + clientIp, limit)) {
                    send429(request, response, "너무 많은 로그인 시도가 발생했습니다. 잠시 후 다시 시도해 주세요.");
                    return;
                }
            } else if (uri.matches("^/assignments/\\d+/submit(/github)?$")) {
                // 2) 제출(파일, GitHub): 계정당 1분 limit
                String userKey = getUserKey(request);
                int limit = properties.rateLimit().submitPerMinute();
                if (!rateLimiterService.tryAcquire("submit:" + userKey, limit)) {
                    send429(request, response, "과제 제출 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
                    return;
                }
            } else if (uri.matches("^/assignments/\\d+/students/\\d+/ai-judge$") || uri.matches("^/comments/\\d+/ai-judge$")) {
                // 3) AI 판정: 계정당 1분 limit
                String userKey = getUserKey(request);
                int limit = properties.rateLimit().aiJudgePerMinute();
                if (!rateLimiterService.tryAcquire("ai-judge:" + userKey, limit)) {
                    send429(request, response, "AI 판정 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getUserKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return rateLimiterService.resolveClientIp(request);
    }

    private void send429(HttpServletRequest request, HttpServletResponse response, String message)
            throws ServletException, IOException {
        response.setStatus(429);
        request.setAttribute("status", 429);
        request.setAttribute("detail", message);
        request.getRequestDispatcher("/error/429").forward(request, response);
    }
}
