package com.bottrading.saas.web.api;

import com.bottrading.saas.model.entity.TenantExportTokenEntity;
import com.bottrading.saas.service.TenantAccountService;
import com.bottrading.saas.service.TenantDataExportService;
import com.bottrading.saas.security.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TenantAccountController {

  private final TenantAccountService tenantAccountService;
  private final TenantDataExportService tenantDataExportService;

  public TenantAccountController(
      TenantAccountService tenantAccountService,
      TenantDataExportService tenantDataExportService) {
    this.tenantAccountService = tenantAccountService;
    this.tenantDataExportService = tenantDataExportService;
  }

  @GetMapping("/tenant/account/export")
  public ResponseEntity<ByteArrayResource> export(@RequestParam("token") String token) {
    Optional<TenantExportTokenEntity> entity = tenantAccountService.consumeExportToken(token);
    if (entity.isEmpty()) {
      return ResponseEntity.status(404).build();
    }
    TenantExportTokenEntity tokenEntity = entity.get();
    byte[] bytes = tenantDataExportService.buildZip(tokenEntity.getTenantId());
    ByteArrayResource resource = new ByteArrayResource(bytes);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
    headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=tenant-export.zip");
    headers.set(HttpHeaders.CACHE_CONTROL, "private, max-age=0, must-revalidate");
    return ResponseEntity.ok().headers(headers).body(resource);
  }

  @GetMapping(value = "/tenant/exports/trades.csv", produces = "text/csv")
  public ResponseEntity<String> tradesCsv(@RequestParam(value = "tenantId", required = false) UUID tenantId) {
    UUID effectiveTenant = requireTenant(tenantId);
    return ResponseEntity.ok(tenantDataExportService.tradesCsv(effectiveTenant));
  }

  @GetMapping(value = "/tenant/exports/fills.csv", produces = "text/csv")
  public ResponseEntity<String> fillsCsv(@RequestParam(value = "tenantId", required = false) UUID tenantId) {
    UUID effectiveTenant = requireTenant(tenantId);
    return ResponseEntity.ok(tenantDataExportService.fillsCsv(effectiveTenant));
  }

  @GetMapping(value = "/tenant/exports/executions.json", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> executionsJson(@RequestParam(value = "tenantId", required = false) UUID tenantId) {
    UUID effectiveTenant = requireTenant(tenantId);
    return ResponseEntity.ok(tenantDataExportService.executionsJson(effectiveTenant));
  }

  private UUID requireTenant(UUID requestedTenant) {
    UUID contextTenant = TenantContext.getTenantId();
    if (requestedTenant == null) {
      if (contextTenant == null) {
        throw new AccessDeniedException("Tenant no resuelto");
      }
      return contextTenant;
    }
    if (contextTenant != null && !contextTenant.equals(requestedTenant)) {
      throw new AccessDeniedException("Tenant inválido");
    }
    return requestedTenant;
  }
}
