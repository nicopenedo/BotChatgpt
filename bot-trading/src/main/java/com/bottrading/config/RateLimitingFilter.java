package com.bottrading.config;

import com.bottrading.saas.security.TenantContext;
import com.bucket4j.Bandwidth;
import com.bucket4j.Bucket;
import com.bucket4j.Bucket4j;
import com.bucket4j.ConsumptionProbe;
import com.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

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

    Bucket bucket = buckets.computeIfAbsent(resolveKey(request), key -> Bucket4j.builder().addLimit(limit).build());
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    long remaining = Math.max(0, probe.getRemainingTokens());
    response.setHeader("X-Rate-Limit-Remaining", Long.toString(remaining));
    response.setHeader("X-Rate-Limit-Reset", resetHeaderValue(probe));
    if (!probe.isConsumed()) {
      response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      return;
    }
    filterChain.doFilter(request, response);
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

  private String resetHeaderValue(ConsumptionProbe probe) {
    long nanosToWait = probe.getNanosToWaitForRefill();
    if (nanosToWait <= 0) {
      return "0";
    }
    Instant reset = Instant.now().plusNanos(nanosToWait);
    return Long.toString(reset.getEpochSecond());
  }
}
