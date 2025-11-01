package com.bottrading.fees;

import com.bottrading.config.FeeProperties;
import com.bottrading.model.dto.AccountBalancesResponse;
import com.bottrading.model.dto.AccountBalancesResponse.Balance;
import com.bottrading.model.dto.OrderRequest;
import com.bottrading.model.dto.OrderResponse;
import com.bottrading.model.dto.PriceTicker;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.OrderType;
import com.bottrading.notify.TelegramNotifier;
import com.bottrading.service.binance.BinanceClient;
import com.bottrading.service.trading.OrderService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class BnbRebalancer {

  private final BinanceClient binanceClient;
  private final OrderService orderService;
  private final FeeProperties feeProperties;
  private final TelegramNotifier notifier;
  private final Clock clock;
  private final Counter topups;

  public BnbRebalancer(
      BinanceClient binanceClient,
      OrderService orderService,
      FeeProperties feeProperties,
      TelegramNotifier notifier,
      MeterRegistry meterRegistry,
      Optional<Clock> clock) {
    this.binanceClient = binanceClient;
    this.orderService = orderService;
    this.feeProperties = feeProperties;
    this.notifier = notifier;
    this.clock = clock.orElse(Clock.systemUTC());
    this.topups = meterRegistry.counter("fees.topups");
  }

  public Optional<OrderResponse> ensureBnbBuffer(String quoteAsset, BigDecimal estimatedDailyFees) {
    AccountBalancesResponse balances = binanceClient.getAccountBalances(List.of("BNB", quoteAsset));
    BigDecimal bnbBalance = balanceOf(balances, "BNB");
    if (bnbBalance == null) {
      bnbBalance = BigDecimal.ZERO;
    }
    if (estimatedDailyFees == null || estimatedDailyFees.compareTo(BigDecimal.ZERO) <= 0) {
      return Optional.empty();
    }
    PriceTicker price = binanceClient.getPrice("BNB" + quoteAsset);
    BigDecimal feePerDayBnb = estimatedDailyFees.divide(price.price(), 8, RoundingMode.HALF_UP);
    if (feePerDayBnb.compareTo(BigDecimal.ZERO) <= 0) {
      return Optional.empty();
    }
    BigDecimal daysCovered =
        bnbBalance.divide(feePerDayBnb, 2, RoundingMode.DOWN);
    if (daysCovered.compareTo(feeProperties.getBnbMinDaysBuffer()) >= 0) {
      return Optional.empty();
    }
    BigDecimal targetBnb = feeProperties.getBnbMinDaysBuffer().multiply(feePerDayBnb);
    BigDecimal needed = targetBnb.subtract(bnbBalance).max(BigDecimal.ZERO);
    BigDecimal minTopup = feeProperties.getBnbMinTopupBnb();
    BigDecimal maxTopup = feeProperties.getBnbMaxTopupBnb();
    BigDecimal topupQty = needed.max(minTopup).min(maxTopup);
    if (topupQty.compareTo(BigDecimal.ZERO) <= 0) {
      return Optional.empty();
    }
    OrderRequest request = new OrderRequest();
    request.setSymbol("BNB" + quoteAsset);
    request.setSide(OrderSide.BUY);
    request.setType(OrderType.MARKET);
    request.setQuantity(topupQty);
    request.setDryRun(false);
    request.setClientOrderId("bnb-topup-" + Instant.now(clock).toEpochMilli());
    OrderResponse response = orderService.placeOrder(request);
    topups.increment();
    notifier.notifyBnbTopup(topupQty, topupQty.multiply(price.price()));
    return Optional.ofNullable(response);
  }

  private BigDecimal balanceOf(AccountBalancesResponse balances, String asset) {
    if (balances == null || balances.balances() == null) {
      return BigDecimal.ZERO;
    }
    return balances.balances().stream()
        .filter(b -> b.asset().equalsIgnoreCase(asset))
        .map(Balance::free)
        .findFirst()
        .orElse(BigDecimal.ZERO);
  }
}
