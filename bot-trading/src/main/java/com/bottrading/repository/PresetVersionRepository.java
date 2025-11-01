package com.bottrading.repository;

import com.bottrading.model.entity.PresetVersion;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.PresetStatus;
import com.bottrading.research.regime.RegimeTrend;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PresetVersionRepository extends JpaRepository<PresetVersion, UUID> {
  Optional<PresetVersion> findFirstByRegimeAndSideAndStatusOrderByActivatedAtDesc(
      RegimeTrend regime, OrderSide side, PresetStatus status);

  List<PresetVersion> findByRegimeAndSideOrderByCreatedAtDesc(RegimeTrend regime, OrderSide side);

  List<PresetVersion> findByRegimeAndSideAndStatusOrderByActivatedAtDesc(
      RegimeTrend regime, OrderSide side, PresetStatus status);

  List<PresetVersion> findByStatus(PresetStatus status);

  List<PresetVersion> findByRegimeAndSideAndStatusOrderByRetiredAtDesc(
      RegimeTrend regime, OrderSide side, PresetStatus status);
}
