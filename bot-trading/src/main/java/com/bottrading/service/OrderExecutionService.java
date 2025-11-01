package com.bottrading.service;

import com.bottrading.config.TradingProps;
import com.bottrading.model.dto.AccountBalancesResponse;
import com.bottrading.execution.OrderSizingService;
import com.bottrading.execution.OrderSizingService.OrderSizingResult;
import com.bottrading.execution.PositionManager;
import com.bottrading.execution.StopEngine;
import com.bottrading.execution.StopEngine.StopPlan;
import com.bottrading.model.dto.ExchangeInfo;
import com.bottrading.model.dto.OrderRequest;
import com.bottrading.model.dto.OrderResponse;
import com.bottrading.model.dto.PriceTicker;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.OrderType;
import com.bottrading.service.binance.BinanceClient;
import com.bottrading.service.risk.RiskGuard;
import com.bottrading.service.risk.TradeEvent;
import com.bottrading.strategy.SignalResult;
import com.bottrading.strategy.SignalSide;
import com.bottrading.strategy.StrategyDecision;
import com.bottrading.strategy.StrategyContext;
import com.bottrading.service.tca.TcaService;
import com.bottrading.util.OrderValidator;
import com.bottrading.shadow.ShadowEngine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrderExecutionService {

  private static final Logger log = LoggerFactory.getLogger(OrderExecutionService.class);
  private static final Tags BUY_TAG = Tags.of("side", "BUY");
  private static final Tags SELL_TAG = Tags.of("side", "SELL");

  private final TradingProps tradingProps;
  private final BinanceClient binanceClient;
  private final com.bottrading.service.trading.OrderService orderService;
  private final RiskGuard riskGuard;
  private final MeterRegistry meterRegistry;
  private final OrderSizingService orderSizingService;
  private final StopEngine stopEngine;
  private final PositionManager positionManager;
  private final ShadowEngine shadowEngine;
  private final TcaService tcaService;

  public OrderExecutionService(
      TradingProps tradingProps,
      BinanceClient binanceClient,
      com.bottrading.service.trading.OrderService orderService,
      RiskGuard riskGuard,
      MeterRegistry meterRegistry,
      OrderSizingService orderSizingService,
      StopEngine stopEngine,
      PositionManager positionManager,
      ShadowEngine shadowEngine,
      TcaService tcaService) {
    this.tradingProps = tradingProps;
    this.binanceClient = binanceClient;
    this.orderService = orderService;
    this.riskGuard = riskGuard;
    this.meterRegistry = meterRegistry;
    this.orderSizingService = orderSizingService;
    this.stopEngine = stopEngine;
    this.positionManager = positionManager;
    this.shadowEngine = shadowEngine;
    this.tcaService = tcaService;
  }

  public Optional<OrderResponse> execute(
      String decisionKey,
      String symbol,
      String interval,
      StrategyDecision decision,
      long closeTime,
      double sizingMultiplier) {
    SignalResult signal = decision.signal();
    if (signal.side() == SignalSide.FLAT) {
      log.debug("Decision {} is flat, no order to execute", decisionKey);
      return Optional.empty();
    }

    ExchangeInfo exchangeInfo = binanceClient.getExchangeInfo(symbol);
    PriceTicker priceTicker = binanceClient.getPrice(symbol);
    StrategyContext context = decision.context();
    BigDecimal lastPrice =
        context.lastPrice() != null ? context.lastPrice() : priceTicker.price();
    BigDecimal volume24h = context.volume24h();
    Double normalizedAtr = context.normalizedAtr();
    BigDecimal atr =
        normalizedAtr == null || lastPrice == null
            ? null
            : BigDecimal.valueOf(normalizedAtr).multiply(lastPrice);

    Assets assets = resolveAssets(symbol);
    AccountBalancesResponse balances =
        orderService.getBalances(List.of(assets.base(), assets.quote()));
    BigDecimal baseBalance = balanceOf(balances, assets.base());
    BigDecimal quoteBalance = balanceOf(balances, assets.quote());

    BigDecimal equity = quoteBalance.add(baseBalance.multiply(lastPrice));
    riskGuard.onEquityUpdate(equity);

    OrderSide orderSide = signal.side() == SignalSide.BUY ? OrderSide.BUY : OrderSide.SELL;
    StopPlan stopPlan = stopEngine.plan(symbol, orderSide, lastPrice, atr);
    OrderSizingResult sizingResult;
    try {
      sizingResult =
          orderSizingService.size(
              orderSide, lastPrice, stopPlan.stopLoss(), atr, equity, exchangeInfo, sizingMultiplier);
    } catch (IllegalStateException | IllegalArgumentException ex) {
      log.warn("Sizing failed for {}: {}", decisionKey, ex.getMessage());
      return Optional.empty();
    }
    OrderRequest request = new OrderRequest();
    request.setSymbol(symbol);
    request.setSide(orderSide);
    request.setDryRun(tradingProps.isDryRun());
    request.setClientOrderId(newClientOrderId(symbol, interval, closeTime));

    OrderType orderType =
        tcaService.recommendOrderType(symbol, sizingResult.orderType(), Instant.now());
    request.setType(orderType);
    if (orderType == OrderType.LIMIT) {
      request.setPrice(lastPrice);
    }

    if (orderSide == OrderSide.BUY) {
      BigDecimal quantity = sizingResult.quantity();
      BigDecimal requiredQuote = quantity.multiply(lastPrice);
      if (requiredQuote.compareTo(quoteBalance) > 0) {
        log.info("Quote balance {} insufficient for {}", quoteBalance, decisionKey);
        return Optional.empty();
      }
      request.setQuantity(quantity);
      meterRegistry.counter("orders.queued", BUY_TAG).increment();
    } else {
      BigDecimal quantity = sizingResult.quantity().min(baseBalance);
      if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
        log.info("Base balance {} insufficient to sell for {}", baseBalance, decisionKey);
        return Optional.empty();
      }
      request.setQuantity(quantity);
      meterRegistry.counter("orders.queued", SELL_TAG).increment();
    }

    try {
      OrderValidator.validate(request, exchangeInfo, lastPrice);
    } catch (IllegalArgumentException ex) {
      log.warn("Order validation failed for {}: {}", decisionKey, ex.getMessage());
      return Optional.empty();
    }

    try {
      tcaService.recordSubmission(
          request.getClientOrderId(), symbol, orderSide, orderType, lastPrice, volume24h, atr, Instant.now());
      OrderResponse response = orderService.placeOrder(request);
      log.info(
          "Order {} executed: side={} qty={} quote={} status={} dryRun={}",
          decisionKey,
          request.getSide(),
          request.getQuantity(),
          request.getQuoteAmount(),
          response.status(),
          request.isDryRun());
      if (!request.isDryRun()) {
        BigDecimal notional =
            request.getQuoteAmount() != null && request.getQuoteAmount().compareTo(BigDecimal.ZERO) > 0
                ? request.getQuoteAmount()
                : request.getQuantity() != null && lastPrice != null
                    ? request.getQuantity().multiply(lastPrice)
                    : null;
        riskGuard.onTrade(new TradeEvent(symbol, true, BigDecimal.ZERO, null, notional));
      }
      if (response.executedQty() != null && response.executedQty().compareTo(BigDecimal.ZERO) > 0) {
        tcaService.recordFill(response.clientOrderId(), response.price(), null, response.transactTime());
      }
      if (!request.isDryRun()) {
        positionManager.openPosition(
            new PositionManager.OpenPositionCommand(
                symbol,
                orderSide,
                lastPrice,
                request.getQuantity(),
                stopPlan.stopLoss(),
                stopPlan.takeProfit(),
                null,
                response.clientOrderId()));
        shadowEngine.registerShadow(symbol, orderSide, lastPrice, request.getQuantity(), stopPlan);
      }
      return Optional.of(response);
    } catch (Exception ex) {
      log.error("Failed to execute order for {}: {}", decisionKey, ex.getMessage(), ex);
      return Optional.empty();
    }
  }

  private String newClientOrderId(String symbol, String interval, long closeTime) {
    return "candle-%s-%s-%d".formatted(symbol, interval, closeTime);
  }

  private BigDecimal balanceOf(AccountBalancesResponse response, String asset) {
    if (response == null || response.balances() == null) {
      return BigDecimal.ZERO;
    }
    return response.balances().stream()
        .filter(balance -> balance.asset().equalsIgnoreCase(asset))
        .findFirst()
        .map(AccountBalancesResponse.Balance::free)
        .orElse(BigDecimal.ZERO);
  }

  private Assets resolveAssets(String symbol) {
    String upper = symbol.toUpperCase();
    List<String> knownQuotes =
        List.of("USDT", "BUSD", "USDC", "BTC", "ETH", "BNB", "EUR", "TRY", "BIDR", "AUD");
    for (String quote : knownQuotes) {
      if (upper.endsWith(quote)) {
        String base = upper.substring(0, upper.length() - quote.length());
        return new Assets(base, quote);
      }
    }
    int mid = symbol.length() / 2;
    return new Assets(upper.substring(0, mid), upper.substring(mid));
  }

  private record Assets(String base, String quote) {}
}
