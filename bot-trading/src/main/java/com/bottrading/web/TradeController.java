package com.bottrading.web;

import com.bottrading.model.dto.OrderRequest;
import com.bottrading.model.dto.OrderResponse;
import com.bottrading.service.trading.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trade")
public class TradeController {

  private final OrderService orderService;

  public TradeController(OrderService orderService) {
    this.orderService = orderService;
  }

  @PostMapping("/order")
  @PreAuthorize("hasRole('TRADE')")
  public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody OrderRequest request) {
    return ResponseEntity.ok(orderService.placeOrder(request));
  }

  @GetMapping("/order/{orderId}")
  @PreAuthorize("hasRole('READ')")
  public ResponseEntity<OrderResponse> getOrder(
      @PathVariable String orderId, @RequestParam String symbol) {
    return ResponseEntity.ok(orderService.getOrder(symbol, orderId));
  }
}
