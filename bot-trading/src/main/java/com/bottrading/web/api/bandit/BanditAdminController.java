package com.bottrading.web.api.bandit;

import com.bottrading.bandit.BanditArmEntity;
import com.bottrading.bandit.BanditArmStatus;
import com.bottrading.bandit.BanditProperties;
import com.bottrading.bandit.BanditPullEntity;
import com.bottrading.bandit.BanditStore;
import com.bottrading.model.enums.OrderSide;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bandit")
@PreAuthorize("hasRole('ADMIN')")
public class BanditAdminController {

  private final BanditStore store;
  private final BanditProperties properties;

  public BanditAdminController(BanditStore store, BanditProperties properties) {
    this.store = store;
    this.properties = properties;
  }

  @GetMapping("/arms")
  public List<BanditArmResponse> listArms(
      @RequestParam(required = false) String symbol,
      @RequestParam(required = false) String regime,
      @RequestParam(required = false) String side) {
    OrderSide orderSide = parseSide(side);
    List<BanditArmEntity> arms = store.listArms(symbol, regime, orderSide);
    return arms.stream().map(BanditArmResponse::from).collect(Collectors.toList());
  }

  @PostMapping("/arms/{id}/block")
  public void block(@PathVariable("id") UUID id) {
    store.updateStatus(id, BanditArmStatus.BLOCKED);
  }

  @PostMapping("/arms/{id}/unblock")
  public void unblock(@PathVariable("id") UUID id) {
    store.updateStatus(id, BanditArmStatus.ELIGIBLE);
  }

  @PostMapping("/reset")
  public void reset(
      @RequestParam String symbol,
      @RequestParam String regime,
      @RequestParam String side,
      @RequestParam(defaultValue = "false") boolean confirm) {
    if (!confirm) {
      throw new IllegalArgumentException("Reset requires confirm=true");
    }
    store.resetStats(symbol, regime, parseSide(side));
  }

  @GetMapping("/pulls")
  public List<BanditPullResponse> recentPulls(
      @RequestParam String symbol,
      @RequestParam String regime,
      @RequestParam String side,
      @RequestParam(defaultValue = "50") int limit) {
    List<BanditPullEntity> pulls = store.recentPulls(symbol, regime, parseSide(side), limit);
    return pulls.stream().map(BanditPullResponse::from).collect(Collectors.toList());
  }

  @GetMapping("/overview")
  public BanditOverviewResponse overview(@RequestParam String symbol) {
    BanditStore.CanaryBudgetSnapshot snapshot = store.canarySnapshot(symbol, Instant.now());
    double share =
        snapshot.totalPulls() == 0
            ? 0.0
            : (double) snapshot.candidatePulls() / snapshot.totalPulls();
    return new BanditOverviewResponse(
        properties.getAlgorithm().name(), share, snapshot.totalPulls(), snapshot.candidatePulls());
  }

  private OrderSide parseSide(String side) {
    if (side == null || side.isBlank()) {
      return null;
    }
    return OrderSide.valueOf(side.toUpperCase());
  }

  public record BanditOverviewResponse(String algorithm, double candidateShare, long totalPulls, long candidatePulls) {}
}
