# Spec — `guardrails-trifecta-correlator` (módulo 12)

> **Prerequisito.** Requiere la extensión `guardrails-core-session-metadata`
> ([spec](guardrails-core-session-metadata-spec.md)), que se construye antes en esta misma rama.
> Sin ella el módulo no puede saber en qué sesión ocurre una invocación; ver §9, decisión 3.

## 1. Problema y alcance

Simon Willison describe la **"lethal trifecta"**: un agente es explotable por diseño cuando en una
misma sesión concurren acceso a datos privados, procesamiento de contenido no confiable y
capacidad de comunicarse hacia fuera. Cada pata por separado es inofensiva; juntas permiten que
una instrucción escondida en un dato leído haga que el agente exfiltre otro dato. Ningún guardrail
del proyecto ve esa combinación, porque todos juzgan una invocación a la vez.

Este módulo la ve: acumula por sesión qué patas ha tocado el agente y, cuando las tres coinciden,
**escala** — y sigue escalando durante el resto de la sesión, no solo en la invocación que cerró
el triángulo. La resolución humana la aporta `guardrails-approval-gate` (módulo 11), que ya
convierte un `Escalate` en una pausa real.

**No-goals.**

- No detecta inyección de prompts, ni autoriza, ni valida destinos: eso es de los módulos 4, 3 y 9.
  Aquí solo se correlaciona.
- No decide quién aprueba ni cómo: eso es del módulo 11 y del canal que exponga el operador.
- No infiere las capacidades de una tool leyendo su nombre, su descripción o su esquema. Las
  declara el operador (§9, decisión 2).
- No depende de ningún módulo `guardrails-*` salvo `core` (ARCHITECTURE.md §5).

## 2. La premisa del encargo no se sostiene — y por qué

El encargo pedía leer las tres señales «de la traza de decisión que `guardrails-core` ya mantiene».
Verificado en el código, esa traza **no puede responder la pregunta**, por tres motivos
independientes y cada uno suficiente:

**a) Un `Guardrail` no ve la traza.** El SPI es
`GuardrailDecision evaluate(ToolInvocationContext context)`. El `ChainVerdict` con las
evaluaciones se construye *después* de que todos los guardrails hayan opinado
(`GuardrailChain.evaluate`, línea 55) y solo lo reciben `GuardedToolCallHandler` y el
`EscalationResolver`. Ningún guardrail puede leer lo que decidió otro, por diseño y por orden de
ejecución.

**b) Aunque lo viera, un `Allow` no significa lo que la trifecta necesita.** Verificado guardrail a
guardrail:

| Pata | Guardrail | Qué significa su `Allow` | ¿Es la señal? |
|---|---|---|---|
| Datos privados | `authz` | «este agente está autorizado a llamar a esta tool» | **No.** Estar autorizado a `get_weather` no es acceder a datos privados |
| Contenido no confiable | `injection-guard` | «no vi patrones de inyección en los argumentos» | **No.** Es ausencia de evidencia, no ausencia de ingesta |
| Comunicación externa | `egress-control` | `NotAnEgressTool` **o** `DestinationsAllowed` | **No.** Los dos casos devuelven `Allow` y significan lo contrario: «no hay egress» y «hay egress permitido» |

**c) Las patas son propiedades de la herramienta, no de la decisión.** La trifecta habla de
*capacidades* que confluyen: qué puede hacer el agente, no qué le permitieron esta vez. Un `Deny`
de `injection-guard` corta la invocación y la trifecta ni se plantea; un `Allow` no dice si la
tool ingiere contenido externo.

**Consecuencia.** Las capacidades las declara el operador, igual que ya declara qué tools tienen
salida a red en `mcp.guardrails.egress.tools`, y el módulo define su propio puerto de sesión bajo
ARCHITECTURE.md §5.2. Lo que sí necesita de core es **saber en qué sesión está**, que es un dato
distinto de las tres señales y que hoy tampoco llega: de ahí la extensión
`guardrails-core-session-metadata` (§9, decisión 3).

## 3. Modelo de dominio

Paquete `io.github.tikyparkinson.mcpguardrails.trifecta.domain`. JDK puro.

```java
/**
 * Una de las tres patas. El nombre entra en el motivo de la escalación, así que es contrato.
 */
enum Capability { PRIVATE_DATA, UNTRUSTED_CONTENT, EXTERNAL_COMMS }

/**
 * Qué patas toca una herramienta, según el operador.
 * Invariantes: toolName no blank; capabilities inmutable y NO vacía (declarar una tool sin
 * ninguna capacidad es ruido: equivale a no declararla).
 */
record ToolCapabilities(String toolName, Set<Capability> capabilities) {}

/**
 * Sesión sobre la que se correlaciona: la sesión MCP de transporte, o el agente cuando el
 * transporte no aporta ninguna. Quién lo deriva está en §5.1 y en la decisión 3.
 * Invariantes: value no blank.
 * Factorías: SessionId.ofMcpSession(String), SessionId.ofAgent(String)
 */
record SessionId(String value) {}

/**
 * Lo acumulado por una sesión hasta ahora.
 * startedAt:  primera invocación de la sesión, para el tope absoluto de duración.
 * lastSeenAt: última invocación, para la caducidad por inactividad.
 * Invariantes: capabilities inmutable; instantes no null; lastSeenAt >= startedAt.
 * hasTrifecta(): las tres patas presentes.
 */
record SessionCapabilities(Set<Capability> capabilities, Instant startedAt, Instant lastSeenAt) {
  boolean hasTrifecta() { return capabilities.size() == Capability.values().length; }
  Set<Capability> missing() { ... }   // para explicar cuánto falta, en el motivo
}

/**
 * Lo que devuelve el puerto al acumular: cómo queda la sesión y si YA estaba completa antes.
 * La segunda parte no se puede deducir de la primera —una sesión con las tres patas después de
 * una invocación que aportó EXTERNAL_COMMS es idéntica tanto si esa invocación trajo la pata que
 * faltaba como si repitió una que ya estaba—, y solo lo sabe quien tenía el estado previo, dentro
 * del mismo paso atómico. Ver decisión 8.
 * Invariante: si completeBefore, la sesión resultante tiene la trifecta (nunca se restan patas).
 */
record SessionAccumulation(SessionCapabilities session, boolean completeBefore) {
  boolean closedNow() { return session.hasTrifecta() && !completeBefore; }
}

/**
 * Configuración del correlador, validada una vez.
 * Invariantes: sessionIdleTimeout y sessionMaxDuration positivos; sessionMaxDuration >=
 * sessionIdleTimeout (un tope absoluto menor que el de inactividad haría el segundo inalcanzable);
 * tools inmutable, sin nombres duplicados.
 * capabilitiesOf(toolName): las declaradas, o conjunto vacío si la tool no se declaró.
 */
record TrifectaPolicy(
    List<ToolCapabilities> tools, Duration sessionIdleTimeout, Duration sessionMaxDuration) {}

/** Resultado del análisis. La ausencia se modela con un tipo, no con null. */
sealed interface TrifectaVerdict permits TrifectaIncomplete, TrifectaComplete {}

/** Faltan patas. Lleva las presentes para poder explicarlo si alguien lo pide. */
record TrifectaIncomplete(Set<Capability> present) implements TrifectaVerdict {}

/**
 * Las tres coinciden en esta sesión.
 * closedNow: true si esta invocación es la que cerró el triángulo, false si ya estaba cerrado.
 * Distinguirlo no cambia la decisión —ambas escalan— pero sí el motivo que lee el humano.
 * Invariante: capabilities contiene las tres.
 */
record TrifectaComplete(Set<Capability> capabilities, boolean closedNow) implements TrifectaVerdict {}
```

## 4. Puertos

### 4.1 `AssessTrifectaUseCase` — entrada (lado del guardrail)

Capa `application.port.in`. Lo invoca `TrifectaGuardrail`.

```java
public interface AssessTrifectaUseCase {
  /**
   * Suma a la sesión lo que aporta esta invocación y dice si las tres patas coinciden.
   * Nunca devuelve null.
   */
  TrifectaVerdict assess(SessionId sessionId, String toolName, Instant occurredAt);
}
```

Recibe la `SessionId` ya derivada, no el `agentId` en bruto: quién es una sesión lo decide el
adaptador de entrada, que es el único que ve el `ToolInvocationContext` completo.

### 4.2 `ResetSessionUseCase` — entrada (lado humano)

Capa `application.port.in`. Lo invoca el canal que el operador exponga, el mismo por el que
resuelve aprobaciones.

```java
public interface ResetSessionUseCase {

  /** Sesiones con la trifecta cerrada ahora mismo, para que un humano sepa qué hay que revisar. */
  List<SessionId> lockedSessions();

  /**
   * Olvida lo acumulado por una sesión. Devuelve false si no había nada que olvidar.
   *
   * <p>Sin esto, una sesión con la trifecta cerrada escala cada invocación hasta que caduca, y con
   * las cuotas de approval-gate el agente queda atascado. Reabrir la sesión es una decisión
   * deliberada de una persona, no algo que ocurra solo.
   */
  boolean reset(SessionId sessionId);
}
```

### 4.3 `SessionCapabilityPort` — salida (lo plugable)

Capa `application.port.out`. Lo implementa `InMemorySessionCapabilityAdapter` por defecto.

```java
public interface SessionCapabilityPort {

  /**
   * Añade las capacidades a la sesión y devuelve cómo queda —esta invocación incluida— junto con
   * si las tres patas ya coincidían antes. Debe ser seguro bajo concurrencia y atómico por sesión:
   * dos invocaciones simultáneas no pueden perder ninguna aportación, y solo una de ellas puede
   * declarar que cerró el triángulo.
   */
  SessionAccumulation accumulate(SessionId sessionId, Set<Capability> capabilities, Instant occurredAt);

  /** Sesiones con las tres patas presentes, más antiguas primero. Nunca null. */
  List<SessionId> withTrifecta();

  /** Olvida la sesión. false si no existía. */
  boolean forget(SessionId sessionId);
}
```

No hay lectura pura de una sesión concreta: el guardrail siempre aporta al leer, y una consulta
sin escritura solo serviría para observabilidad que nadie ha pedido. `withTrifecta` sí existe
porque el humano necesita saber qué revisar antes de poder resetear nada.

## 5. Adaptadores esperados

### 5.1 Adapter-in: `TrifectaGuardrail implements Guardrail`

Paquete `trifecta.adapter.in.chain`.

- `name()` = `"trifecta-correlator"`; `order()` = `90` — después de `anomaly-detector` (80) y antes
  de `ratelimit` (100). La cadena queda: audit (−100), tool-integrity (−50), authz (0),
  injection-guard (50), credential-leak (60), egress-control (70), anomaly-detector (80),
  **trifecta-correlator (90)**, ratelimit (100).
- `evaluate(context)`:
  1. `sessionId = sessionIdResolver.resolve(context)`.
  2. `verdict = useCase.assess(sessionId, toolName, occurredAt)` — `occurredAt` sale del contexto,
     no de un reloj propio.
  3. `TrifectaIncomplete` ⇒ `Allow`.
  4. `TrifectaComplete` ⇒ **`Escalate`**, nunca `Deny`, con motivo
     `"lethal trifecta active in this session (private data, untrusted content, external comms)"`,
     más `"; closed by this invocation"` cuando `closedNow`.

### 5.2 `SessionIdResolver` — cómo se deriva una sesión

Paquete `trifecta.adapter.in.chain`. Interfaz funcional, sustituible por el operador:

```java
@FunctionalInterface
public interface SessionIdResolver {
  SessionId resolve(ToolInvocationContext context);

  /**
   * Por defecto: la sesión MCP que la extensión de core deja en
   * {@code metadata["mcp.sessionId"]}; si el transporte no aporta ninguna, el agente.
   */
  static SessionIdResolver mcpSessionOrAgent() { ... }
}
```

Es el mismo patrón que `AgentIdResolver` en core, y por el mismo motivo: qué constituye una sesión
depende del despliegue, y el módulo no puede saberlo por todos.

**El fallback al agente es la degradación honesta, no el caso normal.** Con `stdio` el proceso
entero es una sesión y coincide; con un transporte HTTP sin sesión, correlacionar por agente mezcla
usuarios (§9, decisión 3). Cuando el resolver por defecto tiene que caer al agente, se registra un
aviso al arrancar la primera vez, no en silencio.

### 5.3 Adapter-out: `InMemorySessionCapabilityAdapter`

Paquete `trifecta.adapter.out.session`.

- `ConcurrentHashMap<SessionId, SessionCapabilities>`, con `compute` para que la suma y la lectura
  sean atómicas por sesión.
- Antes de acumular, la sesión se descarta y empieza de cero si se cumple **cualquiera** de las
  dos condiciones:
  - `lastSeenAt` más antiguo que `sessionIdleTimeout` — el agente dejó de trabajar;
  - `startedAt` más antiguo que `sessionMaxDuration` — el agente lleva demasiado tiempo sin parar.

  Las dos hacen falta, y esto se verificó ejecutándolo: con solo la de inactividad, un agente que
  invoca cada diez segundos refresca `lastSeenAt` en cada llamada y **la sesión no caduca nunca**
  —8640 invocaciones en 24 horas, cero caducidades—, de modo que una trifecta cerrada por la
  mañana seguiría escalando al día siguiente.
- La caducidad se evalúa con el instante que trae la invocación, no con un reloj propio: mantiene
  el adaptador testeable y coherente con el resto de la cadena.
- Las capacidades **nunca se restan**. Es lo que hace que la trifecta, una vez cerrada, siga
  cerrada el resto de la sesión sin necesidad de un flag aparte: cualquier invocación posterior
  vuelve a ver las tres. Incluida una a una tool inocua, que es justo lo que pidió el encargo.
- `forget` borra la entrada; `withTrifecta` lista las sesiones completas ordenadas por `startedAt`.
- Limitación que va al README: la sesión vive en este proceso. Al reiniciar se pierde lo
  acumulado y la trifecta habría que rearmarla; detrás de un balanceador, cada réplica ve solo su
  parte, así que un agente repartido entre réplicas puede cerrar el triángulo sin que ninguna lo
  vea entero. Un adaptador compartido lo resuelve.

### 5.4 Canal humano — no se incluye, se documenta

El módulo no trae transporte, igual que `approval-gate`. El README muestra cómo exponer
`ResetSessionUseCase` junto al controlador de aprobaciones, con el mismo aviso: quien pueda
reabrir una sesión puede levantar la única señal que detecta la condición más grave del framework,
así que ese endpoint se protege igual que el de aprobación.

## 6. Configuración Spring Boot

Prefijo `mcp.guardrails.trifecta`. Record `GuardrailsTrifectaProperties` con `@ConstructorBinding`
y `@DefaultValue` en cada parámetro.

| Propiedad | Tipo | Default | Significado |
|---|---|---|---|
| `enabled` | `boolean` | `true` | Registra el guardrail. |
| `session-idle-timeout` | `Duration` | `PT30M` | Inactividad tras la cual la sesión se olvida. |
| `session-max-duration` | `Duration` | `PT2H` | Duración máxima de una sesión desde su primera invocación, caduque o no por inactividad. Debe ser ≥ `session-idle-timeout`. |
| `tools` | `List<ToolConfig>` | **vacía** | Tools declaradas y qué patas toca cada una. |

`ToolConfig`: `{ name: String, capabilities: Set<Capability> }`.

```yaml
mcp:
  guardrails:
    trifecta:
      session-idle-timeout: PT30M
      session-max-duration: PT2H
      tools:
        - name: read_customer_record
          capabilities: [PRIVATE_DATA]
        - name: fetch_url
          capabilities: [UNTRUSTED_CONTENT, EXTERNAL_COMMS]
        - name: send_email
          capabilities: [EXTERNAL_COMMS]
```

**Por qué dos caducidades.** La de inactividad cierra la sesión del agente que dejó de trabajar.
La absoluta cierra la del que no para: verificado ejecutándolo, un agente que invoca cada diez
segundos refresca `lastSeenAt` en cada llamada y **nunca** alcanza los 30 minutos de inactividad
—8640 invocaciones en 24 horas sin una sola caducidad—. Sin el tope absoluto, la primera trifecta
del día escalaría todo lo que viniera después, indefinidamente.

**La lista vacía por defecto no deniega nada, y aquí eso es correcto** — al contrario que en
`egress-control`, donde la allowlist vacía deniega todo. La diferencia es qué significa el vacío:
en egress es «no autorizo ningún destino», una regla; aquí es «no sé qué hace ninguna tool», que
es ignorancia. Escalar por ignorancia bloquearía el servidor entero desde el arranque. Pero
tampoco puede quedarse callado: con `tools` vacía **el módulo avisa al arrancar** de que está
inactivo, por el mismo criterio de ARCHITECTURE.md §5.2 que exige a un puente incompleto decirlo
en vez de degradarse en silencio. El README lo repite en la primera línea de la configuración.

## 7. Dependencias Maven propuestas

| Dependencia | Scope | Por qué |
|---|---|---|
| `io.github.tikyparkinson:guardrails-core` | compile | El SPI `Guardrail`, `ToolInvocationContext` y los tipos de decisión. |
| `org.springframework.boot:spring-boot` (BOM 4.1.0) | provided | Única anotación usada: `@ConfigurationProperties` (+ `@ConstructorBinding`/`@DefaultValue`) en `infrastructure`. |
| `org.junit.jupiter:junit-jupiter` (BOM 6.1.2) | test | Tests unitarios. |
| `org.mockito:mockito-core` (5.23.0) | test | Doble de `SessionCapabilityPort` en los tests del servicio. |

Ninguna dependencia nueva para el proyecto. Ningún módulo `guardrails-*` distinto de core. Sin
store real ⇒ sin Testcontainers.

## 8. Diagrama del hexágono

```
   guardrails-core                       canal humano (del operador, el de approval-gate)
   GuardrailChain                                        │
         │ Guardrail SPI                                 │ ResetSessionUseCase
         │ context.metadata["mcp.sessionId"]             │  lockedSessions() / reset(id)
         ▼   (lo pone guardrails-core-session-metadata)  ▼
  ┌────────────────────┐                      ┌────────────────────────┐
  │  TrifectaGuardrail │  adapter-in          │   (adaptador propio)   │
  │  order 90          │                      └───────────┬────────────┘
  │  SessionIdResolver │                                  │
  └─────────┬──────────┘                                  │
            │ AssessTrifectaUseCase                       │
            ▼                                             ▼
       ┌──────────────────────────────────────────────────────────┐
       │                  AssessTrifectaService                   │
       │  capabilitiesOf(tool) -> accumulate(session) -> verdict  │
       └─────────────────────────┬────────────────────────────────┘
                                 │  SessionCapabilityPort (out)
                                 ▼
                 ┌─────────────────────────────────────┐
                 │  InMemorySessionCapabilityAdapter   │  (sustituible)
                 │  mapa por sesión                    │
                 │  caduca por inactividad Y por edad  │
                 └─────────────────────────────────────┘

   domain: Capability · ToolCapabilities · SessionId · SessionCapabilities
           TrifectaPolicy · TrifectaVerdict (TrifectaIncomplete | TrifectaComplete)
```

## 9. Decisiones de diseño

1. **Las patas se declaran, no se deducen de la traza.** Es la consecuencia de §2, y conviene
   resumir por qué el atajo no existía: `authz` dice si el agente *puede*, no si los datos son
   privados; `injection-guard` que *no vio* inyección, no que no hubiera ingesta; y `egress-control`
   devuelve `Allow` tanto cuando la tool no sale a la red como cuando sale a un destino permitido —
   los dos casos opuestos para la trifecta, indistinguibles desde fuera. Correlacionar veredictos
   habría producido un detector que dispara solo o nunca, y ninguna de las dos cosas se nota hasta
   que hay un incidente.

2. **El operador declara las capacidades, y el módulo no intenta adivinarlas.** Se descartó
   inferirlas del nombre o la descripción de la tool: la descripción la escribe quien publica el
   servidor MCP, que es precisamente el vector de `tool-integrity` (módulo 7). Un detector cuya
   entrada la controla el atacante no es un detector. El operador ya declara capacidades en
   `mcp.guardrails.egress.tools`; esto es el mismo gesto, ampliado.

3. **Una sesión es la sesión MCP del transporte, no el agente.** El primer borrador de este spec
   decía «sesión = agentId». Verificado contra la demo real, eso estaba mal: el `agentId` por
   defecto es `clientInfo.name()`, el nombre del producto cliente. Tres conexiones distintas
   dieron `clientInfo.name=copilot` las tres, con sesiones de transporte distintas:

   ```
   sessionId=6a78fc9c-…  clientInfo.name=copilot
   sessionId=92d83d23-…  clientInfo.name=copilot
   sessionId=57bc8401-…  clientInfo.name=copilot
   ```

   Correlacionar sobre el agente habría mezclado a todos los usuarios del mismo cliente: uno lee
   un expediente, otro abre una URL, un tercero manda un correo, y el correlador cierra el
   triángulo con tres personas que no se conocen. En un módulo cuya única salida es molestar a un
   humano, ese ruido lo mata: si casi todas las escalaciones son falsas, dejan de mirarse.

   `McpSyncServerExchange.sessionId()` sí identifica la conexión —comprobado que devuelve el mismo
   valor que la cabecera `Mcp-Session-Id`—, y por eso este módulo requiere la extensión
   `guardrails-core-session-metadata`. El fallback al agente existe para transportes sin sesión,
   avisa al arrancar y no pretende ser equivalente.

4. **Acumular sin restar es lo que da el «resto de la sesión», y por eso la sesión debe caducar de
   dos formas.** No hace falta un flag de sesión bloqueada: si las capacidades no se retiran,
   cualquier invocación posterior vuelve a ver las tres y vuelve a escalar, incluso si es a una
   tool inocua. Un flag aparte sería un segundo estado que mantener sincronizado con el primero, y
   desincronizarlos significaría dejar pasar invocaciones en una sesión ya comprometida.

   El precio es que lo acumulado solo desaparece cuando la sesión caduca, así que la caducidad
   tiene que funcionar de verdad. La de inactividad por sí sola no basta: `accumulate` refresca
   `lastSeenAt` en cada invocación, de modo que un agente activo nunca la alcanza. Medido: 8640
   invocaciones repartidas en 24 horas, ni una caducidad. De ahí `session-max-duration`, que se
   mide desde la primera invocación y no se refresca nunca.

5. **Solo `Escalate`, nunca `Deny`.** Lo pidió el encargo y encaja con el resto: la trifecta es una
   condición estructural del montaje, no la prueba de un ataque en curso. Muchas sesiones legítimas
   la cumplen —un asistente que lee un ticket, consulta la base de clientes y responde por correo—
   y denegarlas rompería el producto. Escalar pone a una persona delante. `approval-gate` (módulo
   11) ya convierte ese `Escalate` en una pausa real.

6. **Existe `reset`, y es deliberadamente manual.** Con la trifecta cerrada, cada invocación de la
   sesión escala, y las cuotas de `approval-gate` (5 por agente por defecto) atascan al agente
   enseguida. Esa fricción es el comportamiento correcto para la condición más grave del framework,
   pero necesita una salida que no sea esperar a que caduque. Se descartó reabrir la sesión
   automáticamente tras una aprobación: aprobar *una invocación* no es declarar que la sesión
   entera dejó de ser peligrosa, y confundirlo convertiría una aprobación rutinaria en un permiso
   general. `lockedSessions()` acompaña a `reset` porque nadie puede decidir sobre una lista que no
   ve.

7. **La lista de tools vacía no detecta nada, y el módulo lo dice al arrancar.** Es lo contrario
   del criterio de `egress-control`, donde el vacío deniega. La diferencia está en qué representa:
   allí una decisión del operador, aquí la falta de ella. Fallar cerrado ante la ignorancia dejaría
   el servidor inutilizable en el primer arranque, así que el módulo queda inactivo — pero
   inactivo y callado es la combinación que ARCHITECTURE.md §5.2 prohíbe expresamente, porque un
   operador que cree tener una protección y no la tiene está peor que uno que sabe que no la tiene.
   De ahí el aviso al arrancar, además del README.

8. **`closedNow` lo informa el puerto, porque no se puede deducir.** El primer borrador dejaba que
   el caso de uso lo calculase comparando lo que aportó la invocación con el estado resultante.
   No funciona, y se comprobó ejecutándolo: una sesión con las tres patas tras una invocación que
   aportó `EXTERNAL_COMMS` es **idéntica** tanto si esa invocación trajo la pata que faltaba como
   si repitió una que ya estaba. Misma entrada, dos respuestas correctas distintas.

   Quien lo sabe es el adaptador, que tiene el estado previo, y solo dentro del mismo paso atómico
   —si lo consultara antes en una llamada aparte, dos invocaciones concurrentes podrían declarar
   ambas que cerraron el triángulo—. De ahí que `accumulate` devuelva `SessionAccumulation` en vez
   de `SessionCapabilities`. El alcance del módulo no cambia: es la misma información, pedida a
   quien puede darla.
