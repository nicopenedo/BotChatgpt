package com.bottrading.repository;

import com.bottrading.model.entity.BacktestRun;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BacktestRunRepository extends JpaRepository<BacktestRun, String> {
  Optional<BacktestRun> findByRunId(String runId);
}
