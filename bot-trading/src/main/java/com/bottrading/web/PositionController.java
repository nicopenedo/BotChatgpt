package com.bottrading.web;

import com.bottrading.execution.PositionManager;
import com.bottrading.model.entity.PositionEntity;
import com.bottrading.model.enums.PositionStatus;
import com.bottrading.repository.PositionRepository;
import com.bottrading.service.exchange.ExchangeReconciler;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class PositionController {

  private final PositionRepository positionRepository;
  private final PositionManager positionManager;
  private final ExchangeReconciler exchangeReconciler;

  public PositionController(
      PositionRepository positionRepository,
      PositionManager positionManager,
      ExchangeReconciler exchangeReconciler) {
    this.positionRepository = positionRepository;
    this.positionManager = positionManager;
    this.exchangeReconciler = exchangeReconciler;
  }

  @GetMapping("/api/positions/open")
  public Page<PositionEntity> openPositions(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(page, size);
    List<PositionEntity> open = positionRepository.findByStatus(PositionStatus.OPEN);
    int start = Math.min(page * size, open.size());
    int end = Math.min(start + size, open.size());
    return new org.springframework.data.domain.PageImpl<>(open.subList(start, end), pageable, open.size());
  }

  @GetMapping("/api/positions/{id}")
  public ResponseEntity<PositionEntity> findById(@PathVariable Long id) {
    return positionRepository.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }

  @PostMapping("/admin/positions/{id}/close")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> closePosition(@PathVariable @NotNull Long id) {
    positionManager.closePosition(id);
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/admin/reconcile")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> reconcile() {
    exchangeReconciler.reconcileAll();
    return ResponseEntity.accepted().build();
  }
}
