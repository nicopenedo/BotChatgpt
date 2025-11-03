# Binance Spot Scalping Bot

## Introducción
Este proyecto provee un bot de scalping para Binance Spot construido con Spring Boot 3 y Java 21. El objetivo es entregar una base robusta, segura y lista para producción que opere varios símbolos en paralelo (por defecto `BTCUSDT`, `ETHUSDT`, `BNBUSDT`) y pueda adaptarse dinámicamente a distintos regímenes de mercado.

## Setup local en 3 pasos

1. Copiá el archivo de ejemplo y creá tu entorno: `cp .env.example .env`.
2. Levantá las dependencias necesarias (Postgres + Redis) con Docker: `docker compose up -d`.
3. Ejecutá la aplicación en modo local/testnet: `mvn spring-boot:run -Dspring-boot.run.profiles=local`.

El archivo `.env.example` provee valores seguros que dejan el bot en modo **SHADOW** con `trading.risk.global-pause=true` y las claves de Binance apuntando al **TESTNET** (`https://testnet.binance.vision`). Para operar en vivo, cambiá `TRADING_MODE` a `LIVE`, desactivá el `GLOBAL_PAUSE` y configurá credenciales reales mediante variables de entorno (sin editar el código fuente).

## QA Seguridad UI
- [ ] Formularios en `/tenant/**` rechazan POST sin token CSRF y aceptan con token válido.
- [ ] Sitios externos no pueden enviar POST válidos a `/tenant/**` (403 por CSRF).
- [ ] Usuario con MFA habilitado siempre ve `/mfa` tras login y hasta validar TOTP.
- [ ] Usuario sin MFA accede directo al dashboard tras autenticarse.
- [ ] API REST en `/api/**` permanece stateless y sin protección CSRF.
- [ ] Webhooks en `/webhooks/**` se aceptan sólo si la firma HMAC es válida.
- [ ] Cookies de sesión entregadas como `Secure`, `HttpOnly` y `SameSite=Lax`.
- [ ] CSP aplicada restringiendo assets a `self`.

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
- **Regime Engine + Strategy Router**: clasifica cada vela en UP/DOWN/RANGE con volatilidad HI/LO y enruta presets de señales automáticamente.
- **Allocator multi-activo**: gobierna el sizing por símbolo, riesgo agregado y correlación máxima.
- **Drift & Health Watchdog**: compara live vs shadow/expected, aplica degradaciones automáticas y pausa si hay problemas de conectividad/API.
- **TCA Service**: registra slippage en bps, colas y recomienda LIMIT/MARKET según condiciones reales.

## SaaS multi-tenant "Bots as a Service"

La plataforma incorpora una capa completa SaaS para ofrecer los bots a clientes finales sin custodiar fondos. Cada tenant opera en su propia cuenta del exchange (API key sin permisos de retiro y con **IP allowlist**), mantiene aislamiento a nivel de base de datos (RLS) y cuenta con auditoría WORM por cada acción relevante.

### Onboarding 1-click

1. **Signup** (`POST /api/signup`): crea el tenant, usuario OWNER y exige aceptar Términos & Declaración de Riesgo (`terms_acceptance`).
2. **Checkout** (Stripe o Mercado Pago): los webhooks `/api/billing/webhook/*` actualizan `tenant_billing` y activan el plan.
3. **Carga de API key** (`POST /api/tenant/api-keys`): valida permisos (sin retiro) y enforcea allowlist.
4. **Shadow**: los bots arrancan en modo shadow por defecto; la promoción a live se gobierna por `ShadowPromoterService` y las métricas declaradas (`PF ≥ 1`, MaxDD < 8%, trades ≥ 40).
5. **Live**: al promover, se aplican límites conservadores por plan (bots, símbolos, canary share, trades/día) y kill-switch preconfigurado.

### Planes y límites

| Plan | Bots | Símbolos | Canary share | Data retention | Alertas |
|------|------|----------|--------------|----------------|---------|
| Starter | 1 | 1 | 10% | 90 días | Email |
| Pro | 5 | 3 | 25% | 365 días | Email + Telegram/Discord |

Las restricciones se almacenan en `tenant_limits` y `LimitsGuardService` bloquea aperturas cuando se excede `max_bots`, `max_symbols`, `max_trades_per_day` o el `canary_share_max` configurado.

### Cuenta, privacidad y portabilidad

- **Eliminar cuenta**: la vista `/ui/tenant/account/delete` requiere confirmación doble, contraseña y TOTP si el usuario tiene MFA. Se registra la solicitud, se marca el tenant con `deletion_requested_at`/`purge_after` y el job `TenantAccountCleanupJob` lo purga físicamente entre 24–72h (respetando las retenciones legales definidas en `saas.legal`).
- **Exportar datos**: el botón `Exportar` genera un token firmado vía `TenantAccountService.createExportToken`. La descarga se sirve desde `/tenant/account/export?token=` con streaming de un ZIP (`trades.csv`, `fills.csv`, `executions.json`, `reports/*.pdf|html`). También se exponen rutas directas `/tenant/{tenantId}/exports/trades.csv`, `/tenant/{tenantId}/exports/fills.csv`, `/tenant/{tenantId}/exports/executions.json`.
- Todas las exportaciones se filtran por tenant directamente en base de datos; no hay filtrado en memoria.
- **Auditoría**: todos los eventos (`solicitud`, `descarga`, `borrado`) quedan registrados en `audit_event` con claves `tenant.account.*`.

Variables relevantes (`application.yml` / entorno):

```yaml
saas:
  legal:
    export-token-ttl-minutes: 15   # Validez de los enlaces de descarga
    deletion-min-hours: 24         # Ventana mínima antes del purge
    deletion-max-hours: 72         # Ventana máxima antes del purge
```

### Límites de seguridad por plan

- El plan Starter incluye límites defensivos: `maxDailyDrawdownPct`, `maxConcurrentPositions`, `maxDailyTrades` y `canaryPct` (10%). Estas columnas viven en `tenant_limits` y las consume `LimitsGuardService`/`RiskGuard`.
- El botón **Pause All** (`/tenant/risk/pause-all`) pausa las nuevas entradas pero permite cerrar posiciones. Requiere confirmación doble + TOTP y actualiza `tenant_settings.trading_paused`.
- El estado se refleja en auditoría (`tenant.trading.paused`/`tenant.trading.resumed`) y en la UI.

### Billing robusto

- Estados soportados: `ACTIVE`, `GRACE`, `PAST_DUE`, `DOWNGRADED` en `TenantBillingEntity`.
- Webhooks (`/api/billing/webhook/{provider}`) son idempotentes con `BillingWebhookEventEntity` (`event_id`, `signature`, `processed_at`). Duplicados se rechazan (HTTP 409) y se registra auditoría.
- Ante fallos de cobro se entra en `GRACE` durante `saas.billing.grace-days`; luego pasa a `PAST_DUE` y se degrada automáticamente al plan Starter/Free (sin bloquear cierres ni exportaciones).
- CLI operativo (`java -jar bot-trading.jar billing ...`):
  - `billing replay-webhook --event-id <id>`: reprocesa un evento.
  - `billing force-state --tenant <uuid> --state ACTIVE|GRACE|PAST_DUE|DOWNGRADED`.
  - `billing history --tenant <uuid>`: muestra transiciones.

### Consentimiento versionado

- Cada activación/modificación de bot exige consentimiento vigente de Términos y Declaración de Riesgo.
- Los hashes/versiones actuales (`saas.legal.terms-version`, `saas.legal.risk-version`) se guardan en `terms_acceptance` junto a `consented_at` e IP. Si falta consentimiento se bloquea la activación (HTTP 412).
- Al actualizar versiones, forzar nuevo consentimiento mostrando los textos en la UI (panel de bots).

### Geo-block y sanciones

- `SanctionsFilter` bloquea peticiones cuando el país de la IP (`GeoLocationService`) o de facturación coinciden con `saas.legal.sanctioned-countries`.
- Las APIs (`/api/**`) devuelven 451, las vistas redirigen a `/blocked` (template `templates/blocked.html`). Todas las denegaciones quedan en auditoría (`geo.blocked`).

```yaml
saas:
  legal:
    sanctioned-countries:
      - CU
      - IR
      - SY
```

### Portal interno y RBAC

- Staff accede vía SSO OAuth2 + MFA (configurar `spring.security.oauth2.client.*`).
- Roles (`StaffRole`): `SUPPORT_READ`, `OPS_ACTIONS`, `FINANCE`. Se aplican a vistas Thymeleaf en `templates/staff/` (tenants, planes, auditoría, pause-all, webhooks, incidentes).
- Toda acción staff se audita y exige MFA confirmado.

### Reportes y exportes recurrentes

- `TenantReportScheduler` genera reporte mensual HTML (Thymeleaf `report/report_monthly_template.html`) y lo convierte a PDF (FlyingSaucer/iText). Los archivos viven bajo `reports/tenant/<tenant>/<yyyy>/<mm>/`.
- Endpoints auxiliares: `/tenant/{tenantId}/exports/trades.csv`, `/tenant/{tenantId}/exports/fills.csv`, `/tenant/{tenantId}/exports/executions.json`.
- `TenantDataExportService` reutiliza lógica para CSV/JSON y ZIP de portabilidad.

### QA y pruebas

Pruebas recomendadas (JUnit 5 + Spring Test):

- `TenantAccountApiIntegrationTest`: validar borrado (soft-delete + auditoría) y exportación (200 OK con token válido, 401/403/404 en casos negativos).
- Billing webhook idempotente: simular eventos duplicados y transición `ACTIVE → GRACE → PAST_DUE → DOWNGRADED`.
- Consentimiento: bloquear activación de bots si faltan hashes vigentes y permitir tras aceptarlos.
- Geo-block: mockear `GeoLocationService` para países sancionados y verificar respuesta 451/redirección.
- Pause all: asegurar que `LimitsGuardService`/`RiskGuard` bloquean nuevas entradas pero permiten cierres.
- Unit tests: generadores CSV/ZIP, firma de webhooks, verificación TOTP.

### Variables de entorno clave

| Variable | Descripción |
|----------|-------------|
| `SAAS_BILLING_GRACE_DAYS` | Días de gracia antes de degradar plan. |
| `SAAS_LEGAL_TERMS_VERSION` | Versionado de Términos para consentimiento. |
| `SAAS_LEGAL_RISK_VERSION` | Versionado de Riesgos para consentimiento. |
| `SAAS_LEGAL_SANCTIONED_COUNTRIES` | Lista separada por comas de países bloqueados. |
| `SAAS_LEGAL_EXPORT_TOKEN_TTL_MINUTES` | TTL de enlaces de exportación. |
| `SAAS_LEGAL_DELETION_MIN_HOURS` / `SAAS_LEGAL_DELETION_MAX_HOURS` | Ventana de purge físico. |

> Ajustar las variables vía `application.yml`, `application-*.yml` o variables de entorno (Spring Boot convierte guiones a camelCase automáticamente).

### Panel cliente (Thymeleaf)

- `/ui/tenant`: tablero con estado LIVE/SHADOW/PAUSED, P&L diario/mensual, MaxDD, VaR/CVaR, bandit share y alertas activas.
- Gestión de bots (`/api/bots/{id}/mode/{SHADOW|LIVE|PAUSED}`) con validación de KPIs antes de promover.
- Límites dinámicos (`/api/bots/{id}/limits`) para VaR, MaxDD y cuota canary.
- Reportes descargables (`/api/reports/monthly?yyyymm=`) generan CSV + PDF en `reports/tenant/<tenant>/<yyyy>/<mm>/`.
- Auditoría filtrable (`/api/audit?from=&to=&type=`) respaldada por la tabla WORM `audit_event`.

### Billing y success fee

- `tenant_billing` registra proveedor (`stripe|mp`), estado de suscripción y High-Water Mark (`hwm_pnl_net`).
- Webhooks idempotentes para `invoice.paid/failed` y `subscription.updated/canceled` controlan el estado del tenant (`ACTIVE`, `SUSPENDED`).
- Success fee opcional (`applySuccessFee`) calcula `(pnl_mes - hwm) · rate` (10–15%), actualiza el HWM y emite auditoría.

### Seguridad y gobernanza

- **RBAC** OWNER/ADMIN/VIEWER con autenticación básica + MFA opcional (`X-TOTP`).
- API keys cifradas (AES-256 GCM via `SecretEncryptionService` + master key KMS) y jamás expuestas en logs.
- `TenantContextFilter` aplica `SET app.current_tenant` por conexión; Row Level Security garantiza aislamiento entre tenants.
- Métricas Micrometer etiquetadas con `{tenant, plan, symbol}` (`risk.daily_pnl`, `var.cvar_q`, `exec.slippage.avg_bps`, `bandit.pull.count`, `anomaly.alerts`, `killswitch.events`).
- Auditoría append-only (`audit_event_no_update` trigger) y tracking de Términos/consentimiento (`terms_acceptance`).

### Runbook & operaciones

- **Kill-switch**: `TradingState.activateKillSwitch()` registra `killswitch.events{tenant,plan}` y pausa aperturas. Para resetear, usar `/api/bots/{id}/mode/PAUSED` o `/admin/incidents`.
- **Billing fallido**: webhook `invoice.failed` → tenant `SUSPENDED`. Restaurar pagando y verificando `tenant_billing.status=paid`.
- **Shadow sin promoción**: revisar `/api/bandit/stats` y reportes shadow (PF, MaxDD, slippage). Ajustar presets o ampliar ventana `shadow_trades_min`.
- **Incidentes 429 / WS down**: `TenantNotificationService` genera alertas email/Telegram/Discord y las registra en `audit_event`. Consultar `risk.api_error_rate` y `anomaly.alerts` etiquetadas por tenant.

### Dependencias Maven

El repositorio incluye un `.mvn/settings.xml` para forzar el mirror `repo1.maven.org`. Si tu entorno corporativo bloquea Maven
Central con respuesta `403`, actualizá esa URL por un mirror accesible o precargá el artefacto `spring-boot-starter-parent` en tu
cache local (`~/.m2/repository`).

## Chaos & Resilience Pack

El bot incorpora un módulo de caos activable en runtime para validar resiliencia sin tocar producción real.

- **ChaosSuite**: inyecta fallas controladas sobre el WebSocket, API REST y reloj local.
- **Fail-safe**: timeout automático (`chaos.safety.max-duration-sec`) y kill-switch remoto.
- **CandleSanitizer**: valida klines, rellena huecos y elimina duplicados antes de pasar al motor.
- **ChaosClock**: aplica `clock.driftMs` sobre el `Clock` global para validar watchdogs dependientes del tiempo.
- **RiskGuard awareness**: añade estado `marketDataStale` para bloquear nuevas entradas cuando se opera en fallback degradado.

### Configuración (`application.yml`)

```yaml
chaos:
  enabled: false
  ws-drop-rate-pct: 0           # % de mensajes WS perdidos/duplicados
  api-burst429-seconds: 0       # duración de ráfagas 429/418
  latency-multiplier: 1.0       # multiplicador de latencia en llamadas REST/WS
  candles-gap-pattern: NONE     # NONE | SKIP_EVERY_10 | RANDOM_1PCT
  clock-drift-ms: 0             # desfase del reloj local
  safety:
    max-duration-sec: 600       # timeout automático del escenario
```

### Endpoints ADMIN

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `POST` | `/admin/chaos/start` | Activa un escenario recibiendo un `ChaosRequest` parcial. |
| `POST` | `/admin/chaos/stop` | Apaga cualquier escenario en curso (kill-switch). |
| `GET` | `/admin/chaos/status` | Reporta estado actual, remaining time y salud del WS. |

### Cómo ejecutar escenarios

```bash
curl -u admin:*** -X POST http://localhost:8080/admin/chaos/start \
  -H 'Content-Type: application/json' \
  -d '{
        "wsDropRatePct": 80,
        "apiBurst429Seconds": 15,
        "latencyMultiplier": 5.0,
        "candlesGapPattern": "SKIP_EVERY_10",
        "clockDriftMs": 2500,
        "maxDurationSec": 120
      }'

# detener el caos
curl -u admin:*** -X POST http://localhost:8080/admin/chaos/stop
```

### Criterios de éxito durante caos

- El bot permanece operativo en modo degradado: cuando el WS cae, `TradingScheduler` conmuta a REST con ritmo limitado (`chaos.allowRestPoll`).
- `RiskGuard` reporta `marketDataStale=true` y bloquea nuevas aperturas mientras los datos estén atrasados.
- No se generan loops infinitos ni explosiones de colas gracias a `CandleSanitizer` y al `Throttle` reutilizado.
- Métricas Micrometer expuestas: `chaos.ws_drops`, `chaos.api_429`, `chaos.latency_mult`, `chaos.active`, `risk.market_data_stale`.

> **Pruebas recomendadas**: ejecutar escenarios extremos (`wsDropRatePct=100`, `latencyMultiplier=10`) y verificar que el bot pausa entradas nuevas, los servicios continúan respondiendo y los contadores se actualizan en Prometheus.

## Gestión intradía de VaR/CVaR y sizing dinámico

- **Modelo de retorno**: cada trade se normaliza en R-múltiplos (`pnl_r`) con slippage observado (`slippage_bps`) proveniente de `vw_trades_enriched` y posiciones shadow. La cola se ajusta vía Cornish-Fisher o bootstrap pesado (`var.heavy_tails`).
- **Sizing con CVaR**: antes de enviar la orden se evalúa `VarAssessment` y se recorta la cantidad hasta que `CVaR_q · stopDistance · qty ≤ cvar_target_pct_per_trade · equity`. Si el presupuesto diario (`cvar_target_pct_per_day`) quedaría excedido se bloquea la entrada.
- **Persistencia y auditoría**: cada evaluación queda en `risk_var_snapshot` con razones (`TRADE_LIMIT`, `DAILY_LIMIT`, fallback de muestras, etc.) y se expone en `/api/var/snapshots`.
- **Integración**: `RiskGuard` y `AllocatorService` consultan el ratio de presupuesto usado para bloquear nuevas aperturas si la suma de CVaR de posiciones abiertas alcanza el límite diario.
- **Micrometer**: gauges `var.cvar_q{symbol,regime,preset}` y `sizing.qty_var_ratio` permiten monitorear el ajuste aplicado; los bloqueos registran `blocks.by_var{reason}`.
- **UI**: el dashboard añade un badge de CVaR con el ratio de corte aplicado y una tabla de snapshots con timestamp, CVaR proyectado, sizing y motivo.

### Configuración (`application.yml`)

```yaml
var:
  enabled: true
  quantile: 0.99
  cvar_target_pct_per_trade: 0.25   # % del capital por trade
  cvar_target_pct_per_day: 1.5      # % del capital por día
  lookback_trades: 250              # muestras símbolo/preset
  min_trades_for_symbol_preset: 80  # umbral antes de usar pool por régimen
  fallback_to_regime_pool: true
  mc_iterations: 20000              # bootstrap Monte Carlo
  heavy_tails: true                 # activa Cornish-Fisher/colas gruesas
```

> **Tip:** ajustar `cvar_target_pct_per_trade`/`per_day` según el capital y la tolerancia diaria. Para backtests con colas extremas, incrementar `lookback_trades` y `mc_iterations` o setear `heavy_tails=false`.

## Gestión de presets y promoción
El bot incorpora un pipeline completo de versionado y promoción de presets por régimen (`UP/DOWN/RANGE`) y `side` (`BUY/SELL`). Cada import del GA crea una `PresetVersion` candidata con trazabilidad a la corrida (`BacktestRun`) y hashes de código/datos/labels para reproducibilidad. El `PresetService` aplica la política de promoción configurable (`PF`, `MaxDD`, trades mínimos y chequeo Shadow) antes de activar un preset en modo **canary** o **full**. El `StrategyRouter` consulta siempre la versión `active` para enrutar la estrategia correspondiente, y los cambios se reflejan en caliente.

### Flujo recomendado
1. **Importar** preset generado por el GA (`/api/presets/import` o CLI `presets import`).
2. **Evaluar** métricas OOS/Shadow en el dashboard o vía `EvaluationSnapshot`.
3. **Promover** a canary (`PF` ≥ baseline, DD ≤ cap, trades ≥ mínimo). Si supera los stages de riesgo (`0.5 → 0.75 → 1.0`) se activa automáticamente.
4. **Snapshot** periódico (`/api/presets/{id}/snapshot-live`) para registrar KPIs live/shadow.
5. **Leaderboard** (`/ui/presets/leaderboard` o CLI `leaderboard`) para comparar candidatos y activos.
6. **Rollback** inmediato a la versión activa previa si los KPIs live rompen los límites.

### API clave (rol `ADMIN`)
| Método | Endpoint | Descripción |
|--------|----------|-------------|
| `GET` | `/api/presets?regime=&side=&status=` | Lista versiones filtradas con paginado opcional. |
| `POST` | `/api/presets/import` | Multipart (`params`, `signals`, `metrics`) + metadatos `runId`, etc. Crea `candidate`. |
| `POST` | `/api/presets/{id}/activate?mode=canary|full` | Aplica política y activa versión. |
| `POST` | `/api/presets/{id}/retire` | Marca `retired` y registra auditoría. |
| `POST` | `/api/presets/{id}/rollback` | Reactiva la última versión `active` conocida. |
| `GET` | `/api/leaderboard?regime=&window=&minTrades=&maxDD=` | Ranking OOS/Shadow/Live filtrable. |
| `GET` | `/api/presets/{id}/snapshots` | Historial de `EvaluationSnapshot`. |
| `POST` | `/api/presets/{id}/snapshot-live?window=30d` | Registra KPIs live/shadow en JSON. |

### CLI (Picocli)
Los comandos se ejecutan vía `java -jar bot-trading.jar <command>`:

```bash
java -jar bot-trading.jar presets import \
  --run-id RUN_GA_20241015 --regime UP --side BUY \
  --params ./preset_up_buy.yaml --metrics ./metrics_oos.json
java -jar bot-trading.jar presets promote --preset-id 4d3d... --mode canary
java -jar bot-trading.jar presets retire --preset-id 4d3d...
java -jar bot-trading.jar presets rollback --regime UP --side BUY
java -jar bot-trading.jar presets snapshot-live --preset-id 4d3d... --window 30d --live ./live.json
java -jar bot-trading.jar leaderboard --regime RANGE --window OOS_90D --min-trades 150 --maxdd 8
```

### Ejemplos `curl`
```bash
curl -u admin:*** -F regime=UP -F side=BUY \
  -F params=@preset_up_buy.yaml -F metrics=@metrics_oos.json \
  http://localhost:8080/api/presets/import
curl -u admin:*** -X POST \
  "http://localhost:8080/api/presets/{id}/activate?mode=canary"
curl -u admin:*** -X POST \
  "http://localhost:8080/api/presets/{id}/snapshot-live?window=30d" \
  -H 'Content-Type: application/json' \
  -d '{"liveMetrics":{"PF":1.8,"Trades":60}}'
```

### Leaderboard y dashboard
La vista `/ui/presets/leaderboard` permite comparar presets por régimen/ventana, aplicar filtros (`minTrades`, `maxDD`) y ejecutar acciones (Activate/Canary/Retire/Rollback). El detalle `/ui/presets/{id}` muestra snapshots OOS/Shadow/Live, tracking en vivo (PF, MaxDD, trades, slippage) y los JSON/YAML importados para auditoría rápida.

### Persistencia
Las migraciones `V7__preset_versioning.sql` y `V8__leaderboard_views.sql` crean las tablas `preset_versions`, `backtest_runs`, `evaluation_snapshots`, `live_tracking` e índices por `regime/side/status`. Los hashes (`code_sha`, `data_hash`, `labels_hash`) habilitan reproducibilidad y auditoría completa.

## Meta-selector online con multi-armed bandits

El motor de decisión incorpora un **meta-selector online** que elige, en cada vela, qué `PresetVersion` ejecutar para la tupla `(symbol, regime, side)` utilizando algoritmos de multi-armed bandits (Thompson Sampling, UCB1 o UCB-Tuned). Cada preset se modela como un **arm** almacenado en `bandit_arm` con sus estadísticas exponencialmente decaídas (`stats_json`) y cada trade/pull se registra en `bandit_pull` junto al contexto (`trend`, `atrPct`, `adx`, `hourOfDay`, `spread/slippage` estimados, etc.).

- **Reward en R-multiple**: `reward = clip(pnlR, ±cap_r) - α·slippage_bps - β·fees_bps` (configurable vía `bandit.reward.*`).
- **Canary budget**: límites diarios/semanales (`max_trades_per_day`, `max_share_pct_per_day`) para evitar que los candidatos dominen el flujo. El selector degrada a fallback cuando se agota el presupuesto.
- **Guardas de riesgo**: respeta `RiskGuard`, arms bloqueados y `min_samples_to_compete` antes de permitir que un candidato compita.
- **Persistencia & rehidratación**: Flyway `V11__bandit_tables.sql` crea las tablas e índices; al reiniciar se recupera el estado sin perder memoria histórica.
- **Métricas Micrometer**: `bandit.pull.count`, `bandit.reward.avg`, `bandit.canary.share`, `bandit.blocked.count` y `bandit.algorithm` para monitoreo/prometheus.
- **Configuración**: en `application.yml` (`bandit.enabled`, `bandit.algorithm`, `bandit.decay.half_life_days`, penalizaciones, etc.).
- **APIs (rol ADMIN)**: `GET /api/bandit/arms`, `POST /api/bandit/arms/{id}/block|unblock`, `POST /api/bandit/reset?confirm=true`, `GET /api/bandit/pulls`, `GET /api/bandit/overview`.
- **UI**: el dashboard (`/ui/dashboard`) añade el panel **Bandit** con ranking de presets (pulls, reward promedio, CI/UCB) y últimas decisiones con trazabilidad (`decisionId`).
- **Selector**: `BanditSelector.pickPresetOrFallback(...)` integra con `StrategyService` y devuelve el preset elegido o fallback router; `BanditSelector.update(decisionId, reward)` actualiza el arm al cerrar un trade.

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
- `TRADING_SYMBOLS`, `ROUTER_ENABLED`, `ALLOCATOR_*`, `DRIFT_*`, `HEALTH_*`, `TCA_*` para activar el router por régimen, el asignador multi-activo, el watchdog de deriva/salud y el módulo de TCA.
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
| GET | `/api/regime/status?symbol=BTCUSDT` | `VIEWER` | Estado actual del Regime Engine (trend/vol, ATR/ADX normalizados e historial reciente). |
| GET | `/api/status/overview?symbol=BTCUSDT` | `VIEWER` | Resumen live con allocator, drift watchdog, health y modo de trading. |
| GET | `/api/tca/slippage?symbol=BTCUSDT&from=&to=` | `VIEWER` | Métricas TCA: slippage promedio (bps), colas y agregados por hora/tipo de orden. |
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
| GET | `/admin/drift/status` | `VIEWER` | Estado del watchdog (stage, sizing, métricas live/shadow). |
| POST | `/admin/drift/reset` | `ADMIN` | Limpia ventanas live/shadow y vuelve a modo LIVE. |
| POST | `/admin/mode/{live|shadow|pause}` | `ADMIN` | Fuerza modo de operación (Live/Shadow/Pause). |
| POST | `/admin/scheduler/enable` | `ADMIN` | Habilita el scheduler de velas. |
| POST | `/admin/scheduler/disable` | `ADMIN` | Deshabilita el scheduler (modo mantenimiento). |
| GET | `/admin/scheduler/status` | `ADMIN` | Devuelve modo (`ws`/`polling`), última `decisionKey`, flags y cooldown restante. |
| GET | `/admin/live-enabled` | `ADMIN` | Consulta estado `liveEnabled`. |
| GET | `/api/strategy/decide?symbol=BTCUSDT` | `READ` | Evalúa la estrategia compuesta y devuelve `side`, `confidence` y notas. |
| GET | `/api/positions/open` | `READ` | Lista posiciones abiertas paginadas. |
| GET | `/api/positions/{id}` | `READ` | Recupera el detalle de una posición. |
| POST | `/admin/positions/{id}/close` | `ADMIN` | Cierra una posición y cancela órdenes gestionadas. |
| POST | `/admin/reconcile` | `ADMIN` | Fuerza reconciliación contra Binance. |
| GET | `/api/bandit/arms?symbol=&regime=&side=` | `ADMIN` | Lista arms activos/candidatos con estadísticas. |
| GET | `/api/bandit/pulls?symbol=&regime=&side=&limit=` | `ADMIN` | Últimos pulls con reward/contexto. |
| GET | `/api/bandit/overview?symbol=` | `ADMIN` | Share canary y algoritmo activo. |
| POST | `/api/bandit/arms/{id}/block` | `ADMIN` | Bloquea un preset para exclusión inmediata. |
| POST | `/api/bandit/arms/{id}/unblock` | `ADMIN` | Rehabilita un preset. |
| POST | `/api/bandit/reset?symbol=&regime=&side=&confirm=true` | `ADMIN` | Reinicia estadísticas decaídas del bandit. |

## Panel Web de Trading

La ruta `GET /ui/dashboard` (rol `VIEWER`) habilita un panel completo construido con **Thymeleaf + Chart.js Financial**.

- **Filtros persistentes:** símbolo, intervalo, rango de fechas (`from/to` en la URL), side y estado de la posición.
- **Gráfico principal:** velas con overlays seleccionables (VWAP diario, VWAP anclado, ATR Bands, Supertrend) y volumen opcional.
- **Marcadores avanzados:** BUY/SELL, SL, TP, BE y Trailing con tooltip (PnL, fee, slippage en bps, nota de decisión).
- **Curvas sincronizadas:** equity y drawdown reaccionan al crosshair del gráfico de precio.
- **Heatmap PnL:** vista 7×24 coloreada según net PnL/win rate, exportable CSV.
- **Cinta de régimen:** barra de colores en la parte superior del gráfico con el histórico reciente (UP/DOWN/RANGE y volatilidad HI/LO) y leyenda con ATR/ADX normalizados.
- **Badges en vivo:** allocator, drift watchdog, health y modo de trading muestran estado, razón y sizing aplicado en tiempo real.
- **Panel TCA:** promedio de slippage en bps, colas (ms) y tabla por hora/tipo alimentada con fills live/shadow.
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
- En producción, `/actuator/prometheus` queda expuesto si `PROMETHEUS_ENABLED=true` y `MANAGEMENT_ENDPOINTS_INCLUDE` incluye el endpoint (por defecto `health,info,metrics,prometheus`).
- Para endurecer el scrape configura una allowlist (`PROMETHEUS_ALLOWLIST=10.0.0.0/8,192.168.0.0/16`) o un token (`PROMETHEUS_TOKEN=super-secreto`) que Prometheus debe enviar en `X-Prometheus-Token`.
- Para deshabilitar la exposición establece `PROMETHEUS_ENABLED=false` y actualiza `MANAGEMENT_ENDPOINTS_INCLUDE=health,info,metrics`.
- Salud en `/actuator/health` (sin autenticación).
- Contadores: `scheduler.candle.decisions{result=BUY|SELL|FLAT|SKIPPED,...}`, `orders.sent`, `orders.filled`, `strategy.signals`, `risk.stopouts`.
- Temporizador: `scheduler.candle.duration.ms`.
- Contadores adicionales: `router.selections{preset}`, `allocator.opens.allowed/blocked`, `drift.downgrades`, `health.pauses`, `tca.samples{symbol,type}`.
- Temporizador: `scheduler.candle.duration.ms`.
- Gauges: `risk.drawdown`, `risk.equity`, `regime.time_share{symbol,type,value}`, `tca.slippage.avg_bps{symbol}`, `drift.sizing.multiplier`, `health.status`.

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
- Nuevas unitarias para `RegimeEngine`, `StrategyRouter`, `AllocatorService`, `DriftWatchdog` y `TcaService`.
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
