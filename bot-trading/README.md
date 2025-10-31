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
| GET | `/admin/live-enabled` | `ADMIN` | Consulta estado `liveEnabled`. |

### Seguridad
- Autenticación básica (HTTP Basic) con usuarios definidos en `application.properties` (solo para demo). Mover a un almacén seguro o federación de identidad en producción.
- Endpoints públicos: únicamente `/actuator/health`.

### Rate limiting
- Bucket4j limita `/api/trade/**` a `maxOrdersPerMinute` configurado en `TradingProperties`.

## Observabilidad
- Métricas en `/actuator/prometheus`.
- Salud en `/actuator/health`.
- Contadores: `orders.sent`, `orders.filled`, `strategy.signals`, `risk.stopouts`.
- Gauges: `risk.drawdown`, `risk.equity`.

## Backtesting
Servicio `BacktestService` simula la estrategia `ScalpingSmaStrategy` sobre klines históricos (formato JSON). Ejecutar mediante:
```
java -jar target/bot-trading-1.0.0.jar --backtest.file=src/test/resources/klines/BTCUSDT-1d.json
```
Parámetros configurables: `backtest.slippage-bps`, `backtest.start-balance`.

## Pruebas
```
mvn test
```
Incluye:
- Unitarias para utilidades de normalización y validación de órdenes.
- Integración con WireMock simulando respuestas Binance.

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
