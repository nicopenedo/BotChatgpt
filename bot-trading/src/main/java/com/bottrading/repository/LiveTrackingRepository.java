package com.bottrading.repository;

import com.bottrading.model.entity.LiveTracking;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveTrackingRepository extends JpaRepository<LiveTracking, UUID> {
  List<LiveTracking> findByPresetIdOrderByCreatedAtDesc(UUID presetId);
}
