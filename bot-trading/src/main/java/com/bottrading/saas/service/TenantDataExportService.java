package com.bottrading.saas.service;

import com.bottrading.model.entity.OrderEntity;
import com.bottrading.model.entity.PositionEntity;
import com.bottrading.model.entity.TradeEntity;
import com.bottrading.model.entity.TradeFillEntity;
import com.bottrading.repository.OrderRepository;
import com.bottrading.repository.TradeFillRepository;
import com.bottrading.repository.TradeRepository;
import com.bottrading.saas.security.TenantContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;

@Service
public class TenantDataExportService {

  private final TradeRepository tradeRepository;
  private final TradeFillRepository tradeFillRepository;
  private final OrderRepository orderRepository;
  private final TenantReportService tenantReportService;

  public TenantDataExportService(
      TradeRepository tradeRepository,
      TradeFillRepository tradeFillRepository,
      OrderRepository orderRepository,
      TenantReportService tenantReportService) {
    this.tradeRepository = tradeRepository;
    this.tradeFillRepository = tradeFillRepository;
    this.orderRepository = orderRepository;
    this.tenantReportService = tenantReportService;
  }

  public byte[] buildZip(UUID tenantId) {
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
      writeTrades(zip, tenantId);
      writeFills(zip, tenantId);
      writeExecutions(zip, tenantId);
      writeReports(zip, tenantId);
      zip.finish();
      zip.flush();
      return outputStream.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Unable to build export", e);
    }
  }

  public String tradesCsv(UUID tenantId) {
    StringBuilder builder = new StringBuilder();
    builder.append("id,positionId,price,quantity,fee,side,executedAt\n");
    List<TradeEntity> trades = tradeRepository.findAll();
    for (TradeEntity trade : trades) {
      PositionEntity position = trade.getPosition();
      if (position != null && !matchesTenant(position.getPresetId(), tenantId)) {
        continue;
      }
      builder.append(trade.getId())
          .append(',')
          .append(position != null ? position.getId() : "")
          .append(',')
          .append(n(trade.getPrice()))
          .append(',')
          .append(n(trade.getQuantity()))
          .append(',')
          .append(n(trade.getFee()))
          .append(',')
          .append(trade.getSide())
          .append(',')
          .append(trade.getExecutedAt())
          .append('\n');
    }
    return builder.toString();
  }

  public String fillsCsv(UUID tenantId) {
    StringBuilder builder = new StringBuilder();
    builder.append("id,orderId,price,qty,commission,commissionAsset,tradeId,createdAt\n");
    List<TradeFillEntity> fills = tradeFillRepository.findAll();
    for (TradeFillEntity fill : fills) {
      if (fill.getTrade() != null && fill.getTrade().getPosition() != null) {
        if (!matchesTenant(fill.getTrade().getPosition().getPresetId(), tenantId)) {
          continue;
        }
      }
      builder.append(fill.getId())
          .append(',')
          .append(fill.getOrderId())
          .append(',')
          .append(n(fill.getPrice()))
          .append(',')
          .append(n(fill.getQty()))
          .append(',')
          .append(n(fill.getCommission()))
          .append(',')
          .append(fill.getCommissionAsset())
          .append(',')
          .append(fill.getTrade() != null ? fill.getTrade().getId() : "")
          .append(',')
          .append(fill.getCreatedAt())
          .append('\n');
    }
    return builder.toString();
  }

  public String executionsJson(UUID tenantId) {
    StringBuilder builder = new StringBuilder();
    builder.append("[\n");
    List<OrderEntity> orders = orderRepository.findAll();
    boolean first = true;
    for (OrderEntity order : orders) {
      if (order.getPosition() != null && !matchesTenant(order.getPosition().getPresetId(), tenantId)) {
        continue;
      }
      if (!first) {
        builder.append(",\n");
      }
      first = false;
      builder
          .append("  {")
          .append("\"orderId\":\"").append(order.getOrderId()).append("\",")
          .append("\"symbol\":\"").append(order.getSymbol()).append("\",")
          .append("\"side\":\"").append(order.getSide()).append("\",")
          .append("\"price\":\"").append(n(order.getPrice())).append("\",")
          .append("\"quantity\":\"").append(n(order.getQuantity())).append("\",")
          .append("\"status\":\"").append(order.getStatus()).append("\",")
          .append("\"createdAt\":\"")
          .append(order.getCreatedAt() != null ? order.getCreatedAt() : "")
          .append("\"}");
    }
    builder.append("\n]\n");
    return builder.toString();
  }

  private void writeTrades(ZipOutputStream zip, UUID tenantId) throws IOException {
    zip.putNextEntry(new ZipEntry("trades.csv"));
    zip.write(tradesCsv(tenantId).getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }

  private void writeFills(ZipOutputStream zip, UUID tenantId) throws IOException {
    zip.putNextEntry(new ZipEntry("fills.csv"));
    zip.write(fillsCsv(tenantId).getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }

  private void writeExecutions(ZipOutputStream zip, UUID tenantId) throws IOException {
    zip.putNextEntry(new ZipEntry("executions.json"));
    zip.write(executionsJson(tenantId).getBytes(StandardCharsets.UTF_8));
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

  private boolean matchesTenant(UUID presetId, UUID tenantId) {
    if (presetId == null || tenantId == null) {
      return true;
    }
    UUID currentTenant = TenantContext.getTenantId();
    return currentTenant == null || currentTenant.equals(tenantId);
  }
}
