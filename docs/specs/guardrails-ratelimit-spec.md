# Spec — guardrails-ratelimit

> Módulo 5 según ARCHITECTURE.md §6. Autor: spec-architect.
> Paquete raíz: `io.github.tikyparkinson.mcpguardrails.ratelimit`.
> Prerequisito cumplido: `guardrails-injection-guard-DONE.md` aprobado.

## 1. Problema y alcance

Un agente MCP descontrolado (bucle, bug o abuso deliberado) puede machacar una tool cara o
peligrosa cientos de veces por minuto. `guardrails-ratelimit` limita la frecuencia de
invocación por par `(agente, tool)` con ventana fija (fixed window): configurable
`max-invocations` por `window`. El conteo vive detrás de `RateLimitStorePort`
(ARCHITECTURE.md §4): default in-memory y referencia JDBC/Postgres probada con Testcontainers.
Al exceder el límite, `Deny` + evento en el bus de auditoría.

**No-goals:**
- No implementa sliding window, token bucket ni burst allowance: fixed window es predecible,
  barato y suficiente como primera línea. Algoritmos más finos = otra implementación del puerto.
- No hay límites por agente-global ni por tool-global: solo por par `(agente, tool)`, un único
  límite uniforme configurado. Límites por-tool diferenciados serían scope creep (se haría vía
  otra implementación de `RateLimitStorePort`/política futura).
- No hace throttling/cola/espera: decide Allow/Deny, no retrasa.
- No limpia ventanas viejas en el adaptador JDBC (housekeeping del operador; el DDL incluye
  índice por ventana para facilitar el DELETE periódico). El in-memory sí auto-desaloja.

## 2. Modelo de dominio

Paquete `io.github.tikyparkinson.mcpguardrails.ratelimit.domain`. JDK puro, autocontenido.

```java
/**
 * Política de rate limiting con ventana fija.
 * Invariantes: maxInvocations >= 1; window no null, positiva y no cero.
 */
record RateLimitPolicy(int maxInvocations, Duration window) {
  /**
   * Inicio de la ventana fija que contiene el instante dado:
   * epochMillis - (epochMillis % window.toMillis()).
   */
  Instant windowStartFor(Instant occurredAt) { ... }

  /** true si count (conteo ya incluida la invocación actual) excede maxInvocations. */
  boolean exceededBy(long count) { return count > maxInvocations; }
}

/**
 * Resultado de la comprobación.
 * Invariantes: count >= 1; policy no null.
 * Derivado: allowed() == !policy.exceededBy(count).
 */
record RateLimitStatus(long count, RateLimitPolicy policy) {
  boolean allowed() { ... }
}
```

## 3. Puertos (contratos de application)

Paquete `io.github.tikyparkinson.mcpguardrails.ratelimit.application.port`.

### 3.1 `CheckRateLimitUseCase` — puerto de entrada

- Capa: `application.port.in`. Lo invoca el adapter-in (`RateLimitGuardrail`).
- Lo implementa: `CheckRateLimitService` (application).

```java
public interface CheckRateLimitUseCase {
  /** Registra la invocación en su ventana y devuelve el estado resultante. Nunca null. */
  RateLimitStatus check(String agentId, String toolName, Instant occurredAt);
}
```

### 3.2 `RateLimitStorePort` — puerto de salida

- Capa: `application.port.out`.
- Lo implementan: `InMemoryRateLimitStoreAdapter` (default) y `JdbcRateLimitStoreAdapter`
  (referencia Testcontainers).

```java
public interface RateLimitStorePort {
  /**
   * Incrementa atómicamente el contador de (agentId, toolName, windowStart) y devuelve el
   * valor resultante (>= 1, incluye esta invocación). Lanza RuntimeException si el store falla.
   */
  long incrementAndCount(String agentId, String toolName, Instant windowStart);
}
```

## 4. Caso de uso — `CheckRateLimitService`

Clase en `ratelimit.application.usecase`, implementa `CheckRateLimitUseCase`. Constructor:
`(RateLimitStorePort store, RateLimitPolicy policy)`.

1. Valida `agentId`/`toolName` no null ni blank; `occurredAt` no null.
2. `windowStart = policy.windowStartFor(occurredAt)` (dominio).
3. `count = store.incrementAndCount(agentId, toolName, windowStart)` — fallo del store se
   propaga (fail-closed en core, coherente con los módulos anteriores).
4. Devuelve `new RateLimitStatus(count, policy)`.

Nota: el contador se incrementa aunque el resultado sea Deny — los intentos rechazados también
consumen cuota (anti-martilleo). Decisión 3.

## 5. Adaptadores esperados

### Adapter-in: `RateLimitGuardrail`

Paquete `ratelimit.adapter.in.chain`. Implementa `Guardrail` de core.

- `name()` = `"ratelimit"`; `order()` = `100` (último de los cuatro).
- `evaluate(context)`:
  1. `status = useCase.check(agentId, toolName, context.occurredAt())`.
  2. `status.allowed()` ⇒ `Allow`, sin evento (mismo criterio anti-ruido que injection-guard).
  3. Excedido ⇒ registra en bus (`emittedBy="ratelimit"`, tipo `DECISION_DENY`,
     `detail = "count=<n> limit=<max> window=<iso>"`) y devuelve
     `Deny("rate limit exceeded for agent '<a>' on tool '<t>' (<n>/<max> in <window>)")`.
  - Fallo del bus/store ⇒ propaga.

### Adapters-out

Paquete `ratelimit.adapter.out.persistence`.

1. **`InMemoryRateLimitStoreAdapter`** (default): `ConcurrentHashMap<WindowKey, AtomicLong>`
   (`WindowKey` = record privado agentId+toolName+windowStart). Auto-desalojo: al tocar una
   ventana nueva para una key, elimina entradas de ventanas anteriores a la actual (barrido
   barato al incrementar; sin hilos de limpieza). El starter lo registra
   `@ConditionalOnMissingBean`.
2. **`JdbcRateLimitStoreAdapter`** (referencia): `JdbcClient` contra tabla
   `mcp_rate_limit_counter` con upsert atómico PostgreSQL:

   ```sql
   INSERT INTO mcp_rate_limit_counter (agent_id, tool_name, window_start, invocation_count)
   VALUES (:agentId, :toolName, :windowStart, 1)
   ON CONFLICT (agent_id, tool_name, window_start)
   DO UPDATE SET invocation_count = mcp_rate_limit_counter.invocation_count + 1
   RETURNING invocation_count;
   ```

   DDL de referencia en `src/main/resources/mcp-guardrails-ratelimit-schema.sql`:

   ```sql
   CREATE TABLE IF NOT EXISTS mcp_rate_limit_counter (
     agent_id         VARCHAR(255) NOT NULL,
     tool_name        VARCHAR(255) NOT NULL,
     window_start     TIMESTAMPTZ  NOT NULL,
     invocation_count BIGINT       NOT NULL,
     PRIMARY KEY (agent_id, tool_name, window_start)
   );
   CREATE INDEX IF NOT EXISTS idx_mcp_rate_limit_window
     ON mcp_rate_limit_counter (window_start);
   ```

   Probado **obligatoriamente con Testcontainers/PostgreSQL** (ARCHITECTURE.md §8), incluida la
   atomicidad bajo concurrencia (invocaciones paralelas no pierden incrementos).

## 6. Configuración Spring Boot

Clase `GuardrailsRatelimitProperties` en `ratelimit.infrastructure`.

| Propiedad | Tipo | Default | Significado |
|---|---|---|---|
| `mcp.guardrails.ratelimit.enabled` | boolean | `true` | Activa/desactiva el guardrail. |
| `mcp.guardrails.ratelimit.max-invocations` | int | `60` | Invocaciones permitidas por ventana por (agente, tool). |
| `mcp.guardrails.ratelimit.window` | `Duration` | `PT1M` | Tamaño de la ventana fija. |

Expone `toPolicy()` → `RateLimitPolicy` para el starter.

## 7. Dependencias Maven propuestas

Sin artefactos nuevos: idéntico patrón a guardrails-audit (todas las versiones ya verificadas
2026-07-24: Testcontainers BOM 2.0.5 en el parent; el driver Postgres se fija en el pom raíz a
42.7.13 desde 2026-07-26, por delante del BOM de Boot, que resuelve la 42.7.11 vulnerable).

| Dependencia | Scope | Justificación |
|---|---|---|
| `io.github.tikyparkinson:guardrails-core` | compile | SPI `Guardrail` y tipos de decisión para el adapter-in. |
| `io.github.tikyparkinson:guardrails-audit` | compile | Bus de auditoría para registrar los Deny por límite excedido. |
| `org.springframework.boot:spring-boot` (BOM) | provided | `@ConfigurationProperties` en `infrastructure`. |
| `org.springframework:spring-jdbc` (BOM Boot) | provided | `JdbcClient` del adaptador JDBC de referencia. |
| `org.junit.jupiter:junit-jupiter` (BOM) | test | Framework de tests. |
| `org.mockito:mockito-core` | test | Mocks del puerto store y del bus en tests unitarios. |
| `org.testcontainers:testcontainers-postgresql` (BOM TC) | test | Contenedor PostgreSQL para el test del adaptador JDBC (obligatorio §8). |
| `org.testcontainers:testcontainers-junit-jupiter` (BOM TC) | test | Integración `@Testcontainers` con JUnit. |
| `org.postgresql:postgresql` (fijado en el pom raíz: 42.7.13) | test | Driver JDBC real para el test con Testcontainers. Se fija por delante del BOM de Boot por CVE-2026-54291 (ver spec de audit §7). |

## 8. Diagrama del hexágono

```
        GuardrailChain (guardrails-core)
                 │  Guardrail SPI
    ┌────────────▼─────────────┐        bus de auditoría (solo Deny)
    │ adapter.in.chain         │──────► RecordAuditEventUseCase (guardrails-audit)
    │ RateLimitGuardrail       │
    │ allowed→Allow,           │
    │ exceeded→Deny            │
    └────────────┬─────────────┘
                 │ CheckRateLimitUseCase (port.in)
    ┌────────────▼──────────────┐
    │ application               │
    │ CheckRateLimitService     │──── RateLimitStorePort (port.out)
    └────────────┬──────────────┘              │
                 │                 ┌───────────┴───────────────────┐
    ┌────────────▼─────────────┐   │ adapter.out.persistence       │
    │ domain                   │   │ InMemoryRateLimitStore (def,  │
    │ RateLimitPolicy (ventana │   │  auto-desalojo)               │
    │ fija), RateLimitStatus   │   │ JdbcRateLimitStore (Postgres  │
    └──────────────────────────┘   │  upsert atómico, Testcont.)   │
                                   └───────────────────────────────┘
```

## 9. Decisiones de diseño

1. **Fixed window**: predecible y trivial de razonar/almacenar (una fila por ventana). El borde
   de ventana permite hasta 2×max en instantes contiguos en el peor caso teórico; aceptable
   para una primera línea de defensa y documentado aquí.
2. **Límite por par `(agente, tool)`** con política única global: el caso de uso recibe la
   política por constructor; granularidad por-tool sería configuración especulativa hoy.
3. **Los intentos denegados consumen cuota** (`incrementAndCount` siempre incrementa): evita
   que un cliente martillee esperando el reset exacto de la ventana.
4. **Solo se audita el Deny**: mismo criterio anti-ruido que injection-guard (decisión 2 de ese
   spec); el TOOL_INVOKED ya existe por audit.
5. **Upsert atómico con `RETURNING`** en Postgres: una sola sentencia, sin race conditions ni
   transacciones explícitas; la atomicidad se verifica con test concurrente en Testcontainers.
6. **In-memory con auto-desalojo de ventanas viejas al incrementar**: sin scheduler propio
   (ARCHITECTURE.md §7 prohíbe estado estático/singletons manuales; un hilo de limpieza sería
   ciclo de vida que le corresponde a Spring, no a la librería).

---
Estado: **PENDIENTE de aprobación por code-reviewer al final del ciclo del módulo.**
