# ARCHITECTURE.md — mcp-guardrails-spring-boot-starter

Este documento es la **ley del proyecto**. Los 5 agentes (`spec-architect`, `domain-builder`,
`adapter-builder`, `test-engineer`, `code-reviewer`) deben leerlo antes de producir nada y
no pueden contradecirlo. Si un spec o un fragmento de código choca con este documento, gana
este documento.

## 1. Identidad del proyecto

| Campo           | Valor                                                                                      |
| --------------- | ------------------------------------------------------------------------------------------ |
| groupId         | `io.github.tikyparkinson`                                                                  |
| artifactId raíz | `mcp-guardrails-spring-boot-starter`                                                        |
| Repositorio     | `https://github.com/TikyParkinson/mcp-agent-guardrails-spring-boot-starter`                 |
| Build tool      | Maven (multi-módulo)                                                                        |
| Java            | 25 (LTS), `--release 25`, preview features **desactivadas**                                |
| Licencia        | Apache License 2.0 (header en cabecera de cada `.java`)                                    |
| Distribución    | GitHub (código) + Maven Central (artefactos, vía Central Publisher Portal / OSSRH sucesor) |

## 2. Regla de oro de versiones: "siempre la última GA"

Ningún agente puede escribir un número de versión de memoria. Antes de fijar la versión de
**Spring Boot, Spring AI, MCP Java SDK, JUnit 5, Mockito, Testcontainers, Jacoco, Spotless o
Checkstyle**, el agente debe verificar la última versión **GA (no milestone, no RC, no SNAPSHOT)**
consultando Maven Central (`search.maven.org`) o el repositorio oficial en GitHub. Si el agente
no tiene forma de verificarlo en el momento, debe dejarlo marcado explícitamente como
`// TODO(version-check): verificar última GA antes de mergear` en vez de inventar un número.

## 3. Arquitectura hexagonal — reglas duras

Cada guardrail es un hexágono independiente. La regla de dependencia es unidireccional:

```
adapters (in/out)  ──depends on──>  application (casos de uso, puertos)  ──depends on──>  domain
```

- **`domain`**: entidades, value objects (siempre `record` inmutables cuando aplique), reglas de
  negocio puras. **Cero** imports de Spring, cero I/O, cero anotaciones de framework. Si una clase
  de dominio importa algo que no sea JDK puro o otra clase del propio dominio, es un defecto y
  `code-reviewer` debe rechazarlo.
- **`application`**: casos de uso (interactors) que orquestan el dominio, y los **puertos**
  (interfaces) `...Port` que definen qué necesita el caso de uso del mundo exterior
  (`AuditLogStorePort`, `RateLimitStorePort`, etc.). Los puertos son contratos, no implementaciones.
- **`adapters/in`**: quien "entra" al hexágono. Aquí vive el interceptor de llamadas MCP
  (tool-call interceptor) que traduce una invocación real de una tool en una llamada al caso de uso.
- **`adapters/out`**: quien "sale" del hexágono. Implementaciones concretas de los puertos
  (`JdbcAuditLogStoreAdapter`, `InMemoryRateLimitStoreAdapter`, etc.).
- **`infrastructure`**: exclusivamente configuración de Spring Boot: clases `@AutoConfiguration`,
  `@ConfigurationProperties`, `spring.factories` / `AutoConfiguration.imports`. Nada de lógica de
  negocio aquí.

## 4. Persistencia: puerto/adaptador plugable

Cada guardrail que necesite persistir estado (auditoría, rate limiting) define su propio puerto
`out` (ej. `AuditLogStorePort`). El starter **siempre** trae una implementación in-memory por
defecto (`@ConditionalOnMissingBean`) para que funcione sin configuración, y documenta cómo el
usuario final sustituye esa bean por su propio adaptador (JDBC, Redis, lo que sea). El proyecto
en sí solo implementa, como referencia probada con Testcontainers, un adaptador JDBC/PostgreSQL
de ejemplo — no se asume que todo usuario use Postgres.

## 5. Módulos Maven

```
mcp-agent-guardrails-spring-boot-starter/                 (pom, packaging=pom, parent)
├── guardrails-core/                    ✅ hecho — Guardrail SPI, chain, GuardrailDecision, tool-call interceptor
├── guardrails-audit/                   ✅ hecho — auditoría/logging de tool calls
├── guardrails-authz/                   ✅ hecho — autorización agente→tool
├── guardrails-injection-guard/         ✅ hecho — anti prompt-injection sobre argumentos
├── guardrails-ratelimit/               ✅ hecho — rate limiting por (agente, tool)
├── guardrails-tool-integrity/          🚧 nuevo — anti tool-poisoning: hash/baseline de la descripción de cada tool
├── guardrails-credential-leak-guard/   🚧 nuevo — detecta y redacta secretos/credenciales en argumentos y resultados
├── guardrails-egress-control/          🚧 nuevo — allowlist de destinos salientes (HTTP, email, mensajería) por tool
├── guardrails-anomaly-detector/        🚧 nuevo — detecta loops e invocaciones repetidas anómalas, usando el histórico de audit/ratelimit
├── guardrails-approval-gate/           🚧 nuevo — implementa la ejecución real de una decisión `Escalate`: pausa la acción hasta aprobación humana
├── guardrails-trifecta-correlator/     🚧 nuevo — correlaciona, a nivel de sesión, si las 3 señales de la "lethal trifecta" están activas a la vez
└── spring-boot-starter/                ✅ hecho — autoconfiguración que agrega todo
```

Cada módulo `guardrails-*` sigue internamente la subdivisión `domain / application / adapter-in
/ adapter-out`. `spring-boot-starter` solo contiene `infrastructure` (autoconfiguración) y
depende de los módulos anteriores. Ninguno importa otro módulo `guardrails-*` como dependencia
Maven: si un guardrail necesita saber qué decidieron otros (caso de `trifecta-correlator`), lo
hace leyendo la traza de decisión ya expuesta por `guardrails-core` para la invocación en curso,
nunca importando el módulo del otro guardrail.

## 6. Orden de construcción

**Hechos (no tocar):** `guardrails-core` → `guardrails-audit` → `guardrails-authz` →
`guardrails-injection-guard` → `guardrails-ratelimit` → `spring-boot-starter` (v0.1.0 ya en
Maven Central).

**Pendientes, en este orden:**

7. `guardrails-tool-integrity` — sin dependencia de los nuevos, puede ir primero.
8. `guardrails-credential-leak-guard` — mismo nivel que `injection-guard` (analiza contenido de
   tool), sin dependencias nuevas.
9. `guardrails-egress-control` — sin dependencias nuevas.
10. `guardrails-anomaly-detector` — depende de datos históricos que ya exponen `guardrails-audit`
    y `guardrails-ratelimit` (ya hechos), por eso no puede ir antes que ellos aunque estos ya
    estén construidos.
11. `guardrails-approval-gate` — implementa qué pasa cuando la chain devuelve `Escalate`; debe
    existir antes que el correlador porque este lo invoca.
12. `guardrails-trifecta-correlator` — el más complejo: lee la traza de `authz`,
    `injection-guard` y `egress-control` para esa invocación/sesión, y si detecta las 3
    condiciones activas a la vez, fuerza una decisión `Escalate` que resuelve `approval-gate`.
    Va último porque depende conceptualmente de que 9, 10 y 11 ya existan.

No se empieza un módulo nuevo sin que el anterior haya pasado por los 5 agentes completos
(spec → domain → adapter → test → review) y `code-reviewer` lo haya aprobado.

**Flujo por rama, módulo a módulo:** cada módulo pendiente se construye en su propia rama
`feature/<modulo>` creada desde `develop` (ej. `feature/guardrails-tool-integrity`). Los 5
agentes corren dentro de esa rama. Solo cuando `code-reviewer` aprueba y existe
`docs/specs/<modulo>-DONE.md`, se abre PR hacia `develop`. No se arranca la siguiente rama hasta
que el PR anterior esté mergeado.

## 7. Estándares de código limpio (obligatorios, sin excepción)

- Métodos: máximo ~25 líneas, una responsabilidad. Si un método necesita comentarios para
  explicar "qué hace" (no "por qué"), hay que dividirlo.
- Nada de `null` como valor de retorno en dominio/aplicación: usar `Optional<T>` o modelar el caso
  con un tipo (`sealed interface` + variantes).
- Sin estado mutable estático, sin singletons manuales (Spring gestiona el ciclo de vida).
- Sin dependencias "por si acaso": cada `<dependency>` en cada `pom.xml` debe estar justificada
  en el spec del módulo. `code-reviewer` rechaza cualquier dependencia no listada ahí.
- Nombres de paquete: `io.github.tikyparkinson.mcpguardrails.<modulo>.<capa>` (ej.
  `io.github.tikyparkinson.mcpguardrails.audit.domain`).
- Java 25: usar `record` para value objects, `sealed interface` para modelar variantes cerradas
  (ej. resultado de una decisión de guardrail: `Allow`, `Deny(reason)`, `Escalate(reason)`),
  pattern matching en `switch` donde aporte claridad. No usar preview features.

## 8. Calidad y build

- **Spotless** (formateo automático, `google-java-format` o equivalente) + **Checkstyle**
  (validación: sin wildcard imports, orden de imports, complejidad ciclomática máxima, longitud
  de línea). Ambos corren en `mvn verify` y **rompen el build** si fallan.
- **Jacoco**: mínimo 80% de cobertura de líneas y de ramas por módulo, aplicado como
  `check` que falla el build si no se cumple.
- **Testcontainers**: obligatorio para probar cualquier adaptador `out` que hable con un store
  real (ej. el adaptador JDBC/Postgres de referencia). Los tests de dominio y aplicación usan
  JUnit 5 + Mockito puro, sin containers.
- CI: GitHub Actions, un workflow que corra `mvn -B verify` en cada push/PR, más un job de
  release que publique a Maven Central en tags `v*.*.*`.

## 9. Definición de "hecho" (Definition of Done) por módulo

Un módulo `guardrails-*` está terminado solo si `code-reviewer` confirma **todos** estos puntos:

- [ ] Spec en `docs/specs/<modulo>-spec.md` existe y el código no se desvía de ella
- [ ] Cero imports de Spring en `domain`
- [ ] Todas las versiones de dependencias son GA vigentes verificadas, ninguna inventada
- [ ] Spotless + Checkstyle pasan sin warnings
- [ ] Cobertura Jacoco ≥ 80% líneas y ramas
- [ ] Adaptadores `out` con store real cubiertos por test de Testcontainers
- [ ] Sin dependencias no justificadas en el `pom.xml`
- [ ] Header de licencia Apache 2.0 presente en cada archivo `.java`
- [ ] README del módulo explica el puerto plugable y cómo sustituir el adaptador por defecto
