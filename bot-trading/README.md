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
| GET | `/api/market/klines?symbol=BTCUSDT&interval=1m&limit=200` | `READ` | Serie de velas. |
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
