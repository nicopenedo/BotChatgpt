package com.bottrading.service;

import com.bottrading.config.TradingProps;
import com.bottrading.model.dto.AccountBalancesResponse;
import com.bottrading.model.dto.ExchangeInfo;
import com.bottrading.model.dto.OrderRequest;
import com.bottrading.model.dto.OrderResponse;
import com.bottrading.model.dto.PriceTicker;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.OrderType;
import com.bottrading.service.binance.BinanceClient;
import com.bottrading.service.risk.RiskGuard;
import com.bottrading.strategy.SignalResult;
import com.bottrading.strategy.SignalSide;
import com.bottrading.util.OrderValidator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.math.BigDecimal;
import java.math.RoundingMode;
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

  public OrderExecutionService(
      TradingProps tradingProps,
      BinanceClient binanceClient,
      com.bottrading.service.trading.OrderService orderService,
      RiskGuard riskGuard,
      MeterRegistry meterRegistry) {
    this.tradingProps = tradingProps;
    this.binanceClient = binanceClient;
    this.orderService = orderService;
    this.riskGuard = riskGuard;
    this.meterRegistry = meterRegistry;
  }

  public Optional<OrderResponse> execute(String decisionKey, SignalResult decision, long closeTime) {
    if (decision.side() == SignalSide.FLAT) {
      log.debug("Decision {} is flat, no order to execute", decisionKey);
      return Optional.empty();
    }

    String symbol = tradingProps.getSymbol();
    ExchangeInfo exchangeInfo = binanceClient.getExchangeInfo(symbol);
    PriceTicker priceTicker = binanceClient.getPrice(symbol);
    BigDecimal lastPrice = priceTicker.price();

    Assets assets = resolveAssets(symbol);
    AccountBalancesResponse balances =
        orderService.getBalances(List.of(assets.base(), assets.quote()));
    BigDecimal baseBalance = balanceOf(balances, assets.base());
    BigDecimal quoteBalance = balanceOf(balances, assets.quote());

    BigDecimal equity = quoteBalance.add(baseBalance.multiply(lastPrice));
    riskGuard.onEquityUpdate(equity);

    BigDecimal riskFraction =
        tradingProps
            .getRiskPerTradePct()
            .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
    if (riskFraction.compareTo(BigDecimal.ZERO) <= 0) {
      log.warn("Risk per trade is non-positive; skipping execution for {}", decisionKey);
      return Optional.empty();
    }
    BigDecimal allocation = equity.multiply(riskFraction).setScale(8, RoundingMode.DOWN);
    OrderSide side = decision.side() == SignalSide.BUY ? OrderSide.BUY : OrderSide.SELL;
    OrderRequest request = new OrderRequest();
    request.setSymbol(symbol);
    request.setType(OrderType.MARKET);
    request.setSide(side);
    request.setDryRun(tradingProps.isDryRun());
    request.setClientOrderId(newClientOrderId(symbol, tradingProps.getInterval(), closeTime));

    if (side == OrderSide.BUY) {
      BigDecimal allocationQuote = allocation.max(exchangeInfo.minNotional());
      BigDecimal usableQuote = allocationQuote.min(quoteBalance);
      if (usableQuote.compareTo(exchangeInfo.minNotional()) < 0) {
        log.info(
            "Quote allocation {} below min notional {} or insufficient balance ({}), skipping",
            usableQuote,
            exchangeInfo.minNotional(),
            quoteBalance);
        return Optional.empty();
      }
      request.setQuoteAmount(usableQuote);
      meterRegistry.counter("orders.queued", BUY_TAG).increment();
    } else {
      BigDecimal desiredQty =
          allocation.max(exchangeInfo.minNotional()).divide(lastPrice, 8, RoundingMode.DOWN);
      BigDecimal quantity = desiredQty.min(baseBalance);
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
      OrderResponse response = orderService.placeOrder(request);
      log.info(
          "Order {} executed: side={} qty={} quote={} status={} dryRun={}",
          decisionKey,
          request.getSide(),
          request.getQuantity(),
          request.getQuoteAmount(),
          response.status(),
          request.isDryRun());
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
