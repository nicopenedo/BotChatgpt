package com.bottrading.saas.service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

public interface GeoLocationService {
  Optional<String> resolveCountry(HttpServletRequest request);
}
