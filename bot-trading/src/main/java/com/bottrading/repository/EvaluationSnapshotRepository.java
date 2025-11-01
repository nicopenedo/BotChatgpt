package com.bottrading.repository;

import com.bottrading.model.entity.EvaluationSnapshot;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationSnapshotRepository extends JpaRepository<EvaluationSnapshot, UUID> {
  List<EvaluationSnapshot> findByPresetIdOrderByCreatedAtDesc(UUID presetId);

  List<EvaluationSnapshot> findByWindowOrderByCreatedAtDesc(String window);
}
