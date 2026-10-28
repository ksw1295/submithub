package com.submithub.security;

import com.submithub.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 인메모리 요청 횟수 제한 (슬라이딩 1분 윈도우).
 * 외부 라이브러리 없이 동시성 컬렉션으로 안전하게 구현.
 */
@Service
public class RateLimiterService {

    private final AppProperties properties;
    private final ConcurrentHashMap<String, Deque<Long>> requestLogs = new ConcurrentHashMap<>();

    public RateLimiterService(AppProperties properties) {
        this.properties = properties;
    }

    public boolean tryAcquire(String key, int limitPerMinute) {
        if (limitPerMinute <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000L;

        Deque<Long> timestamps = requestLogs.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= limitPerMinute) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    /**
     * 프록시 뒤에서도 실제 클라이언트 IP를 추출한다.
     * X-Forwarded-For 는 신뢰하는 프록시(루프백, 사설망 또는 설정된 프록시)에서 온 경우에만 채택한다.
     */
    public String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (isTrustedProxy(remoteAddr)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                // 맨 왼쪽 값은 클라이언트가 마음대로 넣을 수 있으므로, 오른쪽(가까운 쪽)부터 신뢰하지 않는 첫 주소를 쓴다
                String[] ips = forwarded.split(",");
                for (int i = ips.length - 1; i >= 0; i--) {
                    String candidate = ips[i].trim();
                    if (!candidate.isEmpty() && !isTrustedProxy(candidate)) {
                        return candidate;
                    }
                }
                String first = ips[0].trim();
                if (!first.isEmpty()) {
                    return first;   // 전부 신뢰하는 프록시 주소(내부 접속)면 맨 앞 값
                }
            }
        }
        return remoteAddr != null ? remoteAddr : "unknown";
    }

    public boolean isTrustedProxy(String ip) {
        if (ip == null) return false;
        List<String> configured = properties.rateLimit().trustedProxies();
        if (configured != null && configured.contains(ip)) {
            return true;
        }
        return "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)
                || ip.startsWith("10.") || ip.startsWith("192.168.")
                || is172Private(ip);
    }

    private boolean is172Private(String ip) {
        if (!ip.startsWith("172.")) return false;
        String[] parts = ip.split("\\.");
        if (parts.length >= 2) {
            try {
                int b = Integer.parseInt(parts[1]);
                return b >= 16 && b <= 31;
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }

    public void reset() {
        requestLogs.clear();
    }
}
