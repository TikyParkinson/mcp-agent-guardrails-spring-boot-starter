# Spec — `guardrails-approval-gate` (módulo 11)

> **Prerequisito.** Requiere la extensión `guardrails-core-escalation-spi`
> ([spec](guardrails-core-escalation-spi-spec.md)), que se construye antes en esta misma rama.
> Sin ella este módulo no tiene dónde engancharse: ver §1 de aquel documento para la evidencia
> de por qué no puede ser un `Guardrail`.

## 1. Problema y alcance

Los guardrails ya saben decir "esto no me convence": `anomaly-detector` escala un agente en
bucle, `egress-control` puede escalar en vez de denegar. Pero hoy escalar y denegar producen el
mismo efecto —un error al agente—, así que la distinción es puramente nominal. Este módulo la
hace real: cuando la cadena resuelve `Escalate`, la invocación se **retiene** y no se ejecuta
hasta que una persona la apruebe o la rechace explícitamente.

Si nadie responde dentro del plazo configurado, la invocación se **deniega**. El silencio nunca
autoriza.

**No-goals.**

- No decide *cuándo* escalar: eso lo deciden los guardrails de la cadena. Este módulo solo
  ejecuta la consecuencia.
- No aporta interfaz de usuario. Expone la lista de solicitudes pendientes y una operación para
  resolverlas; conectarlo a REST, a un CLI o a Slack es del operador (§5.3 muestra el REST).
- No autentica ni autoriza al aprobador. Quién puede aprobar es responsabilidad del canal que el
  operador exponga, con sus propios mecanismos.
- No recuerda aprobaciones anteriores ni las reutiliza (§9, decisión 3).
- No depende de ningún módulo `guardrails-*` salvo `core` (ARCHITECTURE.md §5).

## 2. Modelo de dominio

Paquete `io.github.tikyparkinson.mcpguardrails.approval.domain`. JDK puro.

```java
/**
 * Identificador de una solicitud. Opaco y no adivinable: es lo que un aprobador presenta para
 * resolverla, así que un valor predecible permitiría a un tercero aprobar por él.
 * Invariantes: value no blank.
 * Factoría: ApprovalId.newId() -> UUID aleatorio.
 */
record ApprovalId(String value) {}

/**
 * Lo que se somete a decisión humana. Lleva los argumentos porque sin ellos nadie puede decidir
 * con criterio: aprobar "delete_table" sin saber qué tabla no es aprobar, es firmar en blanco.
 * Consecuencia asumida y documentada en §9 decisión 5: el canal de aprobación hereda la
 * sensibilidad de los argumentos.
 * Invariantes: id, requestedAt no null; agentId, toolName, reason no blank; arguments inmutable.
 */
record ApprovalRequest(
    ApprovalId id, String agentId, String toolName, Map<String, Object> arguments,
    String reason, Instant requestedAt) {}

/**
 * Cómo terminó una solicitud. Cerrado a dos casos: aprobada o no. "Pendiente" no aparece aquí
 * porque no es un desenlace — es la ausencia de uno, y modelarlo obligaría a todo consumidor a
 * tratar un tercer caso que en realidad significa "todavía no hay respuesta".
 */
sealed interface ApprovalDecision permits Approved, Rejected {}

/** Invariante: approver no blank. */
record Approved(String approver) implements ApprovalDecision {}

/**
 * Rechazo, sea explícito, por expiración del plazo o por saturación del canal. Los tres llegan
 * aquí a propósito (§9 decisión 2).
 * approver: quién rechazó, o SYSTEM cuando no hubo persona.
 * Invariantes: approver y reason no blank.
 * Factorías: Rejected.byTimeout(Duration), Rejected.byQuota(int)
 */
record Rejected(String approver, String reason) implements ApprovalDecision {
  static final String SYSTEM = "system";
}

/**
 * Parámetros del gate, validados una vez en construcción.
 * includeArguments: si los argumentos viajan en la solicitud (§9 decisión 5).
 * Invariantes: timeout positivo; maxPending >= 1; maxPendingPerAgent >= 1 y <= maxPending.
 */
record ApprovalPolicy(
    Duration timeout, int maxPending, int maxPendingPerAgent, boolean includeArguments) {}
```

## 3. Puertos

### 3.1 `RequestApprovalUseCase` — entrada (lado del guardrail)

Capa `application.port.in`. Lo invoca el adapter-in que implementa `EscalationResolver`.

```java
public interface RequestApprovalUseCase {
  /**
   * Somete la invocación a decisión humana y espera. Nunca devuelve null y nunca devuelve
   * Approved salvo que alguien lo haya aprobado explícitamente.
   */
  ApprovalDecision requestApproval(
      String agentId, String toolName, Map<String, Object> arguments, String reason,
      Instant requestedAt);
}
```

### 3.2 `ResolveApprovalUseCase` — entrada (lado humano)

Capa `application.port.in`. Lo invoca el canal que el operador exponga (REST, CLI, bot).

```java
public interface ResolveApprovalUseCase {

  /** Solicitudes en espera, de la más antigua a la más reciente. Nunca null. */
  List<ApprovalRequest> pendingApprovals();

  /**
   * Registra la decisión de una persona. Devuelve false si la solicitud no existe, ya expiró o
   * ya fue resuelta — la primera decisión gana y las siguientes no la sobrescriben.
   */
  boolean resolve(ApprovalId id, ApprovalDecision decision);
}
```

### 3.3 `ApprovalRequestPort` — salida (lo plugable)

Capa `application.port.out`. Es el canal de aprobación: dónde esperan las solicitudes y cómo se
despiertan. Lo implementa `InMemoryApprovalRequestAdapter` por defecto.

```java
public interface ApprovalRequestPort {

  /**
   * Publica la solicitud para que alguien la vea. Devuelve false si el canal no la admite
   * porque está saturado; el caso de uso lo traduce en rechazo, nunca en permiso.
   */
  boolean submit(ApprovalRequest request);

  /**
   * Espera a que la solicitud se resuelva, como máximo lo que dure el plazo.
   * Optional.empty() significa que expiró sin respuesta.
   */
  Optional<ApprovalDecision> awaitDecision(ApprovalId id, Duration timeout);

  /** Registra la decisión y despierta a quien espera. false si no había nada que resolver. */
  boolean resolve(ApprovalId id, ApprovalDecision decision);

  /** Solicitudes pendientes, de la más antigua a la más reciente. Nunca null. */
  List<ApprovalRequest> pending();
}
```

Los cuatro métodos existen porque hay dos lados: `submit` y `awaitDecision` los usa quien pide,
`resolve` y `pending` quien decide. Un puerto con un solo método `requestApproval(...)` obligaría
a cada implementación a resolver por su cuenta cómo se listan y despiertan las solicitudes, que
es justo lo que hay que poder sustituir.

## 4. Caso de uso — `RequestApprovalService`

Implementa `RequestApprovalUseCase` y `ResolveApprovalUseCase`. Recibe `ApprovalRequestPort` y
`ApprovalPolicy` por constructor.

`requestApproval(agentId, toolName, arguments, reason, requestedAt)`:

1. Construye `ApprovalRequest` con `ApprovalId.newId()`. Los argumentos se incluyen tal cual si
   `policy.includeArguments()`, y como mapa vacío si no.
2. `port.submit(request)`. Si devuelve `false` ⇒ **`Rejected.byQuota(maxPending)`**, sin esperar.
   Un canal saturado deniega de inmediato en vez de encolar más hilos bloqueados.
3. `port.awaitDecision(id, policy.timeout())`.
4. Presente ⇒ esa decisión, tal cual.
5. Vacío (expiró) ⇒ **`Rejected.byTimeout(policy.timeout())`**.

`pendingApprovals()` delega en `port.pending()`.
`resolve(id, decision)` delega en `port.resolve(id, decision)`.

**No hay rama que produzca `Approved` sin que alguien lo haya escrito.** Es la propiedad central
del módulo y debe ser evidente leyendo el método, no deducible.

## 5. Adaptadores esperados

### 5.1 Adapter-in: `ApprovalGate implements EscalationResolver`

Paquete `approval.adapter.in.escalation`.

- `resolve(context, verdict)`:
  1. Extrae el motivo del `Escalate` de `verdict.finalDecision()`.
  2. Llama a `useCase.requestApproval(...)` con los datos del `ToolInvocationContext`, incluido
     `occurredAt` (no se lee un reloj aquí: el instante lo trae el contexto).
  3. `Approved(who)` ⇒ `new ApprovedExecution(who)`.
  4. `Rejected(who, why)` ⇒ `new RejectedExecution(why + " (by " + who + ")")`.
- No tiene `order()`: **no es un `Guardrail`** y no participa en la cadena. Se invoca una sola
  vez, después de que la cadena entera haya resuelto `Escalate`.

### 5.2 Adapter-out por defecto: `InMemoryApprovalRequestAdapter`

Paquete `approval.adapter.out.channel`.

- `ConcurrentHashMap<ApprovalId, PendingApproval>`, donde cada `PendingApproval` guarda la
  solicitud y un `CompletableFuture<ApprovalDecision>` que aún no se ha completado.
- `submit`: comprueba las dos cuotas y registra, **atómicamente** respecto a otros `submit`. Si
  cualquiera de las dos está llena devuelve `false` sin registrar nada.
- `awaitDecision`: `decision.get(timeout)`. Al volver —resuelta o expirada— **retira la solicitud
  del mapa** en ambos casos: una solicitud cuyo solicitante ya se marchó no puede quedarse
  ocupando cuota ni aparecer como pendiente ante un humano que ya no puede influir en nada.
- `resolve`: `decision.complete(...)`, que devuelve `true` solo para el primero que llega. Una
  segunda llamada devuelve `false` y no altera la decisión ya tomada.
- `pending`: copia ordenada por `requestedAt`.

**Regla dura: nunca esperar con un lock tomado.** La espera no puede ejecutarse dentro de un
bloque `synchronized` ni con ningún lock del adaptador retenido. La sección crítica de `submit` y
`resolve` dura microsegundos; la espera dura minutos, y meterla dentro serializaría todas las
aprobaciones: una sola invocación pendiente bloquearía a las demás durante el plazo completo,
convirtiendo `max-pending` en un adorno. Es la diferencia con
`InMemoryInvocationHistoryAdapter` de `anomaly-detector`, donde sincronizar es correcto
precisamente porque allí nada espera. Que Java 25 ya no pinee el hilo portador dentro de
`synchronized` (JEP 491) no cambia nada: el problema aquí no es el pineo, es la exclusión mutua.

Limitación real, que va al README: las solicitudes viven en este proceso. Al reiniciar, toda
espera en curso se pierde; como las llamadas MCP que esperaban mueren con el proceso, no queda
ninguna aprobación huérfana, pero tampoco sobrevive ninguna. Y detrás de un balanceador, la
solicitud solo es visible en la réplica que la creó: el canal humano debe apuntar a esa réplica o
sustituirse por un adaptador compartido.

### 5.3 Canal humano — no se incluye, se documenta

El módulo **no trae** `spring-web` ni ningún transporte: ningún módulo del proyecto lo hace, y
atarlo a una stack web limitaría dónde se puede usar. El README muestra el controlador REST
mínimo sobre `ResolveApprovalUseCase` —listar pendientes, aprobar, rechazar— para que el operador
lo copie, junto con el aviso de que ese endpoint decide quién puede aprobar y debe protegerse en
consecuencia.

## 6. Configuración Spring Boot

Prefijo `mcp.guardrails.approval`. Record `GuardrailsApprovalProperties` con `@ConstructorBinding`
y `@DefaultValue` en cada parámetro.

| Propiedad | Tipo | Default | Significado |
|---|---|---|---|
| `enabled` | `boolean` | `true` | Registra el gate. Sin él, una escalación devuelve error como antes. |
| `timeout` | `Duration` | `PT2M` | Cuánto se retiene la invocación esperando respuesta. Al expirar, se deniega. |
| `max-pending` | `int` | `20` | Solicitudes simultáneas admitidas. Al superarlo se deniega sin esperar. |
| `max-pending-per-agent` | `int` | `5` | Tope por agente. Debe ser ≤ `max-pending`. |
| `include-arguments` | `boolean` | `true` | Si los argumentos de la invocación viajan en la solicitud. |

```yaml
mcp:
  guardrails:
    approval:
      timeout: PT2M
      max-pending: 20
      max-pending-per-agent: 5
```

**Por qué dos cuotas.** La global protege el pool de hilos del servidor: cada espera retiene uno.
La de por agente evita que un solo agente en bucle —justo lo que `anomaly-detector` escala— llene
la cuota global y deje sin canal de aprobación a todos los demás, que es un DoS efectivo contra
el propio mecanismo de seguridad.

**Por qué `max-pending` es 20 y no más.** El coste de una espera depende del modelo de hilos del
servidor, que este módulo no controla. Medido en Java 25: diez mil esperas concurrentes sobre
hilos virtuales ocupan unos 30 MB, así que ahí el límite podría ser órdenes de magnitud mayor.
Pero sobre hilos de plataforma —lo que hay con Tomcat si no se activa
`spring.threads.virtual.enabled`— el pool ronda los 200, y retener 100 sería quedarse con la
mitad del servidor. El default tiene que ser seguro en el peor caso; quien corra sobre hilos
virtuales puede subirlo mucho, y el README lo explica.

**Sobre el plazo.** Debe ser menor que el timeout del cliente MCP. Si el cliente se rinde antes,
la invocación queda esperando a un aprobador cuya respuesta ya no puede llegar a nadie.

**Sobre `include-arguments`.** El default es `true` porque sin argumentos la aprobación es una
firma en blanco (§9 decisión 5). Se puede desactivar cuando el canal de aprobación es menos
confiable que la invocación misma; entonces el aprobador ve agente, herramienta y motivo, y
decide con eso.

## 7. Dependencias Maven propuestas

| Dependencia | Scope | Por qué |
|---|---|---|
| `io.github.tikyparkinson:guardrails-core` | compile | `EscalationResolver`, `EscalationOutcome`, `ToolInvocationContext` y `ChainVerdict`. |
| `org.springframework.boot:spring-boot` (BOM 4.1.0) | provided | Única anotación usada: `@ConfigurationProperties` (+ `@ConstructorBinding`/`@DefaultValue`) en `infrastructure`. |
| `org.junit.jupiter:junit-jupiter` (BOM 6.1.2) | test | Tests unitarios. |
| `org.mockito:mockito-core` (5.23.0) | test | Doble de `ApprovalRequestPort` en los tests del servicio. |

Ninguna dependencia nueva para el proyecto. Ningún módulo `guardrails-*` distinto de core. Sin
store real ⇒ sin Testcontainers.

## 8. Diagrama del hexágono

```
   guardrails-core                          canal humano (REST/CLI/bot, del operador)
   GuardedToolCallHandler                                    │
   final = Escalate                                          │
          │                                                  │
          ▼  EscalationResolver                              ▼  ResolveApprovalUseCase
  ┌───────────────────┐                          ┌──────────────────────┐
  │   ApprovalGate    │  adapter-in              │  (adaptador propio)  │
  └─────────┬─────────┘                          └──────────┬───────────┘
            │ RequestApprovalUseCase                        │
            ▼                                               ▼
       ┌──────────────────────────────────────────────────────────┐
       │              RequestApprovalService                      │
       │  submit -> saturado? Rejected.byQuota                    │
       │  await  -> expirado? Rejected.byTimeout                  │
       └─────────────────────────┬────────────────────────────────┘
                                 │  ApprovalRequestPort (out)
                                 ▼
                 ┌────────────────────────────────┐
                 │ InMemoryApprovalRequestAdapter │  (sustituible)
                 │  mapa + CompletableFuture      │
                 └────────────────────────────────┘

   domain: ApprovalId · ApprovalRequest · ApprovalDecision (Approved | Rejected) · ApprovalPolicy
```

## 9. Decisiones de diseño

1. **La extensión de core es inevitable, no una comodidad.** Verificado en el código, no supuesto:
   `DecisionCombiner` toma el primer `Escalate` y ningún guardrail posterior puede rebajarlo a
   `Allow`, así que una aprobación expresada como decisión de la cadena sería ignorada por
   construcción. Ver §1 del spec de la extensión.

2. **Expiración, saturación y rechazo explícito son el mismo desenlace.** Los tres producen
   `Rejected`, que es lo que exige el fail-closed. Separarlos en tipos distintos obligaría a cada
   consumidor a tratar tres casos que no le cambian la conducta, y abriría la puerta a que alguien
   tratase "expiró" como algo más leve que "lo rechazaron". Lo que sí cambia entre ellos —qué
   contarle al operador— viaja en el texto del motivo.

3. **Sin caché de aprobaciones.** Se descartó recordar "este agente ya tiene permiso para esta
   invocación" y dejar pasar las repeticiones. Reutilizar una aprobación exige decidir cuándo dos
   invocaciones son "la misma", y equivocarse en esa comparación significa ejecutar algo que nadie
   aprobó: quien autoriza `delete_table(staging)` no ha autorizado `delete_table(prod)`. Una
   decisión, una invocación. El coste —un agente en bucle genera muchas solicitudes— se acota con
   las cuotas, que fallan cerrado, en vez de con una comparación que puede fallar abierto.

   Se estudió también la variante asimétrica: propagar solo los **rechazos** a las solicitudes
   pendientes equivalentes, que sí sería fail-closed —equivocarse denegaría de más, nunca de
   menos—. Se descarta por coste/beneficio, no por riesgo: exigiría una huella de argumentos,
   tercera forma canónica del proyecto, para ahorrarle a una persona rechazar como mucho
   `max-pending-per-agent` veces. Si algún día el tope por agente sube mucho, esta es la vía
   correcta a reconsiderar.

   Conviene saber que este módulo **no es** la defensa contra un agente en bucle. `GuardrailChain`
   evalúa todos los guardrails sin cortar en el primero que decide, así que `ratelimit` (orden
   100) se sigue evaluando aunque `anomaly-detector` (80) haya escalado, y es él quien corta el
   bucle. Aquí las cuotas solo protegen el canal de aprobación.

   Nota operativa: al resolverse o expirar una solicitud se libera cuota, y un agente en bucle
   ocupará el hueco. No es un agravante —expirar la libera igual— pero explica por qué la lista
   de pendientes de un agente descontrolado se rellena sola.

4. **Espera bloqueante, y por eso hay cuotas.** El handler de core es síncrono
   (`BiFunction<McpSyncServerExchange, CallToolRequest, CallToolResult>`) y el proyecto solo
   decora `SyncToolSpecification`, así que retener una invocación es retener su hilo; no hay forma
   de "pausar" sin ello sin llevar los guardrails al modo asíncrono del SDK, que es otro alcance.

   Cuánto duele depende del modelo de hilos, que este módulo no elige: sobre hilos virtuales el
   coste es despreciable —diez mil esperas concurrentes medidas en unos 30 MB—, sobre hilos de
   plataforma cada espera es una porción real de un pool de un par de cientos. De ahí el default
   conservador de `max-pending` (§6) y la regla de no esperar con un lock tomado (§5.2).

5. **Los argumentos viajan al aprobador, y hay un camino en el que eso duele.** Sin ellos la
   aprobación es una firma en blanco, así que el default es incluirlos. Pero existe una
   combinación concreta, no hipotética, que hay que nombrar: `credential-leak-guard` admite
   `ESCALATE` como acción sobre los argumentos de entrada, de modo que una invocación **escalada
   precisamente por contener un secreto** acabaría mostrando ese secreto en el canal de
   aprobación. Es circular: para decidir sobre el secreto, la persona tendría que verlo, y el
   guardrail que existe para que no se propague sería quien lo propaga.

   Por eso el README recomienda `DENY` en `credential-leak-guard` cuando este módulo está activo,
   y existe `include-arguments` para quien tenga un canal de aprobación menos protegido que la
   propia invocación. Se prefirió eso a recortar los argumentos por heurística: un enmascarado por
   nombre de clave daría una falsa sensación de seguridad y duplicaría, peor, lo que
   `credential-leak-guard` ya hace bien.

6. **La solicitud se retira del canal al terminar la espera, también al expirar.** Si al expirar
   se quedara pendiente, un humano podría aprobar minutos después una invocación que ya fue
   denegada y cuyo hilo ya devolvió el error: una aprobación sin efecto, y una lista de pendientes
   que miente sobre lo que aún se puede decidir.

7. **La decisión pendiente es un `CompletableFuture`, no un latch más una referencia.** El diseño
   original de §5.2 usaba `CountDownLatch` para señalar y `AtomicReference` para llevar la
   decisión, con un `compareAndSet` manual que implementaba "la primera decisión gana". Funcionaba,
   pero obligaba a `awaitDecision` a descartar el `boolean` que devuelve `await`, porque la
   referencia era la fuente de verdad y el latch solo despertaba; SonarQube lo señala como
   `java:S899` y tiene razón: eran dos primitivas para una sola pregunta.

   `CompletableFuture` responde las dos de forma nativa. `complete(...)` devuelve `true` solo para
   el primero que llega, que **es** la regla de la primera decisión en vez de una simulación de
   ella, y `get(timeout)` expresa el plazo sin dejar ningún valor sin usar. Un campo menos, un
   `compareAndSet` menos, y ninguna diferencia observable: las dos cuotas, el retiro al terminar la
   espera y la irrevocabilidad de la primera decisión se comportan igual, con los mismos 96 tests
   sin tocar.

   La regla dura de §5.2 sigue vigente y por el mismo motivo: `get(timeout)` tampoco puede
   ejecutarse con un lock del adaptador retenido.
