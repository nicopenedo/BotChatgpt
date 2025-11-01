package com.bottrading.repository;

import com.bottrading.model.entity.RiskVarSnapshotEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RiskVarSnapshotRepository extends JpaRepository<RiskVarSnapshotEntity, Long> {

  List<RiskVarSnapshotEntity> findTop50BySymbolOrderByTimestampDesc(String symbol);

  @Query(
      "SELECT s FROM RiskVarSnapshotEntity s WHERE (:symbol IS NULL OR s.symbol = :symbol)"
          + " AND (:fromTs IS NULL OR s.timestamp >= :fromTs)"
          + " ORDER BY s.timestamp DESC")
  List<RiskVarSnapshotEntity> findSnapshots(
      @Param("symbol") String symbol, @Param("fromTs") Instant fromTs);
}
