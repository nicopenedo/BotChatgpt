package com.bottrading.repository;

import com.bottrading.model.entity.PositionEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionRepository extends JpaRepository<PositionEntity, Long> {
  Optional<PositionEntity> findFirstBySymbolAndClosedAtIsNull(String symbol);
}
