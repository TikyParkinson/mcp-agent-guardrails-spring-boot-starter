# Spec — guardrails-authz

> Módulo 3 según ARCHITECTURE.md §6. Autor: spec-architect.
> Paquete raíz: `io.github.tikyparkinson.mcpguardrails.authz`.
> Prerequisito cumplido: `guardrails-audit-DONE.md` aprobado.

## 1. Problema y alcance

Hoy cualquier agente MCP puede invocar cualquier tool registrada: no existe control de acceso
agente→tool. `guardrails-authz` introduce una política de autorización declarativa: reglas
`(agentPattern, toolPattern, effect)` evaluadas en orden, con efecto por defecto configurable
para invocaciones sin regla. El guardrail `authz` consulta la política, devuelve
`Allow`/`Deny`/`Escalate` y registra cada decisión en el bus de auditoría del módulo anterior
(ARCHITECTURE.md §6).

**No-goals:**
- No hay autenticación: el `AgentId` viene resuelto por core (client info MCP); este módulo no
  verifica identidad, solo autoriza la identidad declarada.
- No persiste políticas: la política por defecto vive en configuración (properties). No hay
  store real ⇒ **no aplica Testcontainers** (ARCHITECTURE.md §8 solo lo exige para stores
  reales). Quien quiera políticas dinámicas (BD, OPA, etc.) implementa `AccessPolicyPort`.
- No inspecciona argumentos de la tool (eso es de injection-guard, módulo 4). Autoriza solo por
  identidad de agente y nombre de tool.
- No implementa jerarquías de roles, grupos ni scopes OAuth. Patrones exactos y comodín `*`,
  nada más.
- Sin autoconfiguración aquí (starter, módulo 6); solo la clase `@ConfigurationProperties`.

## 2. Modelo de dominio

Paquete `io.github.tikyparkinson.mcpguardrails.authz.domain`. JDK puro, autocontenido (sin
imports de core ni de audit, mismo criterio que el módulo 2).

```java
/** Efecto de una regla o del default de la política. */
enum PermissionEffect { ALLOW, DENY, ESCALATE }

/**
 * Regla de autorización. Patrones: coincidencia exacta case-sensitive o comodín total "*".
 * Invariantes: ningún campo null; patterns no blank.
 */
record PolicyRule(String agentPattern, String toolPattern, PermissionEffect effect) {
  boolean matches(String agentId, String toolName) { ... } // "*" o igualdad exacta en ambos
}

/**
 * Política completa: lista ordenada de reglas + efecto por defecto.
 * Invariantes: rules no null (copia defensiva List.copyOf), defaultEffect no null.
 * Primera regla que matchea gana; sin match ⇒ defaultEffect.
 */
record AccessPolicy(List<PolicyRule> rules, PermissionEffect defaultEffect) {
  PolicyDecision decide(String agentId, String toolName) { ... }
}

/**
 * Resultado de evaluar la política: efecto + origen (la regla que matcheó o el default).
 * Invariantes: effect no null; source no null ni blank (ej. "rule[2]" o "default").
 */
record PolicyDecision(PermissionEffect effect, String source) {}
```

La lógica de matching y selección vive **en el dominio** (`AccessPolicy.decide`), no en el caso
de uso: es regla de negocio pura.

## 3. Puertos (contratos de application)

Paquete `io.github.tikyparkinson.mcpguardrails.authz.application.port`.

### 3.1 `AuthorizeToolInvocationUseCase` — puerto de entrada

- Capa: `application.port.in`. Lo invoca el adapter-in (`AuthzGuardrail`).
- Lo implementa: `AuthorizeToolInvocationService` (application).

```java
public interface AuthorizeToolInvocationUseCase {
  /** Evalúa la política vigente para (agentId, toolName). Nunca null. */
  PolicyDecision authorize(String agentId, String toolName);
}
```

### 3.2 `AccessPolicyPort` — puerto de salida

- Capa: `application.port.out`.
- Lo implementa: `InMemoryAccessPolicyAdapter` (default, construido desde properties). El
  usuario lo sustituye para políticas dinámicas.

```java
public interface AccessPolicyPort {
  /** Política vigente. Se consulta en cada invocación (permite implementaciones dinámicas). */
  AccessPolicy currentPolicy();
}
```

## 4. Caso de uso — `AuthorizeToolInvocationService`

Clase en `authz.application.usecase`, implementa `AuthorizeToolInvocationUseCase`. Constructor:
`(AccessPolicyPort policyPort)`.

1. Valida `agentId` y `toolName` no null ni blank (IllegalArgumentException si lo son).
2. `policy = policyPort.currentPolicy()`.
3. Devuelve `policy.decide(agentId, toolName)`:
   - Recorre `rules` en orden; la primera con `matches(agentId, toolName)` determina
     `PolicyDecision(rule.effect(), "rule[i]")` (i = índice 0-based).
   - Sin match: `PolicyDecision(defaultEffect, "default")`.

Sin más ramas: la traducción de `PolicyDecision` a `GuardrailDecision` y el registro en el bus
de auditoría son responsabilidad del adapter-in.

## 5. Adaptadores esperados

### Adapter-in: `AuthzGuardrail`

Paquete `authz.adapter.in.chain`. Implementa `Guardrail` de core.

- `name()` = `"authz"`; `order()` = `0`.
- `evaluate(context)`:
  1. `decision = useCase.authorize(context.agentId().value(), context.toolName().value())`.
  2. Registra en el bus (`RecordAuditEventUseCase` de guardrails-audit) un `NewAuditEvent`
     con `emittedBy="authz"`, tipo según efecto (`DECISION_ALLOW`/`DECISION_DENY`/
     `DECISION_ESCALATE`) y `detail = decision.source()`.
  3. Traduce y devuelve, con pattern matching sobre `PermissionEffect`:
     - `ALLOW` ⇒ `new Allow()`
     - `DENY` ⇒ `new Deny("agent '<a>' is not allowed to call tool '<t>' (<source>)")`
     - `ESCALATE` ⇒ `new Escalate("agent '<a>' requires approval for tool '<t>' (<source>)")`
  - Si el bus de auditoría lanza, la excepción se propaga (fail-closed en core, coherente con
    los módulos 1-2).

### Adapter-out: `InMemoryAccessPolicyAdapter`

Paquete `authz.adapter.out.policy`. Guarda una `AccessPolicy` inmutable recibida por
constructor y la devuelve en `currentPolicy()`. El starter la construirá desde
`GuardrailsAuthzProperties` y la registrará `@ConditionalOnMissingBean`. Sin Testcontainers
(no hay store real).

## 6. Configuración Spring Boot

Clase `GuardrailsAuthzProperties` en `authz.infrastructure` (registro en el starter, módulo 6).

| Propiedad | Tipo | Default | Significado |
|---|---|---|---|
| `mcp.guardrails.authz.enabled` | boolean | `true` | Activa/desactiva el guardrail authz. |
| `mcp.guardrails.authz.default-effect` | `PermissionEffect` | `ALLOW` | Efecto cuando ninguna regla matchea. |
| `mcp.guardrails.authz.rules[i].agent` | String | — | Patrón de agente de la regla i (`*` = cualquiera). |
| `mcp.guardrails.authz.rules[i].tool` | String | — | Patrón de tool de la regla i (`*` = cualquiera). |
| `mcp.guardrails.authz.rules[i].effect` | `PermissionEffect` | — | Efecto de la regla i. |

La clase properties expone `toAccessPolicy()` para que el starter construya el adaptador default.

## 7. Dependencias Maven propuestas

Sin versiones nuevas: todo ya está gestionado por los BOMs del parent (verificados 2026-07-24
en los ciclos anteriores). Este módulo no introduce artefactos nuevos en Maven Central.

| Dependencia | Scope | Justificación |
|---|---|---|
| `io.github.tikyparkinson:guardrails-core` | compile | SPI `Guardrail`, `ToolInvocationContext`, `Allow`/`Deny`/`Escalate` que consume/produce el adapter-in. |
| `io.github.tikyparkinson:guardrails-audit` | compile | Bus de auditoría (`RecordAuditEventUseCase`, `NewAuditEvent`, `AuditEventType`) usado por el adapter-in para registrar decisiones. |
| `org.springframework.boot:spring-boot` (BOM) | provided | `@ConfigurationProperties` en `infrastructure`. |
| `org.junit.jupiter:junit-jupiter` (BOM) | test | Framework de tests. |
| `org.mockito:mockito-core` | test | Mocks de `AccessPolicyPort` y del bus de auditoría. |

Ninguna otra. Sin Testcontainers ni driver JDBC: no hay adaptador out con store real.

## 8. Diagrama del hexágono

```
        GuardrailChain (guardrails-core)
                 │  Guardrail SPI
    ┌────────────▼─────────────┐         bus de auditoría
    │ adapter.in.chain         │──────► RecordAuditEventUseCase
    │ AuthzGuardrail           │         (guardrails-audit)
    │ effect → Allow/Deny/Esc  │
    └────────────┬─────────────┘
                 │ AuthorizeToolInvocationUseCase (port.in)
    ┌────────────▼──────────────┐
    │ application               │
    │ AuthorizeToolInvocation-  │──── AccessPolicyPort (port.out)
    │ Service                   │              │
    └────────────┬──────────────┘  ┌───────────┴──────────────┐
                 │                 │ adapter.out.policy       │
    ┌────────────▼─────────────┐   │ InMemoryAccessPolicy-    │
    │ domain                   │   │ Adapter (default, desde  │
    │ AccessPolicy, PolicyRule │   │ properties)              │
    │ PermissionEffect,        │   └──────────────────────────┘
    │ PolicyDecision           │
    └──────────────────────────┘
```

## 9. Decisiones de diseño

1. **`default-effect = ALLOW`**: un starter que deniega todo sin configurar rompería la promesa
   de "funciona sin configuración" (ARCHITECTURE.md §4). Quien quiera default-deny lo declara
   con una línea de properties. Documentado en el README del módulo.
2. **Primera regla que matchea gana** (first-match-wins, sin merge de efectos): semántica
   simple y predecible, igual que en firewalls; permite poner excepciones antes del catch-all.
3. **Matching mínimo: exacto o `*`**: glob/regex parciales invitan a errores de seguridad
   difíciles de auditar. Si mañana hace falta, se amplía vía spec.
4. **La decisión vive en el dominio** (`AccessPolicy.decide`): el caso de uso solo orquesta
   puerto→dominio. Matching y precedencia son reglas de negocio puras y testeables sin mocks.
5. **El registro en el bus de auditoría vive en el adapter-in, no en el caso de uso**: el caso
   de uso de authz responde una pregunta pura ("¿puede A llamar a T?"); auditar cada decisión
   es una preocupación de integración entre módulos, y mantenerla en el adapter evita que
   `application` de authz dependa de otro módulo (dominio/application autocontenidos).
6. **`PolicyDecision.source`** ("rule[i]" / "default") viaja como `detail` del evento de
   auditoría: trazabilidad de qué regla disparó cada decisión sin volcar la política entera.

---
Estado: **PENDIENTE de aprobación por code-reviewer al final del ciclo del módulo.**
