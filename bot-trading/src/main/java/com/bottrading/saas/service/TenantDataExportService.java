package com.bottrading.saas.service;

import com.bottrading.model.entity.OrderEntity;
import com.bottrading.model.entity.TradeEntity;
import com.bottrading.model.entity.TradeFillEntity;
import com.bottrading.repository.OrderRepository;
import com.bottrading.repository.TradeFillRepository;
import com.bottrading.repository.TradeRepository;
import com.bottrading.saas.security.TenantAccessGuard;
import com.bottrading.util.JsonUtils;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantDataExportService {

  private final TradeRepository tradeRepository;
  private final TradeFillRepository tradeFillRepository;
  private final OrderRepository orderRepository;
  private final TenantReportService tenantReportService;
  private final TenantAccessGuard tenantAccessGuard;

  public TenantDataExportService(
      TradeRepository tradeRepository,
      TradeFillRepository tradeFillRepository,
      OrderRepository orderRepository,
      TenantReportService tenantReportService,
      TenantAccessGuard tenantAccessGuard) {
    this.tradeRepository = tradeRepository;
    this.tradeFillRepository = tradeFillRepository;
    this.orderRepository = orderRepository;
    this.tenantReportService = tenantReportService;
    this.tenantAccessGuard = tenantAccessGuard;
  }

  @Transactional(readOnly = true)
  public byte[] buildZip(UUID tenantId) {
    try (var outputStream = new java.io.ByteArrayOutputStream();
        var zip = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
      writeTrades(zip, tenantId, null, null);
      writeFills(zip, tenantId, null, null);
      writeExecutions(zip, tenantId, null, null);
      writeReports(zip, tenantId);
      zip.finish();
      zip.flush();
      return outputStream.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Unable to build export", e);
    }
  }

  @Transactional(readOnly = true)
  public void writeTradesCsv(Writer writer, Instant from, Instant to) {
    doWriteTradesCsv(writer, tenantAccessGuard.requireCurrentTenant(), from, to);
  }

  @Transactional(readOnly = true)
  public void writeTradesCsv(UUID tenantId, Writer writer, Instant from, Instant to) {
    doWriteTradesCsv(writer, tenantId, from, to);
  }

  private void doWriteTradesCsv(Writer writer, UUID tenantId, Instant from, Instant to) {
    try {
      writer.append("id,positionId,price,quantity,fee,side,executedAt\n");
      try (Stream<TradeEntity> stream =
          tradeRepository.streamByTenantAndRange(tenantId, from, to)) {
        stream.forEach(trade -> {
          try {
            writer
                .append(n(trade.getId()))
                .append(',')
                .append(trade.getPosition() != null ? n(trade.getPosition().getId()) : "")
                .append(',')
                .append(n(trade.getPrice()))
                .append(',')
                .append(n(trade.getQuantity()))
                .append(',')
                .append(n(trade.getFee()))
                .append(',')
                .append(trade.getSide() != null ? trade.getSide().name() : "")
                .append(',')
                .append(n(trade.getExecutedAt()))
                .append('\n');
          } catch (IOException ex) {
            throw new UncheckedIOException(ex);
          }
        });
      }
      writer.flush();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Transactional(readOnly = true)
  public void writeFillsCsv(Writer writer, Instant from, Instant to) {
    doWriteFillsCsv(writer, tenantAccessGuard.requireCurrentTenant(), from, to);
  }

  @Transactional(readOnly = true)
  public void writeFillsCsv(UUID tenantId, Writer writer, Instant from, Instant to) {
    doWriteFillsCsv(writer, tenantId, from, to);
  }

  private void doWriteFillsCsv(Writer writer, UUID tenantId, Instant from, Instant to) {
    try {
      writer.append("id,orderId,refPrice,fillPrice,slippageBps,symbol,executedAt\n");
      try (Stream<TradeFillEntity> stream =
          tradeFillRepository.streamByTenantAndRange(tenantId, from, to)) {
        stream.forEach(fill -> {
          try {
            writer
                .append(n(fill.getId()))
                .append(',')
                .append(n(fill.getOrderId()))
                .append(',')
                .append(n(fill.getRefPrice()))
                .append(',')
                .append(n(fill.getFillPrice()))
                .append(',')
                .append(n(fill.getSlippageBps()))
                .append(',')
                .append(n(fill.getSymbol()))
                .append(',')
                .append(n(fill.getExecutedAt()))
                .append('\n');
          } catch (IOException ex) {
            throw new UncheckedIOException(ex);
          }
        });
      }
      writer.flush();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Transactional(readOnly = true)
  public void writeExecutionsJson(Writer writer, Instant from, Instant to) {
    doWriteExecutionsJson(writer, tenantAccessGuard.requireCurrentTenant(), from, to);
  }

  @Transactional(readOnly = true)
  public void writeExecutionsJson(UUID tenantId, Writer writer, Instant from, Instant to) {
    doWriteExecutionsJson(writer, tenantId, from, to);
  }

  private void doWriteExecutionsJson(Writer writer, UUID tenantId, Instant from, Instant to) {
    try {
      writer.append("[\n");
      AtomicBoolean first = new AtomicBoolean(true);
      try (Stream<OrderEntity> stream =
          orderRepository.streamByTenantAndRange(tenantId, from, to)) {
        stream.forEach(order -> {
          try {
            if (!first.getAndSet(false)) {
              writer.append(",\n");
            }
            writer.append("  ").append(JsonUtils.orderToJson(order));
          } catch (IOException ex) {
            throw new UncheckedIOException(ex);
          }
        });
      }
      writer.append("\n]\n");
      writer.flush();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void writeTrades(ZipOutputStream zip, UUID tenantId, Instant from, Instant to)
      throws IOException {
    zip.putNextEntry(new ZipEntry("trades.csv"));
    var writer = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
    writeTradesCsv(tenantId, writer, from, to);
    writer.flush();
    zip.closeEntry();
  }

  private void writeFills(ZipOutputStream zip, UUID tenantId, Instant from, Instant to)
      throws IOException {
    zip.putNextEntry(new ZipEntry("fills.csv"));
    var writer = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
    writeFillsCsv(tenantId, writer, from, to);
    writer.flush();
    zip.closeEntry();
  }

  private void writeExecutions(ZipOutputStream zip, UUID tenantId, Instant from, Instant to)
      throws IOException {
    zip.putNextEntry(new ZipEntry("executions.json"));
    var writer = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
    writeExecutionsJson(tenantId, writer, from, to);
    writer.flush();
    zip.closeEntry();
  }

  private void writeReports(ZipOutputStream zip, UUID tenantId) throws IOException {
    var reportDir = tenantReportService.ensureLatestReportDirectory(tenantId);
    zip.putNextEntry(new ZipEntry("reports/index.html"));
    zip.write(("<html><body>Exported reports for tenant " + tenantId + "</body></html>")
        .getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
    if (reportDir != null) {
      java.nio.file.Files.walk(reportDir)
          .filter(java.nio.file.Files::isRegularFile)
          .forEach(
              path -> {
                try {
                  String relative = reportDir.relativize(path).toString();
                  zip.putNextEntry(new ZipEntry("reports/" + relative));
                  zip.write(java.nio.file.Files.readAllBytes(path));
                  zip.closeEntry();
                } catch (IOException e) {
                  throw new IllegalStateException("Unable to add report", e);
                }
              });
    }
  }

  private String n(Object value) {
    return value == null ? "" : value.toString();
  }
}
