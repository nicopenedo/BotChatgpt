package com.bottrading.web;

import com.bottrading.shadow.ShadowEngine;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shadow")
public class ShadowController {

  private final ShadowEngine shadowEngine;

  public ShadowController(ShadowEngine shadowEngine) {
    this.shadowEngine = shadowEngine;
  }

  @GetMapping("/status")
  public ShadowEngine.ShadowStatus status(@RequestParam @NotBlank String symbol) {
    return shadowEngine.status(symbol);
  }
}
