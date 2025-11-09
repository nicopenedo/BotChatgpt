# Test Suite

## Prerequisitos
- Java 21
- Maven 3.9+
- Docker en ejecución para los tests que usan Testcontainers (solo necesarios cuando se ejecuta el perfil `tc`).

## Comandos principales
- `mvn -U -DskipITs -Dstyle.color=never test` ejecuta la suite unitaria completa usando el perfil `test` por defecto.
- `mvn -U -DskipTests package` construye el paquete sin ejecutar pruebas.
- Para pruebas que requieren Postgres real habilita el perfil `tc` y ejecuta `mvn -DskipITs -Ptc test` (Docker necesario).

## Perfiles de Spring
- Los tests usan `@ActiveProfiles("test")` por defecto con la configuración declarada en `src/test/resources/application-test.yml`.

## Notas
- Las métricas Micrometer se instrumentan con `SimpleMeterRegistry` en las pruebas unitarias.
- WireMock se usa para simular integraciones HTTP (p. ej. Binance). Asegúrate de que los puertos dinámicos estén libres.
