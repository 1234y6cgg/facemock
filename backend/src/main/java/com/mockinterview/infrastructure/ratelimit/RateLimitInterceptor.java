package com.mockinterview.infrastructure.ratelimit;

import com.mockinterview.capability.ratelimit.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final int START_LIMIT = 6;
    private static final int ANSWER_LIMIT = 15;
    private static final int UPLOAD_LIMIT = 5;
    private static final int WINDOW_SEC = 60;

    private final RateLimiterService limiter;

    public RateLimitInterceptor(RateLimiterService limiter) {
        this.limiter = limiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String method = request.getMethod();
        String path = request.getRequestURI();

        String bucket = null;
        int limit = 0;
        if ("POST".equals(method) && path.matches("/api/interviews/\\d+/answer")) {
            bucket = "answer"; limit = ANSWER_LIMIT;
        } else if ("POST".equals(method) && path.matches("/api/interviews/\\d+/start")) {
            bucket = "start"; limit = START_LIMIT;
        } else if ("POST".equals(method) && "/api/resumes".equals(path)) {
            bucket = "upload"; limit = UPLOAD_LIMIT;
        } else {
            return true;
        }

        String ip = request.getRemoteAddr();
        if (!limiter.allow(bucket, ip, limit, WINDOW_SEC)) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
            return false;
        }
        return true;
    }
}
