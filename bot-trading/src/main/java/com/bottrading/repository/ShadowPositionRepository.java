package com.bottrading.repository;

import com.bottrading.model.entity.ShadowPositionEntity;
import com.bottrading.model.enums.PositionStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShadowPositionRepository extends JpaRepository<ShadowPositionEntity, Long> {
  List<ShadowPositionEntity> findBySymbolOrderByOpenedAtDesc(String symbol);

  List<ShadowPositionEntity> findByPresetIdAndStatusOrderByClosedAtAsc(
      UUID presetId, PositionStatus status);

  long countByPresetIdAndStatus(UUID presetId, PositionStatus status);
}
