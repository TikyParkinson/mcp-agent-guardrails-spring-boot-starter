# Spec — guardrails-injection-guard

> Módulo 4 según ARCHITECTURE.md §6. Autor: spec-architect.
> Paquete raíz: `io.github.tikyparkinson.mcpguardrails.injectionguard`.
> Prerequisito cumplido: `guardrails-authz-DONE.md` aprobado.

## 1. Problema y alcance

Los argumentos de una tool MCP pueden llegar envenenados: instrucciones inyectadas ("ignore
previous instructions", "you are now DAN"), intentos de exfiltración de system prompts, o
payloads que buscan que el agente ejecute acciones no pedidas. `guardrails-injection-guard`
inspecciona los **argumentos** de cada invocación contra un conjunto ordenado de reglas de
detección (patrones regex con severidad), y produce `Allow`, `Escalate` (severidad SUSPICIOUS)
o `Deny` (severidad MALICIOUS). Cada detección se registra en el bus de auditoría. Es el único
guardrail que mira dentro de los argumentos.

**No-goals:**
- No usa LLM-as-judge ni clasificadores ML: detección determinista por reglas. Un puerto
  (`InjectionRuleSetPort`) permite a quien quiera enchufar detección externa.
- No sanitiza ni reescribe argumentos: decide, no transforma.
- No inspecciona las **respuestas** de las tools (output filtering es otro problema).
- No persiste nada propio: las reglas por defecto vienen embebidas + properties. Sin store real
  ⇒ sin Testcontainers (ARCHITECTURE.md §8).
- Sin autoconfiguración aquí (starter, módulo 6); solo `@ConfigurationProperties`.

## 2. Modelo de dominio

Paquete `io.github.tikyparkinson.mcpguardrails.injectionguard.domain`. JDK puro (regex =
`java.util.regex`), autocontenido.

```java
/** Severidad de una regla de detección. */
enum InjectionSeverity { SUSPICIOUS, MALICIOUS }

/**
 * Regla de detección. Invariantes: id no blank; pattern no null (Pattern precompilado,
 * case-insensitive); severity no null.
 * Factory: InjectionRule.of(String id, String regex, InjectionSeverity) — compila con
 * Pattern.CASE_INSENSITIVE | Pattern.DOTALL; regex inválida ⇒ IllegalArgumentException.
 */
record InjectionRule(String id, Pattern pattern, InjectionSeverity severity) {
  boolean matches(String text) { ... } // pattern.matcher(text).find()
}

/**
 * Resultado del escaneo de una invocación.
 * Invariantes: findings inmutable (List.copyOf), nunca null.
 * Derivados: highestSeverity() -> Optional<InjectionSeverity> (MALICIOUS > SUSPICIOUS);
 * clean() == findings.isEmpty().
 */
record ScanResult(List<Finding> findings) {
  record Finding(String ruleId, InjectionSeverity severity, String argumentPath) {}
}

/**
 * Escáner puro: aplica todas las reglas a todos los valores string (aplanados) de los
 * argumentos. Clase final ArgumentScanner con método:
 *   ScanResult scan(Map<String, Object> arguments, List<InjectionRule> rules)
 * Aplanado: valores String directos; dentro de Map/List anidados se recorre recursivamente
 * (profundidad máx. 8 para evitar bombas de anidamiento — más profundo se ignora);
 * otros tipos (números, booleanos, null) se ignoran. argumentPath estilo "query" /
 * "filters.name" / "items[2]".
 */
```

## 3. Puertos (contratos de application)

Paquete `io.github.tikyparkinson.mcpguardrails.injectionguard.application.port`.

### 3.1 `ScanToolArgumentsUseCase` — puerto de entrada

- Capa: `application.port.in`. Lo invoca el adapter-in (`InjectionGuardrail`).
- Lo implementa: `ScanToolArgumentsService` (application).

```java
public interface ScanToolArgumentsUseCase {
  /** Escanea los argumentos con las reglas vigentes. Nunca null. */
  ScanResult scan(Map<String, Object> arguments);
}
```

### 3.2 `InjectionRuleSetPort` — puerto de salida

- Capa: `application.port.out`.
- Lo implementa: `InMemoryInjectionRuleSetAdapter` (default: reglas built-in + extras de
  properties). Sustituible por feeds dinámicos de reglas.

```java
public interface InjectionRuleSetPort {
  /** Reglas vigentes, en orden de evaluación. Nunca null; puede ser vacía. */
  List<InjectionRule> activeRules();
}
```

## 4. Caso de uso — `ScanToolArgumentsService`

Clase en `injectionguard.application.usecase`, implementa `ScanToolArgumentsUseCase`.
Constructor: `(InjectionRuleSetPort ruleSetPort)`.

1. Valida `arguments` no null (NullPointerException).
2. `rules = ruleSetPort.activeRules()`.
3. Devuelve `ArgumentScanner.scan(arguments, rules)` (dominio puro hace el trabajo).

La traducción `ScanResult` → `GuardrailDecision` y el registro de auditoría son del adapter-in.

## 5. Adaptadores esperados

### Adapter-in: `InjectionGuardrail`

Paquete `injectionguard.adapter.in.chain`. Implementa `Guardrail` de core.

- `name()` = `"injection-guard"`; `order()` = `50` (después de authz).
- `evaluate(context)`:
  1. `result = useCase.scan(context.arguments())`.
  2. Si `result.clean()` ⇒ **no** registra evento (evitar ruido: solo se auditan detecciones;
     el TOOL_INVOKED ya lo registró audit) y devuelve `Allow`.
  3. Si hay findings: registra en el bus (`emittedBy="injection-guard"`,
     tipo `DECISION_DENY`/`DECISION_ESCALATE` según severidad máxima,
     `detail` = ruleIds con paths, ej. `"deny-ignore-instructions@query"` — sin volcar el
     contenido del argumento, coherente con la regla PII de audit).
  4. `MALICIOUS` ⇒ `Deny("malicious content detected in tool arguments (<ruleId@path>, ...)")`;
     solo `SUSPICIOUS` ⇒ `Escalate("suspicious content detected in tool arguments (...)")`.
  - Fallo del bus ⇒ propaga (fail-closed en core).

### Adapter-out: `InMemoryInjectionRuleSetAdapter`

Paquete `injectionguard.adapter.out.rules`. Recibe `List<InjectionRule>` por constructor y la
devuelve. El starter la construye con `BuiltInInjectionRules.defaults()` (dominio) + reglas
extra de properties, y la registra `@ConditionalOnMissingBean`.

**Reglas built-in** (clase `BuiltInInjectionRules` en dominio, ids estables):

| id | severidad | patrón (case-insensitive, aproximado) |
|---|---|---|
| `ignore-previous-instructions` | MALICIOUS | `ignore\s+(all\s+)?(previous\|prior\|above)\s+(instructions\|prompts?\|rules)` |
| `reveal-system-prompt` | MALICIOUS | `(reveal\|show\|print\|repeat)\s+(your\|the)\s+(system\s+)?prompt` |
| `override-role` | MALICIOUS | `you\s+are\s+(now\|no\s+longer)\s+` |
| `disregard-safety` | MALICIOUS | `(disregard\|bypass\|disable)\s+(your\s+)?(safety\|guardrails?\|filters?\|restrictions)` |
| `do-anything-now` | SUSPICIOUS | `\b(DAN\s+mode\|do\s+anything\s+now\|jailbreak)\b` |
| `base64-blob` | SUSPICIOUS | `[A-Za-z0-9+/]{200,}={0,2}` (payloads codificados largos) |

## 6. Configuración Spring Boot

Clase `GuardrailsInjectionGuardProperties` en `injectionguard.infrastructure`.

| Propiedad | Tipo | Default | Significado |
|---|---|---|---|
| `mcp.guardrails.injection-guard.enabled` | boolean | `true` | Activa/desactiva el guardrail. |
| `mcp.guardrails.injection-guard.built-in-rules-enabled` | boolean | `true` | Incluye las reglas built-in. |
| `mcp.guardrails.injection-guard.custom-rules[i].id` | String | — | Id de la regla custom i. |
| `mcp.guardrails.injection-guard.custom-rules[i].pattern` | String (regex) | — | Patrón de la regla custom i. |
| `mcp.guardrails.injection-guard.custom-rules[i].severity` | `InjectionSeverity` | — | Severidad de la regla custom i. |

Expone `toRules()` → `List<InjectionRule>` (built-in si `builtInRulesEnabled` + customs) para
que el starter construya el adaptador default.

## 7. Dependencias Maven propuestas

Sin artefactos nuevos: regex es JDK. Todo gestionado por BOMs ya verificados (2026-07-24).

| Dependencia | Scope | Justificación |
|---|---|---|
| `io.github.tikyparkinson:guardrails-core` | compile | SPI `Guardrail` y tipos de decisión para el adapter-in. |
| `io.github.tikyparkinson:guardrails-audit` | compile | Bus de auditoría para registrar detecciones. |
| `org.springframework.boot:spring-boot` (BOM) | provided | `@ConfigurationProperties` en `infrastructure`. |
| `org.junit.jupiter:junit-jupiter` (BOM) | test | Framework de tests. |
| `org.mockito:mockito-core` | test | Mocks de `InjectionRuleSetPort` y del bus. |

Ninguna otra. Sin Testcontainers: no hay store real.

## 8. Diagrama del hexágono

```
        GuardrailChain (guardrails-core)
                 │  Guardrail SPI
    ┌────────────▼─────────────┐        bus de auditoría (solo detecciones)
    │ adapter.in.chain         │──────► RecordAuditEventUseCase (guardrails-audit)
    │ InjectionGuardrail       │
    │ clean→Allow, SUSP→Esc,   │
    │ MAL→Deny                 │
    └────────────┬─────────────┘
                 │ ScanToolArgumentsUseCase (port.in)
    ┌────────────▼──────────────┐
    │ application               │
    │ ScanToolArgumentsService  │──── InjectionRuleSetPort (port.out)
    └────────────┬──────────────┘              │
                 │                 ┌───────────┴──────────────┐
    ┌────────────▼─────────────┐   │ adapter.out.rules        │
    │ domain                   │   │ InMemoryInjectionRuleSet │
    │ InjectionRule, ScanResult│   │ Adapter (built-in +      │
    │ ArgumentScanner,         │   │ properties)              │
    │ BuiltInInjectionRules    │   └──────────────────────────┘
    └──────────────────────────┘
```

## 9. Decisiones de diseño

1. **SUSPICIOUS ⇒ Escalate, MALICIOUS ⇒ Deny**: dos niveles bastan; un tercer nivel "log-only"
   sería redundante con el hecho de que toda detección ya queda auditada.
2. **No se audita el caso limpio**: el volumen sería idéntico a TOOL_INVOKED (ya registrado por
   audit) sin información nueva. Solo las detecciones aportan señal.
3. **El `detail` de auditoría lleva `ruleId@path`, nunca el contenido del argumento**: los
   argumentos pueden contener PII/secretos; coherente con la Decisión 2 de guardrails-audit.
4. **Aplanado recursivo con tope de profundidad 8**: cubre payloads anidados en JSON sin
   exponerse a bombas de anidamiento. Números/booleanos se ignoran (no son vector de texto).
5. **Reglas built-in en el dominio** (`BuiltInInjectionRules`): son conocimiento de negocio del
   guardrail, no configuración de framework; properties solo decide si se usan y añade customs.
6. **`order() = 50`**: corre después de authz (0). Si authz ya deniega, igual se evalúa (la
   cadena no corta) y su veredicto queda en el trace, pero la severidad Deny de authz domina.
7. **Paquete `injectionguard`** (sin guión): los guiones no son válidos en nombres de paquete
   Java; el artifactId mantiene `guardrails-injection-guard`.

---
Estado: **PENDIENTE de aprobación por code-reviewer al final del ciclo del módulo.**
