package com.bottrading.repository;

import com.bottrading.model.entity.PositionEntity;
import com.bottrading.model.enums.PositionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionRepository extends JpaRepository<PositionEntity, Long> {
  Optional<PositionEntity> findFirstBySymbolAndStatusIn(String symbol, Collection<PositionStatus> statuses);

  Optional<PositionEntity> findByCorrelationId(String correlationId);

  List<PositionEntity> findByStatus(PositionStatus status);
}
