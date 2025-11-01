package com.bottrading.service.preset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bottrading.config.PresetsProperties;
import com.bottrading.model.entity.PresetCanaryState;
import com.bottrading.model.entity.ShadowPositionEntity;
import com.bottrading.model.enums.CanaryStatus;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.model.enums.PositionStatus;
import com.bottrading.repository.PresetCanaryStateRepository;
import com.bottrading.repository.ShadowPositionRepository;
import com.bottrading.research.nightly.ResearchProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CanaryStageServiceTest {

  @Mock private PresetCanaryStateRepository stateRepository;
  @Mock private ShadowPositionRepository shadowRepository;
  @Mock private PresetService presetService;

  private CanaryStageService service;
  private PresetsProperties presetsProperties;
  private ResearchProperties.Nightly.Gate gate;

  @BeforeEach
  void setUp() {
    presetsProperties = new PresetsProperties();
    ResearchProperties properties = new ResearchProperties();
    gate = properties.getNightly().getGate();
    Clock clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
    service = new CanaryStageService(
        stateRepository, shadowRepository, presetService, presetsProperties, Optional.of(clock));
    when(stateRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void evaluateStateWaitsUntilShadowTradesReachMinimum() {
    gate.setShadowMinTrades(50);

    PresetCanaryState state = new PresetCanaryState();
    UUID presetId = UUID.randomUUID();
    state.setPresetId(presetId);
    state.setOosPf(1.6);
    state.setCurrentMultiplier(presetsProperties.getCanary().getStages().get(0));
    state.setStageIndex(0);

    List<ShadowPositionEntity> closed = buildTrades(20, BigDecimal.ONE);
    when(shadowRepository.findByPresetIdAndStatusOrderByClosedAtAsc(presetId, PositionStatus.CLOSED))
        .thenReturn(closed);

    CanaryStageService.StageUpdate update =
        service.evaluateState(state, gate, presetsProperties.getCanary().getStages());

    assertThat(update.status()).isEqualTo(CanaryStatus.SHADOW_PENDING);
    assertThat(update.message()).contains("Waiting for shadow trades");
    verify(stateRepository).save(state);
    assertThat(state.getStatus()).isEqualTo(CanaryStatus.SHADOW_PENDING);
  }

  @Test
  void evaluateStateRejectsWhenShadowProfitFactorDropsTooMuch() {
    gate.setShadowMinTrades(50);
    gate.setShadowPfDropTolerance(0.15);

    PresetCanaryState state = new PresetCanaryState();
    UUID presetId = UUID.randomUUID();
    state.setPresetId(presetId);
    state.setOosPf(1.6);
    state.setCurrentMultiplier(presetsProperties.getCanary().getStages().get(0));
    state.setStageIndex(0);

    List<ShadowPositionEntity> closed = new ArrayList<>();
    closed.addAll(buildTrades(25, BigDecimal.ONE));
    closed.addAll(buildTrades(25, BigDecimal.valueOf(-1)));
    when(shadowRepository.findByPresetIdAndStatusOrderByClosedAtAsc(presetId, PositionStatus.CLOSED))
        .thenReturn(closed);

    CanaryStageService.StageUpdate update =
        service.evaluateState(state, gate, presetsProperties.getCanary().getStages());

    assertThat(update.status()).isEqualTo(CanaryStatus.REJECTED);
    assertThat(update.message()).contains("Shadow PF");
    assertThat(state.getStatus()).isEqualTo(CanaryStatus.REJECTED);
    verify(stateRepository).save(state);
  }

  private List<ShadowPositionEntity> buildTrades(int count, BigDecimal pnl) {
    List<ShadowPositionEntity> trades = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      ShadowPositionEntity entity = new ShadowPositionEntity();
      entity.setStatus(PositionStatus.CLOSED);
      entity.setSide(OrderSide.BUY);
      entity.setRealizedPnl(pnl);
      entity.setQuantity(BigDecimal.ONE);
      trades.add(entity);
    }
    return trades;
  }
}
