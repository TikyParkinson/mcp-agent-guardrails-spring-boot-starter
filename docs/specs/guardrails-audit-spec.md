# Spec — guardrails-audit

> Módulo 2 según ARCHITECTURE.md §6. Autor: spec-architect.
> Paquete raíz: `io.github.tikyparkinson.mcpguardrails.audit`.
> Prerequisito cumplido: `guardrails-core-DONE.md` aprobado.

## 1. Problema y alcance

Nadie sabe hoy qué tools invocan los agentes MCP ni con qué resultado: no hay rastro para
forense, compliance ni debugging. `guardrails-audit` registra cada invocación de tool como un
`AuditEvent` inmutable en un store plugable, y expone el caso de uso `RecordAuditEventUseCase`
como **bus de auditoría** que los guardrails posteriores (authz, injection-guard, ratelimit)
usarán para registrar sus propias decisiones (ARCHITECTURE.md §6: "los demás guardrails
registran eventos en el bus de auditoría"). Incluye un guardrail observador (`audit`) que
registra `TOOL_INVOKED` por cada llamada y nunca bloquea por sí mismo.

**No-goals:**
- No decide nada: el guardrail de audit siempre devuelve `Allow`. Autorización, detección de
  inyección y rate limiting son de los módulos 3-5.
- No registra los **argumentos** de la tool (pueden contener secretos/PII). Solo metadatos:
  agente, tool, instante, tipo de evento, detalle textual. Ver Decisión de diseño 2.
- No expone API de consulta rica (filtros, paginación, retención). Solo `findRecent(limit)`
  para verificar el contrato del puerto; una query API real sería scope creep.
- No incluye autoconfiguración `@AutoConfiguration` (vive en `spring-boot-starter`, módulo 6);
  aquí solo la clase `@ConfigurationProperties`.
- No implementa alerting ni export (SIEM, OTLP); el puerto `AuditLogStorePort` es el punto de
  extensión para eso.

## 2. Modelo de dominio

Paquete `io.github.tikyparkinson.mcpguardrails.audit.domain`. **JDK puro** — no importa nada de
`guardrails-core` (la regla de ARCHITECTURE.md §3 exige dominio autocontenido; el mapeo desde
los tipos de core ocurre en `application`/`adapter`).

```java
/** Tipo cerrado de evento de auditoría. */
enum AuditEventType { TOOL_INVOKED, DECISION_ALLOW, DECISION_DENY, DECISION_ESCALATE }

/**
 * Evento de auditoría inmutable.
 * Invariantes: ningún campo null; agentId, toolName y emittedBy no blank;
 * detail no null (puede ser cadena vacía).
 */
record AuditEvent(
    UUID eventId,
    String agentId,
    String toolName,
    Instant occurredAt,
    String emittedBy,        // guardrail que emite: "audit", "authz", ...
    AuditEventType type,
    String detail) {}

/**
 * Borrador de evento: lo que aporta el llamante; eventId y occurredAt los pone el caso de uso.
 * Invariantes: idénticas a AuditEvent para los campos que comparte.
 */
record NewAuditEvent(String agentId, String toolName, String emittedBy,
    AuditEventType type, String detail) {}
```

## 3. Puertos (contratos de application)

Paquete `io.github.tikyparkinson.mcpguardrails.audit.application.port`.

### 3.1 `RecordAuditEventUseCase` — puerto de entrada (el "bus de auditoría")

- Capa: `application.port.in`.
- Lo invocan: el adapter-in de este módulo (`AuditGuardrail`) y, en módulos futuros, los
  guardrails de authz/injection/ratelimit para registrar sus decisiones.
- Lo implementa: `RecordAuditEventService` (application).

```java
public interface RecordAuditEventUseCase {
  /** Completa el borrador (eventId, occurredAt) y lo persiste. Devuelve el evento final. */
  AuditEvent record(NewAuditEvent draft);
}
```

### 3.2 `AuditLogStorePort` — puerto de salida

- Capa: `application.port.out`.
- Lo implementan: `InMemoryAuditLogStoreAdapter` (default) y `JdbcAuditLogStoreAdapter`
  (referencia, adapter-out).

```java
public interface AuditLogStorePort {
  /** Persiste el evento. Lanza RuntimeException si el store falla (no traga errores). */
  void append(AuditEvent event);

  /** Últimos {@code limit} eventos, más reciente primero. limit >= 1. Nunca null. */
  List<AuditEvent> findRecent(int limit);
}
```

## 4. Caso de uso — `RecordAuditEventService`

Clase en `audit.application.usecase`, implementa `RecordAuditEventUseCase`. Constructor:
`(AuditLogStorePort store, Clock clock, Supplier<UUID> idGenerator)` — `Clock` y `Supplier<UUID>`
son JDK puro, inyectados para testabilidad determinista.

1. Valida `draft` no null (las invariantes de campo las garantiza el record).
2. Construye `AuditEvent(idGenerator.get(), draft.agentId(), draft.toolName(),
   clock.instant(), draft.emittedBy(), draft.type(), draft.detail())`.
3. `store.append(event)` — si el store lanza, la excepción **se propaga** (no se traga).
4. Devuelve el evento persistido.

No devuelve `GuardrailDecision`: este caso de uso es el bus. La decisión la aporta el
adapter-in (§5): el guardrail `audit` siempre responde `Allow` tras registrar.

## 5. Adaptadores esperados

### Adapter-in: `AuditGuardrail`

Paquete `audit.adapter.in.chain`. Implementa `Guardrail` de guardrails-core (aquí sí se importa
core: los adapters pueden depender de contratos externos).

- `name()` = `"audit"`; `order()` = `-100` (primero en el trace, ver Decisión 3).
- `evaluate(context)`:
  1. Construye `NewAuditEvent(context.agentId().value(), context.toolName().value(),
     "audit", TOOL_INVOKED, "")`.
  2. Invoca `RecordAuditEventUseCase.record(...)`.
  3. Devuelve `new Allow()`.
  - Si el store falla, la excepción se propaga y `GuardrailChain` (core) la convierte en
    `Deny` fail-closed. Ver Decisión 1.

### Adapters-out

Paquete `audit.adapter.out.persistence`.

1. **`InMemoryAuditLogStoreAdapter`** (default): buffer acotado thread-safe
   (`ArrayDeque` + `synchronized`, o `ConcurrentLinkedDeque` + contador), capacidad máxima
   configurable (default 1000), desaloja el más antiguo al superarla. Sin Testcontainers
   (no hay store real). El starter lo registrará `@ConditionalOnMissingBean`.
2. **`JdbcAuditLogStoreAdapter`** (referencia): usa `JdbcClient` de spring-jdbc contra la tabla
   `mcp_audit_log`. DDL de referencia en `src/main/resources/mcp-guardrails-audit-schema.sql`:

   ```sql
   CREATE TABLE IF NOT EXISTS mcp_audit_log (
     event_id    UUID PRIMARY KEY,
     agent_id    VARCHAR(255) NOT NULL,
     tool_name   VARCHAR(255) NOT NULL,
     occurred_at TIMESTAMPTZ  NOT NULL,
     emitted_by  VARCHAR(64)  NOT NULL,
     event_type  VARCHAR(32)  NOT NULL,
     detail      TEXT         NOT NULL
   );
   ```

   Probado **obligatoriamente con Testcontainers/PostgreSQL** (ARCHITECTURE.md §8). El usuario
   sustituye cualquiera de los dos implementando `AuditLogStorePort` y exponiendo su bean.

## 6. Configuración Spring Boot

Clase `GuardrailsAuditProperties` en `audit.infrastructure` (registro en el starter, módulo 6).

| Propiedad | Tipo | Default | Significado |
|---|---|---|---|
| `mcp.guardrails.audit.enabled` | boolean | `true` | Activa/desactiva el guardrail de auditoría. |
| `mcp.guardrails.audit.in-memory-max-events` | int | `1000` | Capacidad del buffer del adaptador in-memory. |

## 7. Dependencias Maven propuestas

Versiones GA verificadas en Maven Central el 2026-07-24. **Cambio en el parent pom**: añadir
import de `org.testcontainers:testcontainers-bom:2.0.5` (antes de `spring-boot-dependencies`,
regla de orden de BOMs ya establecida; coincide con la versión que gestiona Boot 4.1.0).
Testcontainers 2.x renombró artefactos: `testcontainers-postgresql` y
`testcontainers-junit-jupiter` (verificado contra el BOM 2.0.5).

| Dependencia | Scope | Justificación |
|---|---|---|
| `io.github.tikyparkinson:guardrails-core` | compile | SPI `Guardrail`, `ToolInvocationContext`, `Allow` que consume el adapter-in. |
| `org.springframework.boot:spring-boot` (BOM) | provided | `@ConfigurationProperties` en `infrastructure`. |
| `org.springframework:spring-jdbc` (BOM Boot) | provided | `JdbcClient` del adaptador JDBC de referencia; lo aporta la app del usuario si usa ese adaptador. |
| `org.junit.jupiter:junit-jupiter` (BOM) | test | Framework de tests. |
| `org.mockito:mockito-core` (5.23.0) | test | Mocks del puerto `out` en tests del caso de uso. |
| `org.testcontainers:testcontainers-postgresql` (BOM TC) | test | Contenedor PostgreSQL para el test de integración del adaptador JDBC (obligatorio §8). |
| `org.testcontainers:testcontainers-junit-jupiter` (BOM TC) | test | Integración `@Testcontainers`/`@Container` con JUnit. |
| `org.postgresql:postgresql` (BOM Boot: 42.7.11) | test | Driver JDBC real para el test con Testcontainers. |

Ninguna otra. Nota: el test de Testcontainers usa `JdbcClient` sobre un `DataSource` simple
(`PGSimpleDataSource` del driver), sin `@SpringBootTest`.

## 8. Diagrama del hexágono

```
        GuardrailChain (guardrails-core)          guardrails futuros (authz, ...)
                 │  Guardrail SPI                          │
    ┌────────────▼─────────────┐                           │
    │ adapter.in.chain         │                           │
    │ AuditGuardrail           │                           │
    │ (TOOL_INVOKED, Allow)    │                           │
    └────────────┬─────────────┘                           │
                 │ RecordAuditEventUseCase (port.in) ◄─────┘
    ┌────────────▼─────────────┐
    │ application              │
    │ RecordAuditEventService  │──── AuditLogStorePort (port.out)
    └────────────┬─────────────┘              │
                 │                ┌───────────┴─────────────────┐
    ┌────────────▼─────────────┐  │ adapter.out.persistence     │
    │ domain                   │  │ InMemoryAuditLogStore (def) │
    │ AuditEvent, NewAuditEvent│  │ JdbcAuditLogStore (Postgres,│
    │ AuditEventType           │  │  Testcontainers)            │
    └──────────────────────────┘  └─────────────────────────────┘
```

## 9. Decisiones de diseño

1. **Fail-closed también para auditoría**: si el store de audit está caído, la excepción sube y
   core la convierte en `Deny`. Un sistema de guardrails que sigue operando sin rastro de
   auditoría es un agujero de compliance; coherente con la Decisión 2 de core.
2. **No se persisten argumentos de la tool**: riesgo de secretos/PII en el log. `detail` queda
   como texto corto controlado por el emisor. Si un módulo futuro necesita más, se ampliará vía
   spec, no colando datos crudos.
3. **`order() = -100`**: audit aparece primero en el trace de cada `ChainVerdict`. Como la
   cadena no hace short-circuit, la posición no cambia el resultado, solo la legibilidad.
4. **Dominio de audit no importa tipos de core**: ARCHITECTURE.md §3 exige dominio JDK-puro
   estricto. El puente core→audit se hace en el adapter-in (`AuditGuardrail`), que sí conoce
   ambos mundos.
5. **`Clock` y `Supplier<UUID>` inyectados en el caso de uso**: JDK puro, sin puerto ceremonial,
   tests deterministas.
6. **`spring-jdbc` en scope `provided`**: solo lo necesita quien use el adaptador JDBC de
   referencia; el default in-memory funciona sin él.

---
Estado: **PENDIENTE de aprobación por code-reviewer al final del ciclo del módulo.**
