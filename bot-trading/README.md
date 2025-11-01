# Binance Spot Scalping Bot

## Introducción
Este proyecto provee un bot de scalping para Binance Spot construido con Spring Boot 3 y Java 21. El objetivo es entregar una base robusta, segura y lista para producción que permita operar un símbolo (por defecto `BTCUSDT`) y extenderse fácilmente a múltiples mercados.

```
+-------------------+        +-------------------+        +------------------+
| REST API (Spring) | <----> | Servicios Trading | <----> | Binance Spot API |
+-------------------+        +-------------------+        +------------------+
        |                               |                          |
        v                               v                          v
+-------------------+        +-------------------+        +------------------+
| Seguridad & Rate  |        | Estrategia SMA    |        |  Persistencia    |
| limit (Security,  |        | RiskGuard,        |        |  PostgreSQL      |
| Bucket4j, Micrometer)      | TradingState      |        |  Flyway          |
+-------------------+        +-------------------+        +------------------+
```

## Arquitectura
- **Spring Boot 3.3**: motor del servicio REST y configuración.
- **Binance Connector Java**: cliente oficial para operar contra la API Spot.
- **Resilience4j**: políticas de retry, rate limiting y circuit breaking.
- **Bucket4j**: limitación de tasa para la API REST.
- **Caffeine**: cacheos críticos (exchange info, comisiones).
- **Micrometer + Prometheus**: observabilidad.
- **PostgreSQL + JPA + Flyway**: persistencia transaccional con migraciones versionadas.

## Requisitos previos
- Java 21
- Maven 3.9+
- Docker y Docker Compose (para despliegue rápido)

## Configuración de entorno
Copiar `.env.example` a `.env` y completar las variables sensibles:

```
cp .env.example .env
```

Variables principales:
- `BINANCE_API_KEY`, `BINANCE_API_SECRET`: credenciales Binance (usar testnet por defecto).
- `BINANCE_BASE_URL`: `https://testnet.binance.vision` para modo demo.
- Parámetros `TRADING_*` para controlar riesgo y estrategia.
- Configuración de base de datos `SPRING_DATASOURCE_*`.

> **Seguridad:** No se versionan secretos reales. El archivo `.secrets.baseline` permite integrar `detect-secrets` en el flujo de pre-commit.

## Perfiles
- `test` (default): usa testnet y `trading.live-enabled=false`.
- `prod`: usa mainnet y habilita trading real mediante `TRADING_LIVE_ENABLED=true` o bandera en línea de comando.

## Puesta en marcha con Docker Compose
```
cd bot-trading
docker-compose up -d
```
Esto levanta PostgreSQL y la aplicación en modo testnet.

### Migraciones y base de datos
Flyway ejecuta automáticamente `V1__baseline.sql`. En producción se recomienda correr `mvn -Pprod flyway:migrate` dentro del contenedor o pipeline.

## Ejecución local
```
mvn spring-boot:run
```
Opciones adicionales:
```
mvn -Pprod -Dspring-boot.run.profiles=prod spring-boot:run \
  -Dspring-boot.run.arguments="--trading.live-enabled=true"
```

## API REST
| Método | Endpoint | Rol requerido | Descripción |
|--------|----------|---------------|-------------|
| GET | `/api/market/price?symbol=BTCUSDT` | `READ` | Precio spot actual. |
| GET | `/api/market/klines?symbol=BTCUSDT&interval=1m&limit=200` | `VIEWER`/`READ` | Serie de velas, soporta filtros `from`/`to`. |
| GET | `/api/market/vwap?symbol=BTCUSDT&interval=1m` | `VIEWER` | VWAP diario o anclado (`anchorTs`). |
| GET | `/api/indicators/atr-bands?symbol=BTCUSDT&interval=1m&period=14&mult=1.0` | `VIEWER` | Bandas ATR (mid/upper/lower). |
| GET | `/api/indicators/supertrend?symbol=BTCUSDT&interval=1m&atrPeriod=14&multiplier=3` | `VIEWER` | Serie Supertrend (opcional). |
| GET | `/api/reports/trades` | `VIEWER` | Trades paginados con filtros y export CSV/JSON. |
| GET | `/api/reports/summary?groupBy=day|week|month|range` | `VIEWER` | Resumen agregado con KPIs. |
| GET | `/api/reports/equity` | `VIEWER` | Serie de equity y `/api/reports/drawdown`. |
| GET | `/api/reports/annotations` | `VIEWER` | Marcadores BUY/SELL/SL/TP/BE/Trailing. |
| GET | `/api/reports/heatmap` | `VIEWER` | Heatmap PnL hora/día con export CSV. |
| POST | `/api/trade/order` | `TRADE` | Enviar orden (LIMIT/MARKET). |
| GET | `/api/trade/order/{id}?symbol=BTCUSDT` | `READ` | Consultar estado de orden. |
| GET | `/api/account/balances?assets=USDT,BTC` | `READ` | Balances filtrados. |
| POST | `/admin/kill-switch` | `ADMIN` | Activa kill switch. |
| POST | `/admin/resume` | `ADMIN` | Desactiva kill switch. |
| POST | `/admin/scheduler/enable` | `ADMIN` | Habilita el scheduler de velas. |
| POST | `/admin/scheduler/disable` | `ADMIN` | Deshabilita el scheduler (modo mantenimiento). |
| GET | `/admin/scheduler/status` | `ADMIN` | Devuelve modo (`ws`/`polling`), última `decisionKey`, flags y cooldown restante. |
| GET | `/admin/live-enabled` | `ADMIN` | Consulta estado `liveEnabled`. |
| GET | `/api/strategy/decide?symbol=BTCUSDT` | `READ` | Evalúa la estrategia compuesta y devuelve `side`, `confidence` y notas. |
| GET | `/api/positions/open` | `READ` | Lista posiciones abiertas paginadas. |
| GET | `/api/positions/{id}` | `READ` | Recupera el detalle de una posición. |
| POST | `/admin/positions/{id}/close` | `ADMIN` | Cierra una posición y cancela órdenes gestionadas. |
| POST | `/admin/reconcile` | `ADMIN` | Fuerza reconciliación contra Binance. |

## Panel Web de Trading

La ruta `GET /ui/dashboard` (rol `VIEWER`) habilita un panel completo construido con **Thymeleaf + Chart.js Financial**.

- **Filtros persistentes:** símbolo, intervalo, rango de fechas (`from/to` en la URL), side y estado de la posición.
- **Gráfico principal:** velas con overlays seleccionables (VWAP diario, VWAP anclado, ATR Bands, Supertrend) y volumen opcional.
- **Marcadores avanzados:** BUY/SELL, SL, TP, BE y Trailing con tooltip (PnL, fee, slippage en bps, nota de decisión).
- **Curvas sincronizadas:** equity y drawdown reaccionan al crosshair del gráfico de precio.
- **Heatmap PnL:** vista 7×24 coloreada según net PnL/win rate, exportable CSV.
- **KPIs:** Net PnL, Trades, Win rate, Profit factor, Max DD, Sharpe y Sortino actualizados al rango activo.
- **Tablas:** trades paginados y resúmenes diarios/semanales/mensuales o por rango.
- **Exports:** botones directos para CSV/JSON (trades, summary, equity, heatmap) y PNG del gráfico principal.
- **Overlays adicionales:** selector de VWAP anclado (datetime), toggles para ATR/Supertrend/volumen/marcadores, guardados en querystring.

Para iniciar sesión demo usar `viewer/viewerPass`. El front consume los endpoints `/api/reports/**` y `/api/market/**`, todos protegidos por el rol `VIEWER`.

### URLs útiles
- `/ui/dashboard?symbol=BTCUSDT&interval=1h&from=2024-01-01T00:00:00Z&to=2024-01-07T00:00:00Z`
- `/api/reports/summary?symbol=ETHUSDT&groupBy=month&from=2024-01-01T00:00:00Z&to=2024-03-31T23:59:59Z`
- `/api/market/vwap?symbol=BTCUSDT&interval=5m&anchorTs=2024-01-05T12:00:00Z`
- `/api/reports/trades/export.csv?symbol=BTCUSDT&from=2024-01-01T00:00:00Z&to=2024-01-31T23:59:59Z`

## Señales & Estrategia
- Las señales técnicas viven en `src/main/java/com/bottrading/strategy/signals/` e incluyen SMA/EMA crossover, MACD, RSI con filtro de tendencia, Bollinger Bands, Supertrend, Donchian, Stochastic, VWAP, filtros ATR/ADX/volumen 24h y más.
- `CompositeStrategy` permite ponderar cada señal mediante `weight` y aplicar filtros. Si un filtro devuelve `FLAT` la operación se aborta.
- La configuración se controla desde [`src/main/resources/strategy.yml`](src/main/resources/strategy.yml). Se pueden activar/desactivar señales, ajustar parámetros (periodos, umbrales, multiplicadores) y definir los umbrales globales `buy`/`sell`.
- `StrategyFactory` carga el YAML (o usa defaults sensatos) y expone una `CompositeStrategy` lista para usarse. El servicio `StrategyService` convierte los klines en series numéricas, aplica filtros (por ejemplo 24h volume) y devuelve `SignalResult`.
- El scheduler `TradingScheduler` opera en **modo vela (WS/polling)**: escucha `kline@interval` por WebSocket con reconexión exponencial y cae a polling consciente de cierres cuando el stream no está disponible. Asegura idempotencia por `decisionKey`, respeta `TradingState`, `RiskGuard`, ventanas horarias, volumen 24h, rate limits internos y normaliza órdenes antes de delegar en `OrderExecutionService`.
- Endpoint REST: `/api/strategy/decide` devuelve la última evaluación. Ideal para dashboards o monitoreo manual.
- Warm-up: cada señal valida si existen velas suficientes antes de emitir voto. El número de velas (p.ej. 200 para 1m) se controla en `StrategyService`. Si falta histórico, la estrategia retorna `FLAT`.
- Tests unitarios cubren cruces SMA/EMA, MACD, RSI, Bollinger, Supertrend, composición y el motor de backtest/GA.

### Seguridad
- Autenticación básica (HTTP Basic) con usuarios definidos en `application.properties` (solo para demo). Mover a un almacén seguro o federación de identidad en producción.
- Endpoints públicos: únicamente `/actuator/health`.

### Rate limiting
- Bucket4j limita `/api/trade/**` a `maxOrdersPerMinute` configurado en `TradingProps`.

## Observabilidad
- Métricas en `/actuator/prometheus`.
- Salud en `/actuator/health`.
- Contadores: `scheduler.candle.decisions{result=BUY|SELL|FLAT|SKIPPED,...}`, `orders.sent`, `orders.filled`, `strategy.signals`, `risk.stopouts`.
- Temporizador: `scheduler.candle.duration.ms`.
- Gauges: `risk.drawdown`, `risk.equity`.

## Research: Backtesting & GA
- Nuevo módulo en `com.bottrading.research` con:
  - `DataLoader` (cache CSV), `ExecutionSimulator`, `Portfolio`, métricas (CAGR, Sharpe, Sortino, Calmar, Profit Factor, win rate, expectancy, exposición, max DD).
  - `ReportWriter`: genera `metrics.json`, `trades.csv`, `equity.csv`, `drawdown.csv` y script Python para graficar.
  - Motor GA (`Genome`, `Evaluator`, `GaRunner`, `WalkForwardOptimizer`) que optimiza pesos/params de las señales.
- CLI:
  - Backtest: `java -jar target/bot-trading-1.0.0.jar backtest --symbol BTCUSDT --interval 1m --from 2024-01-01T00:00:00Z --to 2024-01-02T00:00:00Z --strategy ./strategy.yml --slippageBps 2 --fees 5`
  - GA: `java -jar target/bot-trading-1.0.0.jar ga --symbol BTCUSDT --interval 1m --from 2024-01-01T00:00:00Z --to 2024-02-01T00:00:00Z --pop 30 --gens 20 --seed 42`
- Resultados se escriben en `research-output/` y `ga-output/` por defecto (configurable con `--out`).
- Tests unitarios (`BacktestEngineTest`, `GaRunnerTest`) validan el pipeline determinista.

## Pruebas
```
mvn test
```
Incluye:
- Unitarias para utilidades de normalización y validación de órdenes.
- Integración con WireMock simulando respuestas Binance.
- Nota: la ejecución requiere acceso a Maven Central; si se observa un `403 Forbidden` al resolver
  dependencias, configure un mirror corporativo o repositorio alterno antes de volver a ejecutar
  los tests.

## Despliegue
### Docker
```
docker build -t bot-trading:latest .
```
Configurar variables en `docker-compose.yml` o `.env`.

### Systemd
Archivo ejemplo en `deploy/bot-trading.service` (copiar a `/etc/systemd/system/`).

### Kubernetes
Chart base en `k8s/` con Deployment y Service listos para extender.

## Extensiones futuras
- Migrar autenticación a JWT.
- Integrar motor de estrategias múltiples.
- Añadir persistencia de fills y reconciliación automática.

## Licencia
MIT.

## Gestión de posiciones y OCO híbrido
- `PositionManager` centraliza el ciclo de vida `OPENING → OPEN → CLOSING → CLOSED` con locks por posición e idempotencia sobre cada evento de fill.
- Crea siempre el par SL/TP y primero intenta emitir un **OCO nativo** (`BinanceClient.placeOcoOrder`). Si el exchange responde que no está soportado se activa la **emulación lado cliente**: se levantan órdenes independientes y, ante un fill, se fuerza la cancelación del opuesto con reintentos (`oco.corrections`).
- Persistencia en `positions`, `managed_orders` y `trades`, métricas `positions.opened`, `positions.closed`, `orders.partial`, `orders.filled`, `orders.canceled` y notificaciones `notifyPositionOpened`, `notifyTakeProfit`, `notifyStopHit`, `notifyOcoCorrected`.
- `POST /admin/positions/{id}/close` permite forzar el cierre de una posición (cancela pendientes, marca `CLOSED`). `GET /api/positions/open` y `GET /api/positions/{id}` exponen el estado actual.

## Listeners de fills (userDataStream)
- `UserDataStreamService` mantiene el `listenKey`, renueva cada `binance.userdatastream.keepalive-minutes` y traduce los `executionReport` a `ManagedOrderUpdate` consumidos por el `PositionManager`.
- Si el cliente Binance lanza `UnsupportedOperationException` (p. ej. en entornos de prueba) el servicio degrada a modo noop pero expone `dispatch(...)` para inyectar eventos en tests.
- Toda reconexión/keep-alive fallido queda logueada y dispara `notifyError`/`notifyOcoCorrected` según corresponda.

## Reconciliación al arranque
- `ExchangeReconciler` consulta órdenes abiertas recientes (`BinanceClient.getOpenOrders/getRecentOrders`), las convierte en `ExternalOrderSnapshot` y llama a `positionManager.reconcile(...)` para alinear el estado local.
- Propiedades claves: `reconcile.on-startup`, `reconcile.scan-minutes`, `positions.lock-timeout-ms`.
- Endpoint `POST /admin/reconcile` ejecuta una reconciliación manual sobre todos los símbolos abiertos y deja trazabilidad vía `notifyReconciledItem`.

## Parciales y trailing
- Un fill parcial (`PARTIAL`) reduce `qtyRemaining` de la posición y ajusta el tamaño de la orden opuesta para mantener el vínculo OCO lógico. Al llegar a `FILLED` se registra el trade y se cancela el opuesto.
- `StopEngine` se enfoca en calcular planes SL/TP/trailing/breakeven; los ajustes dinámicos (breakeven y trailing) siguen corriendo a través de los eventos de precio y de fills.
- Cuando la cantidad remanente cae por debajo del `stepSize` efectivo se marca la posición como cerrada y se limpian órdenes pendientes.

### Comandos de ejemplo
```bash
# Forzar reconciliación manual
curl -X POST -u admin:*** http://localhost:8080/admin/reconcile
# Cerrar una posición a mercado
curl -X POST -u admin:*** http://localhost:8080/admin/positions/123/close
```

## Sizing por riesgo
`OrderSizingService` calcula el tamaño de posición dinámico en función del riesgo por trade configurado (`trading.risk-per-trade-pct`), la distancia al stop y las restricciones de `stepSize`/`minNotional`.

- Modelos de slippage: `atr` o `fixedbps` (`sizing.slippage.model`).
- Ajuste de `minNotional` con buffer (`sizing.min-notional-buffer-pct`).
- Recomendación de tipo de orden (`MARKET`/`LIMIT`) y soporte opcional para iceberg.
- Tests cubren BUY/SELL, diferentes distancias de stop y validaciones contra `stepSize`/`minNotional`.

## Shadow Trading
El `ShadowEngine` replica cada trade live en modo simulación usando las mismas reglas de stop y trailing, guardando resultados en `shadow_positions`.

- Divergencias Live vs Shadow se consultan con `GET /api/shadow/status?symbol=BTCUSDT`.
- Si la diferencia porcentual supera `shadow.divergence.pct-threshold` durante `shadow.divergence.min-trades` envía una alerta a Telegram.
- Métricas: `shadow.divergence.alerts`, `shadow.pnl.live`, `shadow.pnl.shadow`.

## Fee Optimizer
`FeeService` cachea comisiones efectivas (maker/taker) 30 minutos y descuenta automáticamente al pagar con BNB. `BnbRebalancer` monitorea el buffer de BNB y ejecuta top-ups cuando la cobertura en días cae por debajo de `fees.bnb.min-days-buffer`.

- Métricas: `fees.effective.maker`, `fees.effective.taker`, `fees.topups`.
- Ajustar límites de reposición con `fees.bnb.min-topup-bnb` y `fees.bnb.max-topup-bnb`.

## Alertas Telegram
`TelegramNotifier` envía notificaciones Markdown a un chat configurado (`telegram.bot-token`, `telegram.chat-id`). Eventos cubiertos:

- Fills/partial fills/cancelaciones.
- Stops, take-profit, movimientos a breakeven y ajustes de trailing.
- Kill-switch ON/OFF, reconexiones de WS, errores relevantes.
- Divergencias Live vs Shadow y reposiciones de BNB.

Para habilitar, establecer `TELEGRAM_ENABLED=true` en `.env` y proporcionar token + chat id.
