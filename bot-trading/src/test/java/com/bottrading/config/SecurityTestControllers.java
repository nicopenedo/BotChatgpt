package com.bottrading.config;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class SecurityTestControllers {

  @RestController
  @RequestMapping("/tenant/settings")
  static class TenantSettingsController {

    @PostMapping("/notifications")
    ResponseEntity<Void> notifications() {
      return ResponseEntity.ok().build();
    }

    @PostMapping("/limits")
    ResponseEntity<Void> limits() {
      return ResponseEntity.ok().build();
    }
  }

  @RestController
  @RequestMapping("/api")
  static class ApiController {

    @PostMapping("/foo")
    ResponseEntity<Void> foo(@RequestBody(required = false) byte[] body) {
      return ResponseEntity.ok().build();
    }
  }

  @Controller
  static class TenantDashboardController {

    @GetMapping("/tenant/dashboard")
    String dashboard() {
      return "tenant/dashboard";
    }
  }
}
