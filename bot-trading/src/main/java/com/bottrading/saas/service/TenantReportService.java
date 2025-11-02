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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Service
public class TenantReportService {

  private static final Logger log = LoggerFactory.getLogger(TenantReportService.class);
  private final TenantRepository tenantRepository;
  private final AuditService auditService;
  private final ResourceLoader resourceLoader;
  private final SpringTemplateEngine templateEngine;

  public TenantReportService(TenantRepository tenantRepository, AuditService auditService) {
    this(tenantRepository, auditService, null, null);
  }

  @Autowired
  public TenantReportService(
      TenantRepository tenantRepository,
      AuditService auditService,
      ResourceLoader resourceLoader,
      SpringTemplateEngine templateEngine) {
    this.tenantRepository = tenantRepository;
    this.auditService = auditService;
    this.resourceLoader = resourceLoader;
    this.templateEngine = templateEngine;
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
      String html = renderTemplate(tenant, month);
      Path htmlPath = baseDir.resolve("summary.html");
      Files.writeString(htmlPath, html, StandardCharsets.UTF_8);
      Path pdf = baseDir.resolve("summary.pdf");
      try (OutputStream out = Files.newOutputStream(pdf)) {
        PdfRendererBuilder builder = new PdfRendererBuilder();
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

  public Path ensureLatestReportDirectory(UUID tenantId) {
    YearMonth current = YearMonth.now();
    Path reportDir = generateMonthlyReport(tenantId, current);
    return reportDir.getParent();
  }

  private String renderTemplate(TenantEntity tenant, YearMonth month) throws IOException {
    Resource template =
        resourceLoader != null
            ? resourceLoader.getResource("classpath:templates/report/report_monthly_template.html")
            : null;
    if (template != null && template.exists() && templateEngine != null) {
      Context context = new Context();
      context.setVariable("tenant", tenant);
      context.setVariable("period", month);
      context.setVariable("generatedAt", java.time.Instant.now());
      return templateEngine.process("report/report_monthly_template", context);
    }
    return "<html><body>"
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
  }
}
