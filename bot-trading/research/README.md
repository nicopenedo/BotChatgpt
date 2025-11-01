# Research Module

Este módulo agrupa herramientas para investigación cuantitativa sobre la estrategia compuesta.

## Componentes
- **DataLoader** (`com.bottrading.research.io.DataLoader`): descarga klines desde Binance (limitado a 1000 velas por llamada) o reutiliza el cache CSV en `research-cache/`.
- **ExecutionSimulator**: aplica slippage en BPS, comisiones maker/taker y devuelve fills simulados.
- **Portfolio**: maneja capital quote/base, registra operaciones (`TradeRecord`) y genera la curva de equity.
- **MetricsCalculator**: calcula CAGR, Sharpe, Sortino, Calmar, Profit Factor, win rate, expectancy, max drawdown y exposición.
- **ReportWriter**: exporta `metrics.json`, `trades.csv`, `equity.csv`, `drawdown.csv` y un script `plot_equity.py` listo para graficar con Python.
- **GA** (`com.bottrading.research.ga`): representa genomas (on/off + pesos + parámetros de señales), operadores genéticos, evaluación paralela y `GaRunner` para iterar generaciones. Incluye `WalkForwardOptimizer` para dividir períodos train/validation/test.

## Uso rápido
```
# Backtest 1m con estrategia YAML custom y reportes en ./reports
java -jar target/bot-trading-1.0.0.jar backtest \
  --symbol BTCUSDT --interval 1m \
  --from 2024-01-01T00:00:00Z --to 2024-01-07T00:00:00Z \
  --strategy ./strategy.yml --slippageBps 2 --fees 5 \
  --out ./reports

# Optimización genética con población 40, 25 generaciones
java -jar target/bot-trading-1.0.0.jar ga \
  --symbol BTCUSDT --interval 1m \
  --from 2024-01-01T00:00:00Z --to 2024-03-01T00:00:00Z \
  --pop 40 --gens 25 --seed 123 --maxWorkers 4
```

## Tips
- Ajustar `research-cache/` si se desea conservar múltiples series (uno por símbolo/intervalo).
- Para WFO, generar `BacktestRequest` segmentados con `WalkForwardOptimizer.split(...)` y pasar un `GaRunnerFactory` que cree `GaRunner` por ventana.
- Los reportes pueden alimentarse directamente a notebooks o al script Python auto-generado.
- Tests unitarios (`BacktestEngineTest`, `GaRunnerTest`) ayudan a validar cambios en el pipeline de investigación.
