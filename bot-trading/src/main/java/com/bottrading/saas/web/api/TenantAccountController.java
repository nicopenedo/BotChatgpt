package com.bottrading.saas.web.api;

import com.bottrading.saas.model.entity.TenantExportTokenEntity;
import com.bottrading.saas.security.TenantAccessGuard;
import com.bottrading.saas.service.TenantAccountService;
import com.bottrading.saas.service.TenantDataExportService;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
public class TenantAccountController {

  private final TenantAccountService tenantAccountService;
  private final TenantDataExportService tenantDataExportService;
  private final TenantAccessGuard tenantAccessGuard;

  public TenantAccountController(
      TenantAccountService tenantAccountService,
      TenantDataExportService tenantDataExportService,
      TenantAccessGuard tenantAccessGuard) {
    this.tenantAccountService = tenantAccountService;
    this.tenantDataExportService = tenantDataExportService;
    this.tenantAccessGuard = tenantAccessGuard;
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

  @GetMapping(value = "/tenant/{tenantId}/exports/trades.csv", produces = "text/csv")
  public ResponseEntity<StreamingResponseBody> tradesCsv(
      @PathVariable UUID tenantId,
      @RequestParam(value = "from", required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(value = "to", required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to) {
    tenantAccessGuard.assertTenant(tenantId);
    StreamingResponseBody body = outputStream -> {
      try (OutputStreamWriter writer =
          new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
        tenantDataExportService.writeTradesCsv(tenantId, writer, from, to);
      }
    };
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=trades.csv")
        .contentType(MediaType.valueOf("text/csv"))
        .body(body);
  }

  @GetMapping(value = "/tenant/{tenantId}/exports/fills.csv", produces = "text/csv")
  public ResponseEntity<StreamingResponseBody> fillsCsv(
      @PathVariable UUID tenantId,
      @RequestParam(value = "from", required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(value = "to", required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to) {
    tenantAccessGuard.assertTenant(tenantId);
    StreamingResponseBody body = outputStream -> {
      try (OutputStreamWriter writer =
          new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
        tenantDataExportService.writeFillsCsv(tenantId, writer, from, to);
      }
    };
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=fills.csv")
        .contentType(MediaType.valueOf("text/csv"))
        .body(body);
  }

  @GetMapping(
      value = "/tenant/{tenantId}/exports/executions.json",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<StreamingResponseBody> executionsJson(
      @PathVariable UUID tenantId,
      @RequestParam(value = "from", required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(value = "to", required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to) {
    tenantAccessGuard.assertTenant(tenantId);
    StreamingResponseBody body = outputStream -> {
      try (OutputStreamWriter writer =
          new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
        tenantDataExportService.writeExecutionsJson(tenantId, writer, from, to);
      }
    };
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=executions.json")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body);
  }
}
