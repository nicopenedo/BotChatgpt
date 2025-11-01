package com.bottrading.repository;

import com.bottrading.model.entity.PresetCanaryState;
import com.bottrading.model.enums.CanaryStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PresetCanaryStateRepository
    extends JpaRepository<PresetCanaryState, UUID> {
  List<PresetCanaryState> findByStatusIn(Collection<CanaryStatus> statuses);
}
