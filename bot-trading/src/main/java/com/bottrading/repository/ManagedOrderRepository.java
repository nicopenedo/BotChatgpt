package com.bottrading.repository;

import com.bottrading.model.entity.ManagedOrderEntity;
import com.bottrading.model.entity.PositionEntity;
import com.bottrading.model.enums.ManagedOrderStatus;
import com.bottrading.model.enums.ManagedOrderType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagedOrderRepository extends JpaRepository<ManagedOrderEntity, Long> {
  List<ManagedOrderEntity> findByPosition(PositionEntity position);

  List<ManagedOrderEntity> findByPositionIdAndStatusIn(Long positionId, Collection<ManagedOrderStatus> statuses);

  Optional<ManagedOrderEntity> findByPositionAndType(PositionEntity position, ManagedOrderType type);

  Optional<ManagedOrderEntity> findByClientOrderId(String clientOrderId);
}
