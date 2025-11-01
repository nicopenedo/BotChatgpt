package com.bottrading.strategy.router;

import static org.assertj.core.api.Assertions.assertThat;

import com.bottrading.config.TradingProps;
import com.bottrading.research.regime.Regime;
import com.bottrading.research.regime.RegimeTrend;
import com.bottrading.research.regime.RegimeVolatility;
import com.bottrading.strategy.StrategyFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

class StrategyRouterTest {

  private TradingProps props;
  private StrategyFactory factory;
  private StrategyRouter router;

  @BeforeEach
  void setUp() {
    props = new TradingProps();
    props.getRouter().setEnabled(true);
    factory = new StrategyFactory(new InMemoryResourceLoader(customYaml()), props);
    router = new StrategyRouter(props, factory, new SimpleMeterRegistry());
  }

  @Test
  void shouldSelectPresetByTrendAndVolatility() {
    Regime regime =
        new Regime(
            "BTCUSDT",
            "1m",
            RegimeTrend.UP,
            RegimeVolatility.LO,
            0.01,
            25,
            0.2,
            Instant.now());
    StrategyRouter.Selection selection = router.select("BTCUSDT", regime);
    assertThat(selection.preset()).isEqualTo("trend_up");
    assertThat(selection.strategy()).isNotNull();
  }

  @Test
  void shouldFallbackToDefaultWhenRouterDisabled() {
    props.getRouter().setEnabled(false);
    Regime regime =
        new Regime(
            "ETHUSDT",
            "1m",
            RegimeTrend.DOWN,
            RegimeVolatility.HI,
            0.02,
            30,
            0.4,
            Instant.now());
    StrategyRouter.Selection selection = router.select("ETHUSDT", regime);
    assertThat(selection.preset()).isEqualTo(factory.getCatalog().defaultPreset());
  }

  private String customYaml() {
    return """
        thresholds:
          buy: 1.0
          sell: 1.0
        signals:
          - type: SMA_CROSS
            weight: 1.0
            confidence: 1.0
            params:
              fast: 5
              slow: 10
        presets:
          trend_up:
            thresholds:
              buy: 2.0
          range:
            thresholds:
              sell: 2.0
        router:
          rules:
            - when:
                trend: UP
              use: trend_up
            - when:
                trend: RANGE
              use: range
        """;
  }

  private static final class InMemoryResourceLoader implements ResourceLoader {

    private final String yaml;

    private InMemoryResourceLoader(String yaml) {
      this.yaml = yaml;
    }

    @Override
    public Resource getResource(String location) {
      return new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)) {
        @Override
        public boolean exists() {
          return true;
        }

        @Override
        public String getFilename() {
          return "strategy.yml";
        }

        @Override
        public long contentLength() throws IOException {
          return yaml.length();
        }
      };
    }

    @Override
    public ClassLoader getClassLoader() {
      return StrategyRouterTest.class.getClassLoader();
    }
  }
}

