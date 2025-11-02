package com.bottrading.web.mvc.service;

import com.bottrading.web.mvc.model.ActivityItemView;
import com.bottrading.web.mvc.model.BillingView;
import com.bottrading.web.mvc.model.BotDetailView;
import com.bottrading.web.mvc.model.BotLogView;
import com.bottrading.web.mvc.model.BotRuleView;
import com.bottrading.web.mvc.model.BotSummaryView;
import com.bottrading.web.mvc.model.DashboardViewModel;
import com.bottrading.web.mvc.model.InvoiceView;
import com.bottrading.web.mvc.model.KpiCardView;
import com.bottrading.web.mvc.model.LeaderboardEntryView;
import com.bottrading.web.mvc.model.NavbarNotificationsView;
import com.bottrading.web.mvc.model.NotificationSettingsView;
import com.bottrading.web.mvc.model.PaginationView;
import com.bottrading.web.mvc.model.PresetDetailView;
import com.bottrading.web.mvc.model.PresetMetricView;
import com.bottrading.web.mvc.model.PresetRegimeSummaryView;
import com.bottrading.web.mvc.model.PresetSnapshotView;
import com.bottrading.web.mvc.model.PresetTableView;
import com.bottrading.web.mvc.model.RegimeSummaryView;
import com.bottrading.web.mvc.model.ReportView;
import com.bottrading.web.mvc.model.RiskCardView;
import com.bottrading.web.mvc.model.RiskLimitSettingsView;
import com.bottrading.web.mvc.model.SelectOptionView;
import com.bottrading.web.mvc.model.SettingsView;
import com.bottrading.web.mvc.model.TenantSwitcherView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TenantViewService {

  private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("es", "AR"));

  private final ObjectMapper objectMapper;

  public TenantViewService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public TenantSwitcherView tenantSwitcher(UUID tenantId) {
    return new TenantSwitcherView("DEMO Capital", List.of("DEMO Capital", "Quant Hedge"));
  }

  public NavbarNotificationsView notifications() {
    return new NavbarNotificationsView(true, "/img/logo.svg", "Demo Trader");
  }

  public DashboardViewModel dashboard(UUID tenantId) {
    List<KpiCardView> kpis =
        List.of(
            new KpiCardView("PnL Neto", "$12.4K", "up", "+4.5%", "vs. 30d", "PnL"),
            new KpiCardView("Profit Factor", "2.6", "up", "+0.3", "vs. 7d", "PF"),
            new KpiCardView("WinRate", "61.3%", "down", "-1.2%", "vs. 14d", "WR"),
            new KpiCardView("Drawdown", "-6.2%", "up", "+1.1%", "Recuperado", "DD"));

    List<ActivityItemView> recentActivity =
        List.of(
            new ActivityItemView("Fill", "fill", "BTC Swing", "Compra 0.5 BTC @ 63,200", "12:45", "+120 USDT"),
            new ActivityItemView("Orden", "alert", "ETH Scalper", "Stop loss actualizado", "12:10", "—"),
            new ActivityItemView("Alerta", "alert", "Kill-switch", "Se alcanzó umbral intradía", "11:54", "—"));

    List<RiskCardView> riskCards =
        List.of(
            new RiskCardView("VaR 95%", "-2.4%", "En rango"),
            new RiskCardView("CVaR", "-4.8%", "Límite ajustado"),
            new RiskCardView("Kill-switch", "Armed", "Activo en 2 exchanges"),
            new RiskCardView("Anomalías", "0", "Últimas 24h"));

    return new DashboardViewModel(
        kpis,
        chartJson(chartLine("Equity", List.of("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"), List.of(100, 105, 111, 109, 118, 122, 125))),
        chartJson(chartBar("PnL diario", List.of("W1", "W2", "W3", "W4"), List.of(2200, -850, 1900, 2450))),
        chartJson(chartHeatmap()),
        recentActivity,
        riskCards);
  }

  public List<BotSummaryView> bots(UUID tenantId) {
    return List.of(
        new BotSummaryView("bot-1", "BTC Swing", "BTCUSDT · 1h", "LIVE", "live", "2.1", "-4.5%", "$3.2K", "Hace 12m"),
        new BotSummaryView("bot-2", "ETH Scalper", "ETHUSDT · 15m", "SHADOW", "shadow", "1.7", "-3.1%", "$1.1K", "Hace 5m"),
        new BotSummaryView("bot-3", "SOL Breakout", "SOLUSDT · 30m", "PAUSED", "paused", "1.3", "-7.2%", "$650", "Hace 4h"));
  }

  public PaginationView botsPagination() {
    return new PaginationView(0, 4, false, true, "#", "#");
  }

  public List<RegimeSummaryView> regimes() {
    return List.of(
        new RegimeSummaryView("UP", "UP", "regime UP", "$4.1K", "+8.2% vs. 7d"),
        new RegimeSummaryView("DOWN", "DOWN", "regime DOWN", "$1.6K", "+3.4% vs. 7d"),
        new RegimeSummaryView("RANGE", "RANGE", "regime RANGE", "$980", "-1.2% vs. 7d"));
  }

  public BotDetailView botDetail(UUID tenantId, String botId) {
    return new BotDetailView(
        botId,
        "BTC Swing",
        "BTCUSDT · 1h",
        "SHADOW",
        "shadow",
        "UP",
        "UP",
        List.of(
            new BotRuleView("Preset", "Momentum Shadow v2"),
            new BotRuleView("Stop dinámico", "ATR x 2.4"),
            new BotRuleView("Max exposición", "15% del equity")),
        List.of(
            new BotLogView("Bandit decision", "Hace 5m", "Se promovió preset Momentum Shadow v2"),
            new BotLogView("Kill-switch", "Hace 1h", "Se testeó señal de emergencia, sin disparo")),
        chartJson(chartLine("Equity", List.of("Q1", "Q2", "Q3", "Q4"), List.of(100, 120, 150, 180))),
        chartJson(chartDonut("Símbolos", List.of("BTCUSDT", "ETHUSDT", "SOLUSDT"), List.of(55, 30, 15))),
        chartJson(chartBar("Slippage", List.of("Slippage", "Fees"), List.of(-35, -22))));
  }

  public List<PresetRegimeSummaryView> presetRegimes() {
    return List.of(
        new PresetRegimeSummaryView("Mercado UP", "Trend following", "Activo", "live", "2.1", "1.3", "-6.5%", "210"),
        new PresetRegimeSummaryView("Mercado DOWN", "Cobertura dinámica", "Shadow", "shadow", "1.8", "1.1", "-5.2%", "160"),
        new PresetRegimeSummaryView("Rango", "Mean reversion", "Canary", "paused", "1.5", "0.9", "-4.1%", "120"));
  }

  public List<PresetTableView> presetsTable() {
    return List.of(
        new PresetTableView("preset-1", "Momentum Shadow", "UP", "UP", "Activo", "1.9", "1.2", "-6.5%", "210"),
        new PresetTableView("preset-2", "Mean Reversion", "RANGE", "RANGE", "Canary", "1.4", "0.8", "-4.8%", "180"),
        new PresetTableView("preset-3", "Volatility Breakout", "DOWN", "DOWN", "Shadow", "1.7", "1.0", "-5.6%", "140"));
  }

  public List<SelectOptionView> presetRegimeOptions(String selected) {
    return List.of(
        new SelectOptionView("UP", "Mercado UP", "UP".equalsIgnoreCase(selected)),
        new SelectOptionView("DOWN", "Mercado DOWN", "DOWN".equalsIgnoreCase(selected)),
        new SelectOptionView("RANGE", "Mercado RANGE", "RANGE".equalsIgnoreCase(selected)));
  }

  public PresetDetailView presetDetail(String presetId) {
    return new PresetDetailView(
        presetId,
        "Momentum Shadow",
        "Estrategia GA optimizada para mercado alcista",
        "Activo",
        "live",
        "UP",
        "UP",
        "LONG",
        List.of(
            new PresetMetricView("PF", "1.9"),
            new PresetMetricView("Sharpe", "1.3"),
            new PresetMetricView("Trades", "210"),
            new PresetMetricView("MaxDD", "-6.5%")),
        "{\n  \"atrPeriod\": 14,\n  \"riskPerTrade\": 0.02,\n  \"killSwitch\": true\n}",
        chartJson(chartLine("Performance", List.of("UP", "DOWN", "RANGE"), List.of(2.1, 1.4, 1.1))));
  }

  public List<PresetSnapshotView> presetSnapshots() {
    return List.of(
        new PresetSnapshotView("30d", "2024-02-20", "1.9", "210", "-6.5%"),
        new PresetSnapshotView("90d", "2024-02-01", "1.8", "540", "-8.2%"));
  }

  public List<LeaderboardEntryView> leaderboardEntries() {
    return List.of(
        new LeaderboardEntryView("BTC Momentum", "Bot", "live", "2.3", "$4.5K", "62%", "-5.4%"),
        new LeaderboardEntryView("ETH Swing", "Bot", "shadow", "2.1", "$3.2K", "59%", "-4.7%"),
        new LeaderboardEntryView("Volatility Breakout", "Preset", "paused", "1.8", "$2.1K", "55%", "-6.2%"));
  }

  public List<SelectOptionView> leaderboardEntities(String current) {
    return List.of(
        new SelectOptionView("bots", "Bots", "bots".equalsIgnoreCase(current)),
        new SelectOptionView("presets", "Presets", "presets".equalsIgnoreCase(current)),
        new SelectOptionView("symbols", "Símbolos", "symbols".equalsIgnoreCase(current)));
  }

  public List<SelectOptionView> leaderboardWindows(String current) {
    return List.of(
        new SelectOptionView("7d", "7 días", "7d".equalsIgnoreCase(current)),
        new SelectOptionView("30d", "30 días", "30d".equalsIgnoreCase(current)),
        new SelectOptionView("90d", "90 días", "90d".equalsIgnoreCase(current)));
  }

  public List<ReportView> reports(UUID tenantId) {
    LocalDate now = LocalDate.now();
    return List.of(
        reportForMonth(now.minusMonths(1)),
        reportForMonth(now.minusMonths(2)),
        reportForMonth(now.minusMonths(3)));
  }

  private ReportView reportForMonth(LocalDate month) {
    String formatted = capitalize(MONTH_FORMATTER.format(month));
    return new ReportView(
        formatted,
        "$" + randomBetween(100, 140) + "K",
        "$" + randomBetween(4, 9) + "K",
        "-" + randomBetween(3, 7) + "%",
        "DEMO",
        month.toString());
  }

  public BillingView billing(UUID tenantId) {
    List<InvoiceView> invoices =
        List.of(
            new InvoiceView("2024-03-01", "Plan Starter", "$199", "Pagado", "live"),
            new InvoiceView("2024-02-01", "Bots adicionales", "$89", "Pagado", "live"),
            new InvoiceView("2024-01-01", "Plan Starter", "$199", "Pendiente", "paused"));
    return new BillingView("Starter", "Activo", "2 / 5", "18 GB", invoices);
  }

  public SettingsView settings(UUID tenantId) {
    return new SettingsView(
        "****-ABCD-1234",
        "****-WEBHOOK",
        new NotificationSettingsView(true, false),
        new RiskLimitSettingsView(2.5, 1.2, 15.0, "America/Argentina/Buenos_Aires", true),
        List.of("America/Argentina/Buenos_Aires", "America/Montevideo", "UTC"));
  }

  private String chartJson(Map<String, Object> chart) {
    try {
      return objectMapper.writeValueAsString(chart);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Error serializing chart data", e);
    }
  }

  private Map<String, Object> chartLine(String label, List<?> labels, List<? extends Number> data) {
    return Map.of(
        "type",
        "line",
        "data",
        Map.of(
            "labels",
            labels,
            "datasets",
            List.of(
                Map.of(
                    "label",
                    label,
                    "data",
                    data,
                    "borderColor",
                    "#2563eb",
                    "tension",
                    0.35,
                    "fill",
                    false))),
        "options",
        Map.of("plugins", Map.of("legend", Map.of("display", false))));
  }

  private Map<String, Object> chartBar(String label, List<?> labels, List<? extends Number> data) {
    return Map.of(
        "type",
        "bar",
        "data",
        Map.of(
            "labels",
            labels,
            "datasets",
            List.of(
                Map.of(
                    "label",
                    label,
                    "data",
                    data,
                    "backgroundColor",
                    Collections.nCopies(data.size(), "rgba(37,99,235,0.75)")))),
        "options",
        Map.of("plugins", Map.of("legend", Map.of("display", false))));
  }

  private Map<String, Object> chartDonut(String label, List<?> labels, List<? extends Number> data) {
    return Map.of(
        "type",
        "doughnut",
        "data",
        Map.of(
            "labels",
            labels,
            "datasets",
            List.of(
                Map.of(
                    "label",
                    label,
                    "data",
                    data,
                    "backgroundColor",
                    List.of("#2563eb", "#34d399", "#22d3ee"),
                    "hoverOffset",
                    8))),
        "options",
        Map.of("plugins", Map.of("legend", Map.of("position", "bottom"))));
  }

  private Map<String, Object> chartHeatmap() {
    // Simplified heatmap using stacked bars
    List<String> labels = List.of("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom");
    return Map.of(
        "type",
        "bar",
        "data",
        Map.of(
            "labels",
            labels,
            "datasets",
            List.of(
                Map.of("label", "00-08", "data", List.of(2, -1, 3, 4, 2, 1, 0), "backgroundColor", "rgba(37,99,235,0.6)"),
                Map.of("label", "08-16", "data", List.of(1, 2, -2, 3, 4, 2, 1), "backgroundColor", "rgba(16,185,129,0.6)"),
                Map.of("label", "16-24", "data", List.of(3, 1, 0, -1, 2, 3, 2), "backgroundColor", "rgba(14,165,233,0.6)"))),
        "options",
        Map.of(
            "plugins", Map.of("legend", Map.of("display", true, "position", "bottom")),
            "scales",
            Map.of("x", Map.of("stacked", true), "y", Map.of("stacked", true))));
  }

  private static String capitalize(String value) {
    if (value == null || value.isBlank()) {
      return value;
    }
    return value.substring(0, 1).toUpperCase(Locale.getDefault()) + value.substring(1);
  }

  private static String randomBetween(int min, int max) {
    BigDecimal random = BigDecimal.valueOf(Math.random());
    BigDecimal scaled = random.multiply(BigDecimal.valueOf(max - min)).add(BigDecimal.valueOf(min));
    return scaled.setScale(1, RoundingMode.HALF_UP).toPlainString();
  }
}
