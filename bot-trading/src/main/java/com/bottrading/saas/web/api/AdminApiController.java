package com.bottrading.saas.web.api;

import com.bottrading.saas.repository.ReferralRepository;
import com.bottrading.saas.repository.TenantBillingRepository;
import com.bottrading.saas.repository.TenantRepository;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminApiController {

  private final TenantRepository tenantRepository;
  private final TenantBillingRepository tenantBillingRepository;
  private final ReferralRepository referralRepository;

  public AdminApiController(
      TenantRepository tenantRepository,
      TenantBillingRepository tenantBillingRepository,
      ReferralRepository referralRepository) {
    this.tenantRepository = tenantRepository;
    this.tenantBillingRepository = tenantBillingRepository;
    this.referralRepository = referralRepository;
  }

  @GetMapping("/tenants")
  public ResponseEntity<?> tenants() {
    return ResponseEntity.ok(tenantRepository.findAll());
  }

  @GetMapping("/billing")
  public ResponseEntity<?> billing() {
    return ResponseEntity.ok(tenantBillingRepository.findAll());
  }

  @GetMapping("/referrals")
  public ResponseEntity<?> referrals() {
    return ResponseEntity.ok(referralRepository.findAll());
  }

  @PostMapping("/incidents")
  public ResponseEntity<Map<String, Object>> logIncident(@RequestBody Map<String, Object> payload) {
    return ResponseEntity.ok(Map.of("status", "recorded", "payload", payload));
  }
}
