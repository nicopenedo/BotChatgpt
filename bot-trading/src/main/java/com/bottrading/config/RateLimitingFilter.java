package com.bottrading.config;

import com.bottrading.saas.security.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

  private final TradingProps properties;
  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
  private final Bandwidth limit;
  private final Duration refillPeriod;

  public RateLimitingFilter(TradingProps properties) {
    this.properties = properties;
    int rps = Math.max(1, properties.getRateLimitRps());
    this.refillPeriod = Duration.ofSeconds(1);
    this.limit = Bandwidth.classic(rps, Refill.greedy(rps, refillPeriod));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!properties.isLiveEnabled()) {
      filterChain.doFilter(request, response);
      return;
    }

    Bucket bucket = resolveBucket(resolveKey(request));
    boolean allowed = bucket.tryConsume(1);
    long remaining = Math.max(0, bucket.getAvailableTokens());
    response.setHeader("X-Rate-Limit-Remaining", Long.toString(remaining));
    if (allowed) {
      filterChain.doFilter(request, response);
      return;
    }
    long retryAfterSeconds = Math.max(1, refillPeriod.getSeconds());
    response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
    response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Too Many Requests");
  }

  private Bucket resolveBucket(String key) {
    return buckets.computeIfAbsent(key, k -> Bucket4j.builder().addLimit(limit).build());
  }

  private String resolveKey(HttpServletRequest request) {
    UUID tenantId = TenantContext.getTenantId();
    String ip = forwardedFor(request);
    if (tenantId != null) {
      return tenantId + ":" + ip;
    }
    return "anonymous:" + ip;
  }

  private String forwardedFor(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      int comma = forwarded.indexOf(',');
      return comma >= 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
    }
    String remoteAddr = request.getRemoteAddr();
    return remoteAddr != null ? remoteAddr : "unknown";
  }
}
