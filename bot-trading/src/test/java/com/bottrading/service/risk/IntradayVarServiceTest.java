package com.bottrading.service.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.bottrading.config.VarProperties;
import com.bottrading.model.enums.OrderSide;
import com.bottrading.repository.PositionRepository;
import com.bottrading.repository.RiskVarSnapshotRepository;
import com.bottrading.repository.ShadowPositionRepository;
import com.bottrading.service.risk.IntradayVarService.HistoricalSample;
import com.bottrading.service.risk.IntradayVarService.SampleUniverse;
import com.bottrading.service.risk.IntradayVarService.VarAssessment;
import com.bottrading.service.risk.IntradayVarService.VarInput;
import com.bottrading.saas.service.TenantMetrics;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class IntradayVarServiceTest {

  @Mock private RiskVarSnapshotRepository snapshotRepository;
  @Mock private PositionRepository positionRepository;
  @Mock private ShadowPositionRepository shadowPositionRepository;
  @Mock private NamedParameterJdbcTemplate jdbcTemplate;
  @Mock private TenantMetrics tenantMetrics;

  private VarProperties properties;
  private SampleUniverse universe;

  @BeforeEach
  void setUp() {
    properties = new VarProperties();
    properties.setEnabled(true);
    properties.setMcIterations(16);
    properties.setHeavyTails(false);
    properties.setQuantile(0.99);
    when(positionRepository.findByStatus(any())).thenReturn(List.of());
    when(tenantMetrics.tags(any())).thenReturn(Tags.empty());

    List<HistoricalSample> samples = new ArrayList<>();
    for (int i = 0; i < 12; i++) {
      samples.add(new HistoricalSample(-0.01, 0.0, null, null, null, null));
    }
    universe = new SampleUniverse(samples, List.of());
  }

  @Test
  void assessUsesDeterministicStatsWithFixedClock() {
    StubIntradayVarService service = new StubIntradayVarService(universe);

    VarInput input =
        new VarInput(
            "BTCUSDT",
            "preset",
            UUID.randomUUID(),
            null,
            null,
            OrderSide.BUY,
            BigDecimal.valueOf(110),
            BigDecimal.valueOf(100),
            BigDecimal.ONE,
            BigDecimal.valueOf(1_000),
            BigDecimal.valueOf(0.001));

    VarAssessment assessment = service.assess(input);

    assertThat(assessment.blocked()).isFalse();
    assertThat(assessment.var()).isEqualByComparingTo("0.1");
    assertThat(assessment.cvar()).isEqualByComparingTo("0.1");
    assertThat(assessment.samples()).isEqualTo(12);
    assertThat(assessment.universe().samples()).hasSize(12);
  }

  private class StubIntradayVarService extends IntradayVarService {
    StubIntradayVarService(SampleUniverse universe) {
      super(
          properties,
          snapshotRepository,
          positionRepository,
          shadowPositionRepository,
          jdbcTemplate,
          new SimpleMeterRegistry(),
          tenantMetrics);
      this.overrideUniverse = universe;
    }

    private final SampleUniverse overrideUniverse;

    @Override
    SampleUniverse loadSamples(String symbol, String presetKey, String regimeTrend, String regimeVolatility) {
      return overrideUniverse;
    }
  }
}
