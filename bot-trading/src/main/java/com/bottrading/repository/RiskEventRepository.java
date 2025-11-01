package com.bottrading.repository;

import com.bottrading.model.entity.RiskEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskEventRepository extends JpaRepository<RiskEventEntity, Long> {}
