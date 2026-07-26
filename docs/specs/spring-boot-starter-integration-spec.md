# Spec — `spring-boot-starter-integration` (cableado de los 7 módulos restantes)

> **Prerequisito.** Requiere la extensión `guardrails-core-decorator-wiring`
> ([spec](guardrails-core-decorator-wiring-spec.md)), que se construye antes en esta misma rama.
> Sin ella `guardrails-approval-gate` no tiene por dónde entrar.

## 1. Problema y alcance

`spring-boot-starter` es el artefacto que importa el usuario final: su promesa es que añadirlo al
classpath activa los guardrails sin configuración. Hoy declara **5 de los 11** módulos —core,
audit, authz, injection-guard y ratelimit—, así que quien lo importe no obtiene tool-integrity,
credential-leak-guard, egress-control, anomaly-detector, approval-gate ni trifecta-correlator,
aunque los seis estén publicados y probados. Este trabajo los cablea.

**No-goals.**

- No cambia el comportamiento de ningún guardrail: solo los conecta.
- No trae transporte HTTP ni UI para los lados humanos de `approval-gate` y `trifecta-correlator`
  (§5.4). El starter publica los casos de uso; el canal lo expone el operador.
- No inventa properties nuevas: cada módulo ya tiene las suyas y este trabajo las respeta.
- No añade dependencias al artefacto publicado más allá de los propios módulos (§7).

## 2. Los tres bloqueos verificados

**(A) La cadena de salida nunca se ejecuta.** `GuardrailToolSpecificationPostProcessor.decorate`
llama a la sobrecarga de **4 argumentos**, que no pasa `EvaluateToolResultUseCase`. Cablear
`credential-leak-guard` sin arreglarlo daría un guardrail que detecta secretos en los argumentos
pero **no redacta los de las respuestas** — media protección presentada como completa, que es peor
que ninguna porque nadie la revisaría.

**(B) `approval-gate` no tiene por dónde entrar.** Ninguna sobrecarga de `GuardrailToolDecorator`
acepta un `EscalationResolver` (`grep`: 0 ocurrencias). Lo resuelve la extensión de core.

**(C) `tool-integrity` necesita un catálogo que alguien tiene que poblar.** Su
`ToolDefinitionCatalogPort` no se alimenta solo: el propio javadoc dice «the wiring layer registers
each decorated tool's definition at startup». El `InMemoryToolDefinitionCatalog` tiene un
`register(ToolDefinition)` que hoy nadie llama, y `McpToolDefinitionMapper.from(McpSchema.Tool)`
hace la traducción. Sin ese registro el guardrail no encuentra ninguna definición y su
`UnknownDefinitionAction` decide por defecto — un módulo cargado que responde siempre lo mismo.
Este es el único de los tres que exige código nuevo en el starter, no solo cableado.

**Lo que NO es un problema, contra lo que se temía:** `spring-jdbc` ya es `provided` en los tres
módulos que lo usan —audit, ratelimit y tool-integrity—, y los dos primeros llevan desde la v0.1.0
en el starter sin propagarlo. `postgresql` es `test` en todos. Añadir `tool-integrity` no cambia
nada para el consumidor. No hace falta `<optional>`.

## 3. Modelo de dominio

Ninguno nuevo. El starter solo contiene `infrastructure` (ARCHITECTURE.md §5).

## 4. Puertos

Ninguno nuevo. El starter consume los puertos `in` de cada módulo y publica implementaciones de
sus puertos `out`.

## 5. Adaptadores esperados

### 5.1 Seis clases `@AutoConfiguration` nuevas

Paquete `starter.infrastructure`, una por módulo, siguiendo el patrón de las cinco existentes:

| Clase | Publica |
|---|---|
| `GuardrailsToolIntegrityAutoConfiguration` | `ToolBaselineStorePort` (in-memory), `ToolDefinitionCatalogPort`, `VerifyToolIntegrityUseCase`, `ToolIntegrityGuardrail` |
| `GuardrailsCredentialLeakAutoConfiguration` | `SecretPatternSetPort`, los dos casos de uso, `CredentialLeakGuardrail` y **`CredentialLeakResultGuardrail`** |
| `GuardrailsEgressAutoConfiguration` | `EgressPolicyPort`, `CheckEgressDestinationUseCase`, `EgressGuardrail` |
| `GuardrailsAnomalyAutoConfiguration` | `InvocationHistoryPort`, `DetectAnomalyUseCase`, `AnomalyGuardrail` |
| `GuardrailsApprovalAutoConfiguration` | `ApprovalRequestPort`, `RequestApprovalService` (una instancia bajo `RequestApprovalUseCase` y **`ResolveApprovalUseCase`**, §5.5), `ApprovalGate` |
| `GuardrailsTrifectaAutoConfiguration` | `SessionCapabilityPort`, `AssessTrifectaService` (una instancia bajo `AssessTrifectaUseCase` y **`ResetSessionUseCase`**, §5.5), `SessionIdResolver`, `TrifectaGuardrail` |

Reglas comunes, idénticas a las cinco existentes:

- `@ConditionalOnProperty(name = "mcp.guardrails.enabled", matchIfMissing = true)` a nivel de clase.
- `@EnableConfigurationProperties(GuardrailsXProperties.class)`.
- Cada bean con `@ConditionalOnMissingBean`, para que el operador pueda sustituir cualquiera.
  Excepción documentada en §5.5: en `approval-gate` y `trifecta-correlator` el back-off es
  conjunto para las dos interfaces de su servicio, no bean a bean.
- El bean del guardrail, además, con
  `@ConditionalOnProperty(name = "mcp.guardrails.<prefijo>.enabled", matchIfMissing = true)`.

Las seis se registran en `META-INF/spring/...AutoConfiguration.imports`.

**Sobre la property maestra:** `mcp.guardrails.enabled=false` apaga las once autoconfiguraciones,
incluida la de core. Sin `EvaluateToolInvocationUseCase` ni `GuardrailToolSpecificationPostProcessor`
las tools no se decoran, así que nada se evalúa. Es coherente y ya es el comportamiento actual; se
documenta, no se cambia.

### 5.2 Cadena de salida: `GuardrailToolSpecificationPostProcessor` (bloqueo A)

Recibe además un `ObjectProvider<EvaluateToolResultUseCase>` y usa la sobrecarga de 5 o 6
argumentos. `GuardrailsCoreAutoConfiguration` publica el bean del caso de uso de salida:

```java
@Bean @ConditionalOnMissingBean
public EvaluateToolResultUseCase evaluateToolResultUseCase(List<ResultGuardrail> guardrails) {
  return new ResultGuardrailChain(guardrails);
}
```

Con la lista vacía —ningún `ResultGuardrail` registrado— la cadena devuelve `PassThrough`, así que
el comportamiento no cambia para quien no cablee ninguno.

### 5.3 Escalación: el resolver opcional (bloqueo B)

El post-processor recibe `ObjectProvider<EscalationResolver>` y pasa
`escalationResolver.getIfAvailable()` a la sobrecarga de 6 argumentos. Sin `approval-gate` en el
classpath el provider devuelve `null` y el handler produce el error histórico.

**Aviso al arrancar:** si hay algún guardrail capaz de emitir `Escalate` y **no** hay
`EscalationResolver`, el starter registra un warning. Hoy esa combinación devuelve un error al
agente sin que nadie lo pida ni lo vea — el operador cree tener escalación y tiene un fallo. Es el
mismo criterio de §5.2 que ya aplican `trifecta-correlator` y `anomaly-detector`.

### 5.4 Catálogo de tool-integrity (bloqueo C)

`GuardrailToolSpecificationPostProcessor` ya intercepta cada `SyncToolSpecification` al decorarla:
es el único punto que ve todas las definiciones. Al decorar, registra la definición en el catálogo
si hay un `ToolDefinitionCatalogPort` que lo admita.

Para no acoplar el post-processor a `tool-integrity` —el starter puede depender de todos, pero el
core no—, el registro se hace mediante un `ToolDefinitionRegistrar` funcional que el starter
publica **solo cuando `tool-integrity` está activo**; sin él, un no-op. El post-processor lo recibe
como `ObjectProvider` y lo invoca por cada tool decorada.

### 5.5 Los lados humanos

`approval-gate` y `trifecta-correlator` exponen casos de uso que necesitan una persona:
`ResolveApprovalUseCase` (listar y resolver aprobaciones) y `ResetSessionUseCase` (listar sesiones
bloqueadas y reabrirlas). El starter **publica esos beans** para que el operador los inyecte en su
propio controlador; no expone endpoints (§1, no-goals). Ambos READMEs ya muestran el controlador
REST mínimo y advierten de que esos endpoints deciden quién puede levantar un bloqueo.

Nota: `RequestApprovalService` implementa las dos interfaces y `AssessTrifectaService` también, así
que cada uno se publica como una sola instancia expuesta bajo dos tipos, no como dos objetos con
estado separado — que sería un fallo silencioso, porque el humano resolvería sobre un canal
distinto del que espera la invocación.

Eso obliga a declarar el bean con el tipo del servicio y a condicionarlo sobre **las dos
interfaces a la vez**:

```java
@Bean
@ConditionalOnMissingBean({AssessTrifectaUseCase.class, ResetSessionUseCase.class})
public AssessTrifectaService assessTrifectaService(...) { ... }
```

El operador que inyecta —el caso normal— no nota diferencia: pide `ResolveApprovalUseCase` y
Spring resuelve por tipo contra la instancia real. Lo que cambia es la **sustitución**: aportar
una de las dos interfaces retira también la otra, porque el back-off de `@ConditionalOnMissingBean`
multi-tipo es total. Ver decisión de diseño 8.

## 6. Configuración Spring Boot

**Ninguna property nueva.** El starter respeta las que cada módulo ya define:

| Prefijo | Módulo |
|---|---|
| `mcp.guardrails.tool-integrity` | tool-integrity |
| `mcp.guardrails.credential-leak` | credential-leak-guard |
| `mcp.guardrails.egress` | egress-control |
| `mcp.guardrails.anomaly` | anomaly-detector |
| `mcp.guardrails.approval` | approval-gate |
| `mcp.guardrails.trifecta` | trifecta-correlator |

Verificado: los seis tienen su propio `enabled`.

## 7. Dependencias Maven propuestas

Seis dependencias nuevas en `spring-boot-starter/pom.xml`, todas del propio proyecto y en scope
`compile`:

| Dependencia | Por qué |
|---|---|
| `io.github.tikyparkinson:guardrails-tool-integrity` | El guardrail de integridad y su catálogo. |
| `io.github.tikyparkinson:guardrails-credential-leak-guard` | Los guardrails de entrada y de salida. |
| `io.github.tikyparkinson:guardrails-egress-control` | El guardrail de allowlist de destinos. |
| `io.github.tikyparkinson:guardrails-anomaly-detector` | El guardrail de comportamiento anómalo. |
| `io.github.tikyparkinson:guardrails-approval-gate` | El `EscalationResolver` y su canal. |
| `io.github.tikyparkinson:guardrails-trifecta-correlator` | El correlador de sesión. |

Ninguna dependencia externa nueva: `spring-jdbc` es `provided` y `postgresql` es `test` en los
módulos que los usan, así que no se propagan al consumidor.

## 8. Diagrama

```
                    mcp.guardrails.enabled (maestra)
                                 │
        ┌────────────────────────┴─────────────────────────┐
        │            11 @AutoConfiguration                 │
        │  cada una con su <modulo>.enabled propio         │
        └────────────────────────┬─────────────────────────┘
                                 │ publica beans
      ┌──────────────────────────┼──────────────────────────┐
      ▼                          ▼                          ▼
 List<Guardrail>          List<ResultGuardrail>      EscalationResolver
 (10 implementaciones)     (credential-leak)          (approval-gate)
      │                          │                          │
      └──────────┬───────────────┴──────────────────────────┘
                 ▼
      GuardrailToolSpecificationPostProcessor
      decorate(spec, useCase, resultUseCase, agentIdResolver, clock, resolver)
                 │  + registra la definición en el catálogo (tool-integrity)
                 ▼
      cada SyncToolSpecification queda guardada
```

## 9. Decisiones de diseño

1. **Una autoconfiguración por módulo, no una gigante.** Es el patrón que ya siguen las cinco
   existentes, y permite que un operador desactive o sustituya un módulo sin tocar los demás. Una
   clase única obligaría a `@ConditionalOnClass` por bean y sería ilegible.

2. **La cadena de salida se arregla aunque solo la use un módulo.** Podría parecer desproporcionado
   cambiar el post-processor por `credential-leak-guard`, pero el SPI de salida se especificó y
   construyó entero en el módulo 8 y hasta hoy **nunca se ha ejecutado en producción**. Cablearlo
   es terminar un trabajo, no ampliarlo.

3. **El catálogo se puebla desde el post-processor, no desde un `ApplicationRunner`.** Un runner se
   ejecuta después de que el contexto arranque, y la primera invocación podría llegar antes de que
   el catálogo estuviera lleno: el guardrail vería una definición desconocida y actuaría según
   `UnknownDefinitionAction` sin que nada estuviera mal. El post-processor registra en el mismo
   momento en que decora, así que no hay ventana.

4. **El registro pasa por un `ToolDefinitionRegistrar` funcional.** El post-processor vive en el
   starter, que sí puede depender de `tool-integrity`, pero acoplarlo directamente haría que el
   cableado de todas las tools dependiera de un módulo concreto. Un funcional que por defecto es
   no-op mantiene el post-processor ignorante de quién escucha.

5. **Se avisa al arrancar cuando hay escalación sin resolver.** Un `Escalate` sin `approval-gate`
   devuelve hoy un error al agente. No es incorrecto —es fail-closed— pero es indistinguible de un
   fallo, y el operador que activó `trifecta-correlator` cree tener aprobación humana. §5.2 exige
   decirlo en voz alta.

6. **Los casos de uso humanos se publican como beans, no como endpoints.** Ningún módulo del
   proyecto trae transporte, y añadirlo aquí ataría el starter a una stack web. Publicar el bean
   deja al operador elegir REST, CLI o bot, y los READMEs ya documentan el controlador mínimo con
   su aviso de seguridad.

7. **Sin `<optional>` para tool-integrity.** Se evaluó, porque arrastra `spring-jdbc`. Verificado
   que esa dependencia es `provided` en los tres módulos que la usan —incluidos audit y ratelimit,
   en el starter desde la v0.1.0— y que `postgresql` es `test`. No se propaga nada, así que
   marcarlo opcional solo añadiría un caso raro: un consumidor con el módulo a medias.

8. **En approval-gate y trifecta-correlator, sustituir un caso de uso obliga a aportar el otro.**
   Es la única excepción a la regla de §5.1 («cada bean con `@ConditionalOnMissingBean`, para que
   el operador pueda sustituir cualquiera») y conviene decirla en voz alta porque contradice esa
   frase.

   El origen es §5.5: un solo servicio con estado compartido —el mapa de sesiones, el de
   aprobaciones pendientes— expuesto bajo dos interfaces. Partirlo en dos beans partiría el
   estado, así que el bean se declara con el tipo del servicio y se condiciona sobre ambas
   interfaces. El back-off de `@ConditionalOnMissingBean` multi-tipo no es por bean sino total:
   basta con que **una** de las dos esté presente para que la autoconfiguración se retire entera.
   Verificado registrando solo `AssessTrifectaUseCase`: el contexto arranca, el guardrail consume
   el bean del operador, y `getBeanNamesForType(ResetSessionUseCase.class)` devuelve `[]`.

   Se consideró la alternativa —dos `@Bean`, uno por interfaz, el segundo devolviendo la misma
   instancia que el primero— y se descartó: registra el mismo objeto dos veces, de modo que
   sustituir una interfaz deja dos candidatos del mismo tipo y **el arranque falla** con
   `expected single matching bean but found 2`. Es decir, la variante que parecía más flexible es
   la que rompe, y lo hace en el arranque del operador, no aquí.

   Que la retirada sea total es además lo correcto para estos dos módulos. Si alguien trae su
   propia correlación de sesiones, un `ResetSessionUseCase` heredado seguiría operando sobre un
   mapa que ya no usa nadie: un botón de «reabrir sesión» que no reabre nada, en silencio. Es
   preferible que el bean falte —error de arranque inmediato y legible— a que mienta. El
   `SessionCapabilityPort` in-memory sí se sigue creando y queda huérfano, pero es un mapa vacío
   sin coste ni efecto.

   Para el operador que solo inyecta, que es el caso que documentan los READMEs, nada de esto
   cambia.
