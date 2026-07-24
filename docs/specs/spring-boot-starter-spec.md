# Spec — spring-boot-starter

> Módulo 6 (final) según ARCHITECTURE.md §6. Autor: spec-architect.
> Paquete raíz: `io.github.tikyparkinson.mcpguardrails.starter.infrastructure`.
> Prerequisito cumplido: DONE.md aprobado de los 5 módulos anteriores.

## 1. Problema y alcance

Los cinco módulos anteriores son librerías puras sin autoconfiguración: nadie las cablea. Este
módulo es el artefacto que el usuario final importa: registra los cuatro guardrails y la cadena
como beans de Spring Boot, con defaults in-memory `@ConditionalOnMissingBean` para que todo
funcione con **cero configuración** (ARCHITECTURE.md §4), y decora automáticamente las
`SyncToolSpecification` MCP presentes en el contexto para que cada tool call pase por la cadena.

**No-goals:**
- Cero lógica de negocio: solo `@AutoConfiguration`, `@ConditionalOn*` y el archivo
  `AutoConfiguration.imports` (ARCHITECTURE.md §5: "spring-boot-starter solo contiene
  infrastructure").
- No registra los adaptadores JDBC de referencia (audit/ratelimit): son opt-in del usuario
  (documentado en los README de esos módulos); el starter solo trae los defaults in-memory.
- No arranca ni configura el servidor MCP (eso es de Spring AI / la app del usuario); solo
  decora las tool specs que ya existan como beans.
- No soporta `McpAsyncServer` (coherente con el no-goal de core).

## 2. Modelo de dominio

**N/A por diseño.** Este módulo no tiene `domain` ni `application` (ARCHITECTURE.md §5). Los
tipos que maneja son los ya aprobados de los módulos 1-5.

## 3. Puertos (contratos de application)

**N/A por diseño.** No define puertos; consume los puertos `in` existentes y provee
implementaciones default de los puertos `out` vía beans condicionales.

## 4. Comportamiento del ensamblaje (en lugar de "caso de uso")

Clases `@AutoConfiguration`, todas bajo `starter.infrastructure`, todas condicionadas al flag
global `mcp.guardrails.enabled` (default true, `matchIfMissing = true`):

### 4.1 `GuardrailsCoreAutoConfiguration`
- `@EnableConfigurationProperties(GuardrailsCoreProperties)`.
- Beans (`@ConditionalOnMissingBean` todos):
  - `Clock` → `Clock.systemUTC()`.
  - `AgentIdResolver` → `AgentIdResolver.clientInfoName()`.
  - `EvaluateToolInvocationUseCase` → `new GuardrailChain(List<Guardrail>)` (colecta todos los
    beans `Guardrail`, lista posiblemente vacía).
  - `GuardrailToolSpecificationPostProcessor` (static bean): `BeanPostProcessor` que en
    `postProcessAfterInitialization` decora con `GuardrailToolDecorator.decorate(...)`:
    - beans de tipo `McpServerFeatures.SyncToolSpecification`;
    - beans `List<?>` cuyos elementos sean todos `SyncToolSpecification` (patrón habitual en
      Spring AI MCP server), devolviendo una lista decorada.
    Usa `ObjectProvider` para resolver el use case/resolver/clock de forma lazy (evita ciclos).

### 4.2 `GuardrailsAuditAutoConfiguration`
- `@EnableConfigurationProperties(GuardrailsAuditProperties)`.
- `AuditLogStorePort` → `InMemoryAuditLogStoreAdapter(props.inMemoryMaxEvents())`
  (`@ConditionalOnMissingBean`).
- `RecordAuditEventUseCase` → `RecordAuditEventService(store, clock, UUID::randomUUID)`
  (`@ConditionalOnMissingBean`).
- `AuditGuardrail` → `@ConditionalOnProperty("mcp.guardrails.audit.enabled", matchIfMissing=true)`
  + `@ConditionalOnMissingBean`.

### 4.3 `GuardrailsAuthzAutoConfiguration`
- `@EnableConfigurationProperties(GuardrailsAuthzProperties)`.
- `AccessPolicyPort` → `InMemoryAccessPolicyAdapter(props.toAccessPolicy())`
  (`@ConditionalOnMissingBean`).
- `AuthorizeToolInvocationUseCase` → `AuthorizeToolInvocationService(policyPort)`
  (`@ConditionalOnMissingBean`).
- `AuthzGuardrail` → `@ConditionalOnProperty("mcp.guardrails.authz.enabled", matchIfMissing=true)`
  + `@ConditionalOnMissingBean`.

### 4.4 `GuardrailsInjectionGuardAutoConfiguration`
- Análogo: `InjectionRuleSetPort` → `InMemoryInjectionRuleSetAdapter(props.toRules())`;
  `ScanToolArgumentsUseCase` → `ScanToolArgumentsService`; `InjectionGuardrail` →
  `@ConditionalOnProperty("mcp.guardrails.injection-guard.enabled", matchIfMissing=true)`.

### 4.5 `GuardrailsRatelimitAutoConfiguration`
- Análogo: `RateLimitStorePort` → `InMemoryRateLimitStoreAdapter()`; `CheckRateLimitUseCase` →
  `CheckRateLimitService(store, props.toPolicy())`; `RateLimitGuardrail` →
  `@ConditionalOnProperty("mcp.guardrails.ratelimit.enabled", matchIfMissing=true)`.

### 4.6 Registro
`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
con las 5 clases. Los guardrails llegan a la cadena por inyección de `List<Guardrail>`; el
orden lo resuelve `GuardrailChain` (order/name), no Spring.

## 5. Adaptadores esperados

**N/A** — no crea adaptadores nuevos; instancia los existentes como beans default. El "punto de
entrada real" es el `BeanPostProcessor` de 4.1, que conecta la cadena al ciclo de vida MCP
decorando las tool specs del contexto.

## 6. Configuración Spring Boot

No introduce propiedades nuevas; activa las ya definidas por los módulos 1-5:
`mcp.guardrails.enabled`, `mcp.guardrails.audit.*`, `mcp.guardrails.authz.*`,
`mcp.guardrails.injection-guard.*`, `mcp.guardrails.ratelimit.*`.

## 7. Dependencias Maven propuestas

Sin artefactos nuevos que verificar: `spring-boot-autoconfigure` y `spring-boot-test` los
gestiona el BOM de Spring Boot 4.1.0 (verificado 2026-07-24); `mcp-core` el BOM MCP 2.0.0.

| Dependencia | Scope | Justificación |
|---|---|---|
| `io.github.tikyparkinson:guardrails-core` | compile | Cadena, SPI, decorator, properties core. |
| `io.github.tikyparkinson:guardrails-audit` | compile | Beans de audit (store, bus, guardrail, properties). |
| `io.github.tikyparkinson:guardrails-authz` | compile | Beans de authz. |
| `io.github.tikyparkinson:guardrails-injection-guard` | compile | Beans de injection-guard. |
| `io.github.tikyparkinson:guardrails-ratelimit` | compile | Beans de ratelimit. |
| `org.springframework.boot:spring-boot-autoconfigure` (BOM) | compile | `@AutoConfiguration`, `@ConditionalOn*` — razón de ser del módulo. |
| `io.modelcontextprotocol.sdk:mcp-core` (BOM) | compile | `SyncToolSpecification` que decora el BeanPostProcessor. Aquí compile (no provided): el starter es quien lo lleva al classpath de la app. |
| `org.junit.jupiter:junit-jupiter` (BOM) | test | Framework de tests. |
| `org.springframework.boot:spring-boot-test` (BOM) | test | `ApplicationContextRunner` para probar las autoconfiguraciones (forma canónica; no es `@SpringBootTest`). |
| `org.springframework:spring-test` (BOM) | test | Requerido por spring-boot-test en runtime de tests. |
| `org.assertj:assertj-core` (BOM Boot) | test | Requerido en compile-time por `ApplicationContextRunner` (su `AssertableApplicationContext` implementa `AssertProvider` de AssertJ). |
| `org.mockito:mockito-core` | test | Dobles puntuales (ej. verificar backoff con bean de usuario). |

## 8. Diagrama del ensamblaje

```
  app del usuario                 spring-boot-starter (infrastructure)
  ───────────────                 ─────────────────────────────────────
  SyncToolSpecification beans ──► GuardrailToolSpecificationPostProcessor
                                        │ decora con GuardrailToolDecorator (core)
                                        ▼
                                  EvaluateToolInvocationUseCase = GuardrailChain
                                        │ List<Guardrail> (beans)
            ┌───────────────┬───────────┴────────┬────────────────┐
            ▼               ▼                    ▼                ▼
      AuditGuardrail   AuthzGuardrail   InjectionGuardrail  RateLimitGuardrail
            │               │                    │                │
      AuditLogStore    AccessPolicy      InjectionRuleSet   RateLimitStore
      (in-memory,      (in-memory,       (in-memory,        (in-memory,
       @CondOnMissing)  @CondOnMissing)   @CondOnMissing)    @CondOnMissing)
```

## 9. Decisiones de diseño

1. **artifactId `mcp-guardrails-spring-boot-starter`** (directorio `spring-boot-starter/` según
   ARCHITECTURE.md §5): el artifactId raíz ya es `mcp-agent-guardrails-spring-boot-starter` y
   Maven prohíbe duplicarlo en el hijo.
2. **BeanPostProcessor para decorar tools**: cubre tanto beans individuales
   `SyncToolSpecification` como `List<SyncToolSpecification>` (patrón de Spring AI MCP server).
   Es el punto de integración menos invasivo: no exige al usuario cambiar cómo registra tools.
   Declarado `static` con dependencias vía `ObjectProvider` para no forzar inicialización
   temprana del contexto.
3. **`Clock` y `AgentIdResolver` como beans `@ConditionalOnMissingBean`**: el usuario puede
   fijar un reloj de test o una resolución de identidad propia sin tocar nada más.
4. **`mcp-core` en scope compile aquí** (era provided en guardrails-core): el starter es el
   artefacto de conveniencia — quien lo importa debe recibir el SDK transitivamente.
5. **Tests con `ApplicationContextRunner`** (spring-boot-test): es la forma canónica de testear
   autoconfiguraciones sin levantar una app completa; la prohibición de `@SpringBootTest` de
   test-engineer aplica a dominio/aplicación, y aquí no hay ni lo uno ni lo otro — se testea
   exactamente lo que este módulo es: el cableado.
6. **Checklist §9 adaptado**: los ítems de dominio (cero Spring en domain, no null en
   application) se marcan N/A — este módulo no tiene esas capas por mandato de ARCHITECTURE.md.

---
Estado: **PENDIENTE de aprobación por code-reviewer al final del ciclo del módulo.**
