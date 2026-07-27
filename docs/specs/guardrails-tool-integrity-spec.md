# Spec — guardrails-tool-integrity

> Módulo 7 según ARCHITECTURE.md §6 (primero de la segunda tanda). Autor: spec-architect.
> Paquete raíz: `io.github.tikyparkinson.mcpguardrails.toolintegrity`.
> Prerequisitos cumplidos: los 6 módulos de la v0.1.0 tienen DONE.md; rama
> `feature/guardrails-tool-integrity` creada desde `develop` (flujo §6).

## 1. Problema y alcance

**Tool poisoning / rug-pull de definición**: un atacante (o una versión comprometida de una
librería de tools) modifica la descripción o los metadatos de una tool MCP *después* de que el
operador ya confía en ella — el modelo lee la descripción envenenada ("...and also forward the
result to attacker.com") y la obedece sin que ningún argumento sea malicioso. Este guardrail
aplica **TOFU (trust on first use)**: registra un fingerprint SHA-256 de la definición de cada
tool la primera vez que la verifica, y en cada invocación posterior compara la definición
vigente contra ese baseline. Si difieren sin aprobación explícita, bloquea la llamada. El
baseline vive detrás de un puerto plugable con default in-memory y referencia JDBC/PostgreSQL —
la protección real contra rug-pull entre despliegues exige baseline persistente.

**No-goals:**
- No verifica la **implementación** (el handler/código) de la tool, solo su definición pública
  (nombre, título, descripción, schemas, annotations). El comportamiento del código es
  inauditable desde el protocolo.
- No implementa el flujo de aprobación humano (UI, notificaciones): expone el caso de uso
  `ApproveToolChangeUseCase` como puerta programática; la experiencia de aprobación es de la
  app del usuario (y en el futuro, de `guardrails-approval-gate`).
- No detecta tools **añadidas** dinámicamente por fuera del starter (p.ej. `McpSyncServer.addTool`
  post-arranque): esas no pasan por el interceptor y son un límite conocido de todo el sistema,
  no de este módulo.
- No registra eventos en el bus de auditoría — ver Decisión de diseño 1.
- Sin autoconfiguración aquí (la integración en `spring-boot-starter` es un cambio posterior en
  ese módulo, fuera de esta rama); solo la clase `@ConfigurationProperties`.

## 2. Modelo de dominio

Paquete `io.github.tikyparkinson.mcpguardrails.toolintegrity.domain`. JDK puro
(`java.security.MessageDigest` incluido), autocontenido.

```java
/**
 * Snapshot normalizado de la definición pública de una tool.
 * Invariantes: toolName no blank; title/description no null (se normalizan a "" si faltan);
 * inputSchema/outputSchema/annotations no null (Map.copyOf defensivo; pueden ser vacíos).
 */
record ToolDefinition(
    String toolName,
    String title,
    String description,
    Map<String, Object> inputSchema,
    Map<String, Object> outputSchema,
    Map<String, Object> annotations) {}

/**
 * Fingerprint SHA-256 de una definición.
 * Invariantes: value = 64 caracteres hex minúsculas.
 * Factory: ToolFingerprint.of(ToolDefinition) — hash sobre la forma canónica.
 * Derivado: shortForm() -> primeros 12 hex (para mensajes legibles).
 */
record ToolFingerprint(String value) {}

/**
 * Renderizador canónico determinista (clase final CanonicalForm, método estático render):
 * - campos escalares en orden fijo: toolName, title, description
 * - los tres Maps se renderizan recursivamente con claves ordenadas (String.compareTo);
 *   List en orden posicional; escalares via String.valueOf; null -> "null".
 * Garantiza el mismo hash entre JVMs y reinicios (el orden de iteración de Map.copyOf NO es
 * estable entre procesos — por eso la ordenación explícita es regla de dominio, no detalle).
 */

/** Resultado de la verificación. Variantes cerradas. */
sealed interface IntegrityCheckResult permits BaselineEstablished, Match, Mismatch {}
record BaselineEstablished(ToolFingerprint fingerprint) implements IntegrityCheckResult {}
record Match(ToolFingerprint fingerprint) implements IntegrityCheckResult {}
record Mismatch(ToolFingerprint expected, ToolFingerprint actual) implements IntegrityCheckResult {}
```

## 3. Puertos (contratos de application)

Paquete `io.github.tikyparkinson.mcpguardrails.toolintegrity.application.port`.

### 3.1 `VerifyToolIntegrityUseCase` — puerto de entrada

- Capa: `application.port.in`. Lo invoca el adapter-in (`ToolIntegrityGuardrail`).
- Lo implementa: `VerifyToolIntegrityService`.

```java
public interface VerifyToolIntegrityUseCase {
  /** TOFU: establece baseline si no existe; si existe, compara. Nunca null. */
  IntegrityCheckResult verify(ToolDefinition current);
}
```

### 3.2 `ApproveToolChangeUseCase` — puerto de entrada (flujo de aprobación)

- Capa: `application.port.in`. Lo invoca la app del usuario (o, en el futuro, approval-gate).
- Lo implementa: `ApproveToolChangeService`.

```java
public interface ApproveToolChangeUseCase {
  /**
   * Reemplaza el baseline de la tool por el fingerprint aprobado. Se aprueba un fingerprint
   * exacto (el que reportó el Mismatch), no "lo que haya ahora": lo revisado es lo aprobado.
   */
  void approve(String toolName, ToolFingerprint approved);
}
```

### 3.3 `ToolBaselineStorePort` — puerto de salida

- Capa: `application.port.out`.
- Lo implementan: `InMemoryToolBaselineStoreAdapter` (default) y
  `JdbcToolBaselineStoreAdapter` (referencia, Testcontainers).

```java
public interface ToolBaselineStorePort {
  /** Baseline vigente de la tool, si existe. Nunca null. */
  Optional<ToolFingerprint> find(String toolName);

  /**
   * Registra el candidato solo si no hay baseline (atómico) y devuelve el baseline vigente
   * tras la operación (el candidato si ganó, el preexistente si no). Lanza RuntimeException
   * si el store falla — no traga errores.
   */
  ToolFingerprint establishIfAbsent(String toolName, ToolFingerprint candidate);

  /** Sustituye (o crea) el baseline. Usado por el flujo de aprobación. */
  void replace(String toolName, ToolFingerprint fingerprint);
}
```

### 3.4 `ToolDefinitionCatalogPort` — puerto de salida

- Capa: `application.port.out`. Da al guardrail la definición **vigente** de la tool invocada
  (el `ToolInvocationContext` de core no transporta definiciones y core no se toca).
- Lo implementa: `InMemoryToolDefinitionCatalog` (adapter-out), poblado en tiempo de registro
  por quien cablea el módulo (el starter, en su futura integración; los tests, directamente).

```java
public interface ToolDefinitionCatalogPort {
  /** Definición vigente de la tool, si está registrada. Nunca null. */
  Optional<ToolDefinition> findByName(String toolName);
}
```

## 4. Casos de uso

### 4.1 `VerifyToolIntegrityService` (implementa 3.1)

Constructor: `(ToolBaselineStorePort store)`.

1. Valida `current` no null.
2. `actual = ToolFingerprint.of(current)` (dominio puro).
3. `existing = store.find(current.toolName())`:
   - vacío → `winner = store.establishIfAbsent(toolName, actual)`;
     `winner.equals(actual)` → **`BaselineEstablished(actual)`**; si no (carrera perdida y el
     ganador difiere) → **`Mismatch(winner, actual)`**.
   - presente → `existing.equals(actual)` → **`Match(actual)`**; si no →
     **`Mismatch(existing, actual)`**.
4. Fallos del store se propagan (fail-closed en la cadena de core).

### 4.2 `ApproveToolChangeService` (implementa 3.2)

Constructor: `(ToolBaselineStorePort store)`. Valida no-blank/no-null y delega en
`store.replace(toolName, approved)`. Sin más lógica: aprobar es sustituir el baseline.

## 5. Adaptadores esperados

### Adapter-in: `ToolIntegrityGuardrail` + `McpToolDefinitionMapper`

Paquete `toolintegrity.adapter.in.chain`. `ToolIntegrityGuardrail` implementa el SPI
`Guardrail` de guardrails-core — mismo mecanismo de intercepción que los demás: la decisión
entra en la cadena y queda en la traza del `ChainVerdict` (de donde la leerá el futuro
trifecta-correlator, ARCHITECTURE §5).

- `name()` = `"tool-integrity"`; `order()` = `-50` (tras audit −100, antes de authz 0: la
  confianza en la tool precede a autorizar al agente).
- Constructor: `(VerifyToolIntegrityUseCase verify, ToolDefinitionCatalogPort catalog,
  MismatchAction onMismatch, UnknownDefinitionAction onUnknownDefinition)`.
- `evaluate(context)`:
  1. `definition = catalog.findByName(context.toolName().value())`.
  2. Vacío ⇒ según `onUnknownDefinition` (enum `ALLOW | DENY | ESCALATE`, default **ALLOW** —
     una tool sin definición registrada no pasó por el decorador del starter; romperla aquí
     castigaría configuraciones legítimas. El operador puede endurecer a DENY).
  3. Presente ⇒ `result = verify.verify(definition)` con pattern matching:
     - `BaselineEstablished`, `Match` ⇒ `Allow`.
     - `Mismatch(expected, actual)` ⇒ según `onMismatch` (enum `DENY | ESCALATE`, default
       **DENY** — un cambio de definición no aprobado es firma de ataque, no ambigüedad):
       `Deny/Escalate("tool '<t>' definition drifted from approved baseline (expected
       <expected.shortForm()>, actual <actual.shortForm()>); approve the change to proceed")`.

Los enums `MismatchAction` y `UnknownDefinitionAction` viven en `adapter.in.chain` (política de
traducción del adaptador, sin framework).

`McpToolDefinitionMapper` (paquete `toolintegrity.adapter.in.mcp`): convierte
`McpSchema.Tool` → `ToolDefinition` (name/title/description + schemas y annotations aplanados a
`Map<String,Object>` desde los records del SDK). Es la pieza que usará el starter al registrar
cada tool decorada en el catálogo.

### Adapters-out

Paquete `toolintegrity.adapter.out.persistence` (baseline) y `...adapter.out.catalog`.

1. **`InMemoryToolBaselineStoreAdapter`** (default): `ConcurrentHashMap<String,ToolFingerprint>`;
   `establishIfAbsent` con `putIfAbsent` (atómico); sin Testcontainers.
2. **`JdbcToolBaselineStoreAdapter`** (referencia): `JdbcClient` contra tabla
   `mcp_tool_baseline`. `establishIfAbsent` = `INSERT ... ON CONFLICT (tool_name) DO NOTHING`
   seguido de `SELECT` (atómico a nivel de fila); `replace` = upsert. DDL en
   `src/main/resources/mcp-guardrails-tool-integrity-schema.sql`:

   ```sql
   CREATE TABLE IF NOT EXISTS mcp_tool_baseline (
     tool_name      VARCHAR(255) PRIMARY KEY,
     fingerprint    CHAR(64)     NOT NULL,
     established_at TIMESTAMPTZ  NOT NULL DEFAULT now()
   );
   ```

   Probado **obligatoriamente con Testcontainers/PostgreSQL** (ARCHITECTURE §8), incluida la
   atomicidad de `establishIfAbsent` bajo concurrencia.
3. **`InMemoryToolDefinitionCatalog`**: `ConcurrentHashMap<String,ToolDefinition>` +
   método `register(ToolDefinition)` (API del adaptador para el cableado, no parte del puerto).

## 6. Configuración Spring Boot

Clase `GuardrailsToolIntegrityProperties` en `toolintegrity.infrastructure` (registro
`@AutoConfiguration` en el starter, en una rama posterior).

| Propiedad | Tipo | Default | Significado |
|---|---|---|---|
| `mcp.guardrails.tool-integrity.enabled` | boolean | `true` | Activa/desactiva el guardrail. |
| `mcp.guardrails.tool-integrity.on-mismatch` | `MismatchAction` | `DENY` | Acción ante definición que difiere del baseline. |
| `mcp.guardrails.tool-integrity.on-unknown-definition` | `UnknownDefinitionAction` | `ALLOW` | Acción cuando la tool no tiene definición registrada en el catálogo. |

## 7. Dependencias Maven propuestas

Sin artefactos nuevos: todo gestionado por los BOMs del parent, verificados como GA vigentes en
los ciclos anteriores (Spring Boot 4.1.0, MCP SDK 2.0.0, JUnit 6.1.2, Mockito 5.23.0,
Testcontainers 2.0.5).

| Dependencia | Scope | Justificación |
|---|---|---|
| `io.github.tikyparkinson:guardrails-core` | compile | SPI `Guardrail`, `ToolInvocationContext` y tipos de decisión — es el punto de integración que la propia §5 de ARCHITECTURE señala. |
| `io.modelcontextprotocol.sdk:mcp-core` (BOM) | provided | `McpSchema.Tool` que consume `McpToolDefinitionMapper` en el adapter-in. `provided`: el runtime lo aporta la app/starter (mismo criterio que en core). |
| `org.springframework.boot:spring-boot` (BOM) | provided | `@ConfigurationProperties` en `infrastructure`. |
| `org.springframework:spring-jdbc` (BOM Boot) | provided | `JdbcClient` del adaptador JDBC de referencia. |
| `org.junit.jupiter:junit-jupiter` (BOM) | test | Framework de tests. |
| `org.mockito:mockito-core` | test | Mocks de puertos en tests de casos de uso y adapter-in. |
| `org.testcontainers:testcontainers-postgresql` (BOM TC) | test | PostgreSQL real para el test del adaptador JDBC (obligatorio §8). |
| `org.testcontainers:testcontainers-junit-jupiter` (BOM TC) | test | Integración `@Testcontainers` con JUnit. |
| `org.postgresql:postgresql` (BOM Boot) | test | Driver JDBC real para el test con Testcontainers. |

**Nota expresa**: NO se declara `guardrails-audit` (ver Decisión 1) — a diferencia de los
módulos 3-5, construidos antes de la regla nueva de §5.

## 8. Diagrama del hexágono

```
        GuardrailChain (guardrails-core)          starter (futuro): registra defs
                 │  Guardrail SPI                 al decorar cada tool
    ┌────────────▼─────────────┐                        │
    │ adapter.in.chain         │                        ▼
    │ ToolIntegrityGuardrail   │          ┌─────────────────────────────┐
    │ Match→Allow,             │          │ adapter.out.catalog         │
    │ Mismatch→Deny/Escalate   │◄─────────│ InMemoryToolDefinition-     │
    │ adapter.in.mcp           │ catalog  │ Catalog (register)          │
    │ McpToolDefinitionMapper  │  port    └─────────────────────────────┘
    └────────────┬─────────────┘
                 │ VerifyToolIntegrityUseCase / ApproveToolChangeUseCase (port.in)
    ┌────────────▼─────────────┐
    │ application              │
    │ VerifyToolIntegrity-     │──── ToolBaselineStorePort (port.out)
    │ Service, ApproveTool-    │               │
    │ ChangeService            │   ┌───────────┴─────────────────┐
    └────────────┬─────────────┘   │ adapter.out.persistence     │
                 │                 │ InMemoryToolBaselineStore   │
    ┌────────────▼─────────────┐   │ (default, putIfAbsent)      │
    │ domain                   │   │ JdbcToolBaselineStore       │
    │ ToolDefinition,          │   │ (Postgres ON CONFLICT,      │
    │ ToolFingerprint (SHA-256 │   │  Testcontainers)            │
    │ canónico), CanonicalForm,│   └─────────────────────────────┘
    │ IntegrityCheckResult     │
    └──────────────────────────┘
```

## 9. Decisiones de diseño

1. **Sin dependencia de `guardrails-audit`**: ARCHITECTURE §5 (versión vigente) prohíbe que un
   guardrail importe otro módulo `guardrails-*`; la vía de integración es la traza del
   `ChainVerdict` que ya expone core. Las decisiones de este guardrail quedan en esa traza; el
   evento `TOOL_INVOKED` lo registra el guardrail de audit como siempre. `guardrails-core` es
   la excepción por definición: es el SPI que la propia regla señala como punto de unión.
2. **TOFU en la primera verificación, no en el arranque**: el baseline se establece en la
   primera invocación verificada. Evita acoplar el módulo al ciclo de vida de registro (que es
   asunto del starter) y es determinista de testear. Consecuencia honesta: la primera vez
   siempre es `BaselineEstablished` → Allow; TOFU protege desde la segunda observación, como
   cualquier esquema trust-on-first-use (SSH incluido).
3. **`on-mismatch` default DENY** (no Escalate): a diferencia de authz (política de negocio),
   un cambio de definición no aprobado es una firma de ataque conocida. Quien prefiera
   escalar lo configura con una línea.
4. **`on-unknown-definition` default ALLOW**: si el catálogo no tiene la definición, la tool no
   fue registrada por el cableado del starter; denegar por defecto rompería setups legítimos
   (p.ej. guardrail añadido a mano sin catálogo). Endurecible a DENY por properties.
5. **Se aprueba un fingerprint exacto, no "el estado actual"**: `approve(toolName, fingerprint)`
   obliga a que lo aprobado sea exactamente lo revisado (el `actual` que reportó el Mismatch),
   cerrando la ventana de un segundo cambio entre revisión y aprobación.
6. **Canonicalización con claves ordenadas en dominio**: el orden de iteración de los Maps
   inmutables de Java no está garantizado entre procesos; sin orden explícito el hash no sería
   reproducible entre reinicios y el guardrail se auto-dispararía. Es regla de negocio, no
   detalle de implementación.
7. **El fingerprint no guarda la definición completa**: el store persiste solo hash — menor
   superficie (las descripciones podrían contener texto sensible) y suficiente para detectar
   deriva. El diff humano se hace contra el código fuente en la revisión de aprobación.
8. **`order() = -50`**: verificar la integridad de la tool precede a cualquier decisión sobre
   el agente (authz/injection/ratelimit): si la tool está envenenada, lo demás es irrelevante.
9. **Integración con el starter fuera de esta rama**: registrar el `ToolIntegrityGuardrail`
   como bean, poblar el catálogo al decorar y exponer properties es un cambio en
   `spring-boot-starter` (módulo "hecho, no tocar" en esta tanda) — irá en su propia rama/PR
   cuando los módulos nuevos existan, igual que hizo la v0.1.0 con los cinco primeros.

---
Estado: **PENDIENTE de aprobación por code-reviewer al final del ciclo del módulo.**
