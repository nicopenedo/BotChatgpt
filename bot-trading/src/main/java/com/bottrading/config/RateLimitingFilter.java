package com.bottrading.config;

// FIX: Bucket4j 8.x import updates and greedy refill configuration.

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
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

  private final TradingProps properties;
  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
  private final Bandwidth limit;

  public RateLimitingFilter(TradingProps properties) {
    this.properties = properties;
    int capacity = Math.max(1, properties.getMaxOrdersPerMinute());
    this.limit = Bandwidth.classic(capacity, Refill.greedy(capacity, Duration.ofMinutes(1)));
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
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    long remaining = Math.max(0, probe.getRemainingTokens());
    response.setHeader("X-Rate-Limit-Remaining", Long.toString(remaining));
    if (probe.isConsumed()) {
      filterChain.doFilter(request, response);
      return;
    }
    long waitForRefill = probe.getNanosToWaitForRefill();
    response.setHeader("Retry-After", String.valueOf(Duration.ofNanos(waitForRefill).toSeconds()));
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
