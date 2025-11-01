package com.bottrading.repository;

import com.bottrading.model.entity.ShadowPositionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShadowPositionRepository extends JpaRepository<ShadowPositionEntity, Long> {
  List<ShadowPositionEntity> findBySymbolOrderByOpenedAtDesc(String symbol);
}
