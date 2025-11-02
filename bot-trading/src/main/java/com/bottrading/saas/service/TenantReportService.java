package com.bottrading.saas.service;

import com.bottrading.saas.model.entity.TenantEntity;
import com.bottrading.saas.repository.TenantRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TenantReportService {

  private static final Logger log = LoggerFactory.getLogger(TenantReportService.class);
  private final TenantRepository tenantRepository;
  private final AuditService auditService;

  public TenantReportService(TenantRepository tenantRepository, AuditService auditService) {
    this.tenantRepository = tenantRepository;
    this.auditService = auditService;
  }

  public Path generateMonthlyReport(UUID tenantId, YearMonth month) {
    TenantEntity tenant =
        tenantRepository
            .findById(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
    log.info("Generating monthly report for tenant {} period {}", tenantId, month);
    Path baseDir =
        Path.of(
            "reports",
            "tenant",
            tenantId.toString(),
            String.valueOf(month.getYear()),
            String.format("%02d", month.getMonthValue()));
    try {
      Files.createDirectories(baseDir);
      Path csv = baseDir.resolve("summary.csv");
      Files.writeString(
          csv,
          "metric,value\n" + "pnl_net,0\n" + "max_dd,0\n" + "trades,0\n",
          StandardCharsets.UTF_8);
      Path pdf = baseDir.resolve("summary.pdf");
      try (OutputStream out = Files.newOutputStream(pdf)) {
        PdfRendererBuilder builder = new PdfRendererBuilder();
        String html =
            "<html><body>"
                + "<h1>Monthly Report - "
                + tenant.getName()
                + " ("
                + month
                + ")</h1>"
                + "<p>Plan: "
                + tenant.getPlan()
                + "</p>"
                + "<p>Status: "
                + tenant.getStatus()
                + "</p>"
                + "<p>KPIs shadow/live pending integration.</p>"
                + "</body></html>";
        builder.withHtmlContent(html, "");
        builder.toStream(out);
        builder.run();
      }
      auditService.record(
          tenantId,
          null,
          "report.monthly.generated",
          Map.of("period", month.toString(), "path", baseDir.toString()));
      return baseDir;
    } catch (IOException e) {
      throw new IllegalStateException("Unable to generate report", e);
    }
  }
}
