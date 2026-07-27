# guardrails-audit-full-coverage — F-1 y F-1b del informe de validación de 0.2.0

> Depende de `guardrails-core-decision-detail-spec.md`, que debe estar implementado y aprobado
> **antes** de empezar este.
>
> Toca `guardrails-authz`, `guardrails-injection-guard`, `guardrails-ratelimit`, `guardrails-audit`
> y `spring-boot-starter`.

## 1. Problema y alcance

Dos hallazgos que son una sola pieza de trabajo.

**F-1.** ARCHITECTURE.md §5 dice que ningún `guardrails-*` importa a otro como dependencia Maven.
`guardrails-authz`, `guardrails-injection-guard` y `guardrails-ratelimit` declaran
`guardrails-audit`. Los tres son de v0.1.0; los seis módulos posteriores cumplen la regla.

**F-1b.** Como consecuencia, solo 4 de 9 guardrails emiten al bus. Una llamada bloqueada por
`credential-leak` deja este rastro:

```
store_note   authz    DECISION_ALLOW    default
store_note   audit    TOOL_INVOKED
```

No hay registro del bloqueo. El trail no está incompleto: **miente por omisión**, porque se lee
como una llamada permitida y ejecutada.

El requisito del informe de validación de 0.2.0 es cobertura total. La solución intuitiva —dar a los cinco
módulos silenciosos una dependencia de `guardrails-audit`— propagaría la violación de F-1 y está
descartada. §5 ya dice dónde va esto:

> A bridge onto another module's store is an adapter, and it lives in `spring-boot-starter`, which
> legitimately depends on every module. It is never a Maven dependency between guardrails.

**Alcance de la auditoría, cerrado**

| Debe quedar auditado | Hoy |
|---|---|
| La decisión de los nueve guardrails, `Allow` incluidos | 4 de 9 |
| El resultado de la cadena de salida: redacción, bloqueo, paso limpio | ninguno |
| Quién aprobó o rechazó una escalación, cuándo y por qué | ninguno |
| Escalaciones caducadas sin respuesta, distinguibles de las decididas | ninguno |
| Guardrails aportados por el operador | no cubierto |

**No-goals**

- No se cambia el `AuditLogStorePort` ni el adaptador in-memory. El almacén sirve tal cual.
- No se persisten argumentos. `guardrails-audit` deliberadamente no los guarda (§5) y esto no lo
  cambia: convertiría el log de auditoría en el sitio donde acaban filtrándose los secretos que
  `credential-leak` bloquea.
- No se añade un adaptador JDBC de auditoría. Fuera de alcance.
- No se toca el orden de la cadena ni ninguna decisión: esto observa, no decide.

## 2. Modelo de dominio

En `guardrails-audit`, cuatro valores nuevos en un enum existente:

```java
// io.github.tikyparkinson.mcpguardrails.audit.domain.AuditEventType
public enum AuditEventType {
  TOOL_INVOKED,
  DECISION_ALLOW,
  DECISION_DENY,
  DECISION_ESCALATE,
  RESULT_PASS_THROUGH,   // nuevo — la cadena de salida no tocó la respuesta
  RESULT_REDACTED,       // nuevo — se redactó algo antes de devolverla
  RESULT_BLOCKED,        // nuevo — la respuesta no llegó al agente
  APPROVAL_RESOLVED      // nuevo — una escalación terminó: por persona o por caducidad
}
```

Añadir valores es aditivo aquí: **verificado que no existe ningún `switch` sobre `AuditEventType`
en `main`**, así que ninguno se vuelve no exhaustivo.

Ningún record nuevo. `NewAuditEvent(agentId, toolName, emittedBy, type, detail)` ya tiene la forma
necesaria.

## 3. Puertos

**Ninguno nuevo.** Se usan los existentes:

- `RecordAuditEventUseCase.publish(NewAuditEvent)` — `guardrails-audit`, `application.port.in`.
- `EvaluateToolInvocationUseCase.evaluate(ToolInvocationContext) : ChainVerdict` — core.
- `EvaluateToolResultUseCase` — core.
- `RequestApprovalUseCase` / `ResolveApprovalUseCase` — `guardrails-approval-gate`.

Que no haga falta ningún puerto nuevo es la señal de que el diseño encaja: todo lo que se necesita
ya está expuesto, solo faltaba alguien que lo observara desde una capa autorizada a verlo todo.

## 4. Caso de uso

No hay interactor nuevo. Hay tres decoradores, todos en `spring-boot-starter`.

### 4.1 Auditoría del veredicto de entrada

`AuditingEvaluateToolInvocation implements EvaluateToolInvocationUseCase`, envolviendo al real:

1. Delega en el `EvaluateToolInvocationUseCase` subyacente y obtiene el `ChainVerdict`.
2. Por cada `GuardrailEvaluation` del veredicto, publica un evento:
   - `emittedBy` = `evaluation.guardrailName()` — el guardrail que decidió, no el auditor.
   - `type` = `DECISION_ALLOW` / `DECISION_DENY` / `DECISION_ESCALATE` según la decisión.
   - `detail` = el `reason` de la decisión, que tras `guardrails-core-decision-detail` existe
     también para `Allow`.
3. Devuelve el veredicto **intacto**.
4. Si publicar falla, se registra y se continúa. Un bus de auditoría caído no puede convertir una
   llamada permitida en un error: sería un guardrail de auditoría denegando por su cuenta.

Se salta la evaluación cuyo `guardrailName` es `audit`: ese guardrail ya emitió su `TOOL_INVOKED`,
y su `Allow` incondicional no aporta nada. Ver decisión de diseño 3.

### 4.2 Auditoría del veredicto de salida

`AuditingEvaluateToolResult implements EvaluateToolResultUseCase`, misma forma, con
`RESULT_PASS_THROUGH` / `RESULT_REDACTED` / `RESULT_BLOCKED`.

**Regla dura:** el `detail` de un `RESULT_REDACTED` es el `reason` del `Redact`, **nunca**
`sanitizedContents`. Ese campo lleva el texto de la respuesta, y volcarlo al log de auditoría
metería en el trail justo los secretos que la redacción acaba de quitar. `test-engineer` debe
cubrir esto explícitamente con un secreto reconocible y aserción de que no aparece.

### 4.3 Auditoría de las decisiones humanas

`AuditingRequestApproval`, decorando `RequestApprovalUseCase`:

1. Delega y espera la `ApprovalDecision`, que llega cuando una persona decide o cuando caduca.
2. Publica un `APPROVAL_RESOLVED` con `emittedBy = "approval-gate"` y un `detail` que distingue
   los tres finales:
   - `Approved(approver)` → `approved by <approver>`
   - `Rejected(approver, reason)` con `approver != "system"` → `rejected by <approver>: <reason>`
   - `Rejected` con `approver == Rejected.SYSTEM` → `not approved, no person involved: <reason>`

La distinción entre «lo decidió alguien» y «no lo decidió nadie» no es cosmética: una denegación
que nadie tomó falsea cualquier revisión posterior si se lee como deliberada. `Rejected.SYSTEM` ya
existe para eso.

**Corrección respecto a la primera versión de este spec.** Decía que `SYSTEM` significa caducidad.
Es falso, y se vio al ejecutarlo: `Rejected.byQuota()` también usa `SYSTEM`, y una cuota agotada no
es una expiración —el canal está saturado y la petición **nunca llegó a una persona**, mientras que
en una expiración sí llegó y nadie contestó. Son dos problemas operativos distintos. El dominio de
`approval-gate` no los separa más allá del texto del motivo, así que el detalle dice que no hubo
persona y deja que el motivo diga cuál de los dos fue, en vez de adivinar por el texto y etiquetar
uno como el otro. Distinguirlos estructuralmente exigiría tocar el dominio de `approval-gate`, lo
cual es otra pasada de `spec-architect` y queda fuera de alcance aquí.

Un solo evento por resolución, al final. Auditar también la petición duplicaría cada escalación en
el log sin añadir información: el `DECISION_ESCALATE` del guardrail que la provocó ya está en el
trail por 4.1, con su motivo.

## 5. Adaptadores esperados

Todos en `spring-boot-starter`, paquete `infrastructure` — es un puente entre módulos, y §5 dice
que eso es un adaptador del starter.

| Clase | Decora | Cuándo se registra |
|---|---|---|
| `AuditingEvaluateToolInvocation` | `EvaluateToolInvocationUseCase` | solo si hay un `RecordAuditEventUseCase` en el contexto |
| `AuditingEvaluateToolResult` | `EvaluateToolResultUseCase` | ídem |
| `AuditingRequestApproval` | `RequestApprovalUseCase` | solo si además está `approval-gate` |

Sin `guardrails-audit` en el classpath, o con `mcp.guardrails.audit.enabled=false`, los tres se
retiran y la cadena funciona igual sin auditar: la auditoría es observación, nunca un requisito
para decidir.

**Cómo se decora sin ciclo de beans.** El bean auditor no puede pedir por tipo aquello que él mismo
publica. `adapter-builder` debe resolverlo con un `BeanPostProcessor` o con un `@Bean @Primary` que
reciba el delegado marcado con un qualifier, y **verificar el arranque real del contexto**, no
solo que compile: es exactamente la clase de fallo que no aparece hasta que Spring construye el
grafo.

### Cambios en los tres módulos infractores (F-1)

`guardrails-authz`, `guardrails-injection-guard` y `guardrails-ratelimit`:

- Se elimina `<dependency>guardrails-audit</dependency>` de su `pom.xml`.
- Sus guardrails pierden el parámetro `RecordAuditEventUseCase` del constructor y dejan de publicar.
- `AuthzGuardrail` conserva su `source` porque ahora viaja en `new Allow(source)`, gracias al spec
  de core.

**Verificación obligatoria de que no se pierde nada:** capturar los eventos que estos tres emiten
hoy y comprobar que el trail sigue conteniendo los mismos —mismo `emittedBy`, mismo `type`— más los
que faltaban. Ninguno desaparece. **El `detail` sí cambia de forma en dos de los tres**, y la
decisión de diseño 10 explica por qué se acepta.

## 6. Configuración Spring Boot

Ninguna property nueva. Una cambia de valor por defecto:

| Property | Antes | Ahora | Por qué |
|---|---|---|---|
| `mcp.guardrails.audit.in-memory-max-events` | `1000` | `5000` | Medido sobre el contexto real: una invocación permitida deja hoy **2** eventos y pasa a dejar **9** (ocho guardrails que deciden + `TOOL_INVOKED`; el `Allow` del propio guardrail `audit` se omite). Con 1000 la ventana caería de 500 invocaciones a ~110. Con 5000 se conservan ~550 |

El README de `guardrails-audit` debe explicar la aritmética —eventos por invocación × invocaciones
retenidas— y decir que el adaptador in-memory es un descarte silencioso: en producción, un log de
auditoría que se pierde al reiniciar y se trunca sin avisar no es un log de auditoría.

## 7. Dependencias Maven propuestas

| Módulo | Cambio | Por qué |
|---|---|---|
| `guardrails-authz` | **quita** `guardrails-audit` | Cierra F-1 |
| `guardrails-injection-guard` | **quita** `guardrails-audit` | Cierra F-1 |
| `guardrails-ratelimit` | **quita** `guardrails-audit` | Cierra F-1 |
| `guardrails-audit` | sin cambios | Solo gana valores de enum |
| `spring-boot-starter` | sin cambios | Ya depende de los once módulos |

Ninguna dependencia nueva en todo el trabajo. Que el resultado neto sea **restar** tres
dependencias es la confirmación de que el diseño va en la dirección correcta.

## 8. Diagrama del hexágono

```
                          spring-boot-starter (infrastructure)
                          ┌──────────────────────────────────────────┐
   tool call ────────────▶│ AuditingEvaluateToolInvocation           │
                          │   delega ─▶ GuardrailChain (core)        │
                          │   recorre ChainVerdict.evaluations       │
                          │   9 × NewAuditEvent(emittedBy=guardrail) │──┐
                          ├──────────────────────────────────────────┤  │
   tool result ──────────▶│ AuditingEvaluateToolResult               │  │
                          │   RESULT_PASS_THROUGH/REDACTED/BLOCKED   │──┤
                          ├──────────────────────────────────────────┤  │
   human decision ───────▶│ AuditingRequestApproval                  │  │
                          │   APPROVAL_RESOLVED (persona | caducidad)│──┤
                          └──────────────────────────────────────────┘  │
                                                                        ▼
                                            guardrails-audit: RecordAuditEventUseCase
                                                                        │
                                                                        ▼
                                                    AuditLogStorePort ─▶ in-memory (5000)

   authz / injection-guard / ratelimit  ──✗──▶  guardrails-audit      (dependencia eliminada, §5)
```

## Decisiones de diseño

1. **Un evento por guardrail, no uno por veredicto.** Un único evento agregado sería más barato en
   volumen, pero `NewAuditEvent` tiene un `emittedBy` y un `type` por evento: agregar obligaría a
   meter nueve decisiones dentro de un `detail` de texto, es decir, a inventar un formato dentro de
   un campo libre que nadie podría consultar. Se paga el volumen y se sube el default en su lugar.

2. **`emittedBy` es el guardrail, no el auditor.** El evento dice `credential-leak DECISION_DENY`,
   no `verdict-auditor DECISION_DENY`. Quien lea el trail quiere saber quién decidió; que el
   registro lo haga otro es un detalle de implementación que no debe filtrarse al log. Además, así
   los eventos son indistinguibles de los que authz emite hoy, que es lo que permite migrar sin
   romper a nadie que ya consuma el trail.

3. **El `Allow` del propio guardrail `audit` no se audita.** Emitiría `audit DECISION_ALLOW` justo
   detrás de su `TOOL_INVOKED`, siempre, sin excepción y sin información. Se omite por ruido. La
   consecuencia —que `audit` sea el único guardrail sin evento de decisión— se documenta en el
   README para que nadie lo lea como un fallo de cobertura.

4. **Se auditan también los `Allow`.** Multiplica el volumen y es deliberado: un trail donde solo
   aparece lo denegado no permite distinguir «el guardrail permitió» de «el guardrail no llegó a
   ejecutarse», y esa diferencia es justo la que hace falta cuando se investiga por qué algo pasó.

5. **La auditoría nunca cambia una decisión.** Los tres decoradores devuelven lo que recibieron y
   se tragan sus propios fallos. Un bus caído degrada la observabilidad, no la protección. Lo
   contrario —fallar cerrado— convertiría el almacén de auditoría en un punto único de caída para
   todo el servidor MCP.

6. **`sanitizedContents` no entra jamás en el log.** Es el texto ya saneado de la respuesta.
   Volcarlo al trail metería el contenido que la redacción acaba de limpiar en un almacén que
   normalmente tiene otra política de acceso. Solo se registra el `reason`.

7. **Un evento por resolución de aprobación, no dos.** La petición ya está en el trail como el
   `DECISION_ESCALATE` del guardrail que la provocó. Un `APPROVAL_REQUESTED` adicional duplicaría
   cada escalación sin añadir nada que no se pueda correlacionar por `(agentId, toolName)`.

8. **La caducidad se distingue del rechazo humano en el `detail`, no en el tipo.** Ambos son el
   mismo hecho —la invocación no se ejecutó— y comparten tipo `APPROVAL_RESOLVED`; lo que cambia es
   quién lo decidió. `Rejected.SYSTEM` ya marca esa diferencia en el dominio de `approval-gate`, y
   este spec la transporta en vez de duplicar el enum.

9. **El default de retención sube a 5000 en vez de dejarse en 1000.** Mantener 1000 habría reducido
   la ventana real de 500 invocaciones a 100 sin que nadie lo notara hasta necesitar el trail. Es
   el fallo más probable de todo este trabajo: no romper nada, y dejar el log cinco veces más corto
   en silencio.
10. **El `detail` de `injection-guard` y `ratelimit` cambia de forma, y se acepta.** Medido sobre el
    contexto real, antes y después:

    | Guardrail | Antes | Después |
    |---|---|---|
    | `authz` | `rule[0]` / `default` | **igual** |
    | `injection-guard` | `ignore-previous-instructions@text` | `malicious content detected in tool arguments (ignore-previous-instructions@q)` |
    | `ratelimit` | `count=6 limit=5 window=PT1M` | `rate limit exceeded for agent 'a' on tool 'search' (3/1 in PT1M)` |

    No se pierde ningún dato: el nuevo `detail` contiene todo lo que contenía el anterior. Lo que se
    pierde es el **formato**. El `detail` que estos módulos escribían era estructurado y pensado para
    consultar; el `reason` de la decisión es prosa pensada para que el agente entienda por qué se le
    ha denegado algo. Son dos campos con propósitos distintos que este trabajo unifica en uno.

    Se acepta porque la alternativa es peor de lo que arregla. Mantener ambos exigiría que
    `GuardrailDecision` llevara `reason` **y** `detail` por separado, es decir, una sexta extensión
    de `guardrails-core` con su propio spec, para que ocho de los nueve guardrails dejaran el segundo
    campo vacío. Y el `detail` estructurado solo existe hoy en `ratelimit`: `injection-guard` ya
    emitía texto libre y `authz` un identificador que no cambia.

    Lo que sí obliga es a avisar: quien tuviera un consumidor parseando `detail == "rule[0]"` en
    `authz` sigue igual, pero quien parseara `count=…` de `ratelimit` tiene que adaptarse. Va en el
    README de `guardrails-audit` y en el cuerpo de la PR, no solo aquí.

    Si más adelante alguien necesita un `detail` consultable por máquina, el sitio correcto no es
    este parche sino un campo estructurado en el evento de auditoría — y entonces se diseña una vez
    para los nueve, no se conserva por accidente en uno.


---

**Listo para `domain-builder`: implementar `docs/specs/guardrails-audit-full-coverage-spec.md`**,
después de `guardrails-core-decision-detail-spec.md`.
