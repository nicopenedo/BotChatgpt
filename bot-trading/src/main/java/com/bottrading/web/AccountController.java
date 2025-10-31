package com.bottrading.web;

import com.bottrading.model.dto.AccountBalancesResponse;
import com.bottrading.service.trading.OrderService;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public class AccountController {

  private final OrderService orderService;

  public AccountController(OrderService orderService) {
    this.orderService = orderService;
  }

  @GetMapping("/balances")
  @PreAuthorize("hasRole('READ')")
  public ResponseEntity<AccountBalancesResponse> getBalances(@RequestParam(required = false) String assets) {
    List<String> assetList =
        assets == null
            ? List.of()
            : Arrays.stream(assets.split(",")).map(String::trim).collect(Collectors.toList());
    return ResponseEntity.ok(orderService.getBalances(assetList));
  }
}
