package com.bottrading.service.trading;

import com.bottrading.config.TradingProperties;
import com.bottrading.model.dto.AccountBalancesResponse;
import com.bottrading.model.dto.ExchangeInfo;
import com.bottrading.model.dto.OrderRequest;
import com.bottrading.model.dto.OrderResponse;
import com.bottrading.model.dto.PriceTicker;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.OrderType;
import com.bottrading.service.StrategyService;
import com.bottrading.service.binance.BinanceClient;
import com.bottrading.strategy.SignalResult;
import com.bottrading.strategy.SignalSide;
import com.bottrading.util.OrderValidator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StrategyExecutionScheduler {

  private static final Logger log = LoggerFactory.getLogger(StrategyExecutionScheduler.class);
  private static final List<String> KNOWN_QUOTES =
      Arrays.asList("USDT", "BUSD", "USDC", "BTC", "ETH", "BNB", "EUR", "TRY");

  private final StrategyService strategyService;
  private final TradingProperties tradingProperties;
  private final BinanceClient binanceClient;
  private final OrderService orderService;

  public StrategyExecutionScheduler(
      StrategyService strategyService,
      TradingProperties tradingProperties,
      BinanceClient binanceClient,
      OrderService orderService) {
    this.strategyService = strategyService;
    this.tradingProperties = tradingProperties;
    this.binanceClient = binanceClient;
    this.orderService = orderService;
  }

  @Scheduled(fixedDelayString = "${trading.loop-delay-ms:60000}")
  public void execute() {
    String symbol = tradingProperties.getSymbol();
    SignalResult result = strategyService.decide(symbol);
    if (result == null || result.side() == SignalSide.FLAT) {
      log.debug("Composite strategy flat for {}: {}", symbol, result != null ? result.note() : "null");
      return;
    }

    OrderSide side = result.side() == SignalSide.BUY ? OrderSide.BUY : OrderSide.SELL;
    PriceTicker ticker = binanceClient.getPrice(symbol);
    BigDecimal lastPrice = ticker.price();
    ExchangeInfo exchangeInfo = binanceClient.getExchangeInfo(symbol);

    Assets assets = resolveAssets(symbol);
    AccountBalancesResponse balances =
        binanceClient.getAccountBalances(List.of(assets.base(), assets.quote()));
    BigDecimal quoteBalance = balanceOf(balances, assets.quote());
    BigDecimal baseBalance = balanceOf(balances, assets.base());
    BigDecimal riskFraction =
        tradingProperties.getRiskPerTradePct().divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);

    OrderRequest request = new OrderRequest();
    request.setSymbol(symbol);
    request.setType(OrderType.MARKET);
    request.setSide(side);
    request.setDryRun(tradingProperties.isDryRun());

    if (side == OrderSide.BUY) {
      if (quoteBalance.compareTo(exchangeInfo.minNotional()) < 0) {
        log.debug("Insufficient quote balance {} for min notional {}", quoteBalance, exchangeInfo.minNotional());
        return;
      }
      BigDecimal allocation = quoteBalance.multiply(riskFraction);
      if (allocation.compareTo(exchangeInfo.minNotional()) < 0) {
        allocation = exchangeInfo.minNotional();
      }
      allocation = allocation.min(quoteBalance);
      if (allocation.compareTo(exchangeInfo.minNotional()) < 0) {
        log.debug("Allocation {} below min notional {}", allocation, exchangeInfo.minNotional());
        return;
      }
      request.setQuoteAmount(allocation);
    } else {
      if (baseBalance.compareTo(BigDecimal.ZERO) <= 0) {
        log.debug("No {} available to sell", assets.base());
        return;
      }
      BigDecimal quantity = baseBalance.multiply(riskFraction);
      if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
        log.debug("Computed quantity <= 0 for {}", assets.base());
        return;
      }
      request.setQuantity(quantity);
    }

    try {
      OrderValidator.validate(request, exchangeInfo, lastPrice);
    } catch (IllegalArgumentException ex) {
      log.warn("Order validation failed: {}", ex.getMessage());
      return;
    }

    OrderResponse response = orderService.placeOrder(request);
    log.info(
        "{} order executed for {} (confidence={}): {} -> {}",
        side,
        symbol,
        result.confidence(),
        result.note(),
        response.status());
  }

  private BigDecimal balanceOf(AccountBalancesResponse response, String asset) {
    if (response == null || response.balances() == null) {
      return BigDecimal.ZERO;
    }
    return response.balances().stream()
        .filter(balance -> balance.asset().equalsIgnoreCase(asset))
        .findFirst()
        .map(balance -> balance.free())
        .orElse(BigDecimal.ZERO);
  }

  private Assets resolveAssets(String symbol) {
    for (String quote : KNOWN_QUOTES) {
      if (symbol.endsWith(quote)) {
        String base = symbol.substring(0, symbol.length() - quote.length());
        return new Assets(base, quote);
      }
    }
    int mid = symbol.length() / 2;
    return new Assets(symbol.substring(0, mid), symbol.substring(mid));
  }

  private record Assets(String base, String quote) {}
}
