package com.mphasis.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

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
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
	private final byte[] fullAccessKey;
	private final byte[] readOnlyKey;

	public ApiKeyAuthenticationFilter(
			@Value("${app.security.api-key:}") String apiKey,
			@Value("${app.security.read-only-api-key:}") String readOnlyApiKey) {
		if (apiKey == null || apiKey.isBlank()) {
			throw new IllegalStateException("TRANSACTION_API_KEY must be configured");
		}
		this.fullAccessKey = apiKey.getBytes(StandardCharsets.UTF_8);
		this.readOnlyKey = readOnlyApiKey == null || readOnlyApiKey.isBlank()
				? null : readOnlyApiKey.getBytes(StandardCharsets.UTF_8);
		if (this.readOnlyKey != null && MessageDigest.isEqual(fullAccessKey, this.readOnlyKey)) {
			throw new IllegalStateException("Read-only and full-access API keys must be different");
		}
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
		String authorization = request.getHeader("Authorization");
		if (authorization == null || !authorization.startsWith("Bearer ")) {
			unauthorized(response);
			return;
		}
		byte[] provided = authorization.substring(7).getBytes(StandardCharsets.UTF_8);
		if (MessageDigest.isEqual(fullAccessKey, provided)) {
			filterChain.doFilter(request, response);
			return;
		}
		if (readOnlyKey != null && MessageDigest.isEqual(readOnlyKey, provided)) {
			if (isReadOnlyMethod(request.getMethod())) {
				filterChain.doFilter(request, response);
			} else {
				forbidden(response);
			}
			return;
		}
		unauthorized(response);
	}

	private boolean isReadOnlyMethod(String method) {
		return "GET".equals(method) || "HEAD".equals(method);
	}

	private void forbidden(HttpServletResponse response) throws IOException {
		response.setStatus(HttpServletResponse.SC_FORBIDDEN);
		response.setContentType("application/json");
		response.getWriter().write("{\"error\":\"Forbidden\"}");
	}

	private void unauthorized(HttpServletResponse response) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType("application/json");
		response.getWriter().write("{\"error\":\"Unauthorized\"}");
	}
}
