package com.bottrading.saas.service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class HeaderGeoLocationService implements GeoLocationService {

  @Override
  public Optional<String> resolveCountry(HttpServletRequest request) {
    String header = request.getHeader("X-Geo-Country");
    if (header == null || header.isBlank()) {
      header = request.getHeader("CF-IPCountry");
    }
    if (header == null || header.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(header.trim().toUpperCase());
  }
}
