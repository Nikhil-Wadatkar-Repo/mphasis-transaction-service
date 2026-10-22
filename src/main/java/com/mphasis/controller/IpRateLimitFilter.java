package com.mphasis.controller;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class IpRateLimitFilter extends OncePerRequestFilter {
	private static final int MAX_TRACKED_CLIENTS = 10_000;
	private static final long WINDOW_SECONDS = 60;

	private final int requestsPerMinute;
	private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

	public IpRateLimitFilter(@Value("${app.rate-limit.requests-per-minute:120}") int requestsPerMinute) {
		if (requestsPerMinute < 1) {
			throw new IllegalArgumentException("RATE_LIMIT_REQUESTS_PER_MINUTE must be positive");
		}
		this.requestsPerMinute = requestsPerMinute;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		return "/api/health".equals(path) || "/actuator/health".equals(path);
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		long now = Instant.now().getEpochSecond();
		String client = request.getRemoteAddr();
		if (windows.size() >= MAX_TRACKED_CLIENTS && !windows.containsKey(client)) {
			windows.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
			if (windows.size() >= MAX_TRACKED_CLIENTS) {
				reject(response, 60);
				return;
			}
		}

		Window window = windows.compute(client, (key, current) -> {
			if (current == null || current.expiresAt <= now) {
				return new Window(now + WINDOW_SECONDS, 1);
			}
			return new Window(current.expiresAt, current.count + 1);
		});
		if (window.count > requestsPerMinute) {
			reject(response, Math.max(1, window.expiresAt - now));
			return;
		}
		filterChain.doFilter(request, response);
	}

	private void reject(HttpServletResponse response, long retryAfterSeconds) throws IOException {
		response.setStatus(429);
		response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
		response.setContentType("application/json");
		response.getWriter().write("{\"error\":\"Rate limit exceeded\"}");
	}

	private record Window(long expiresAt, int count) {}
}
