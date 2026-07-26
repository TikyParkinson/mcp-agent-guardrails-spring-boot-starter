# Spec — guardrails-egress-control

> Módulo 9 según ARCHITECTURE.md §6. Autor: spec-architect.
> Paquete raíz: `io.github.tikyparkinson.mcpguardrails.egress`.
> Prerequisito cumplido: `guardrails-credential-leak-guard-DONE.md` aprobado.

## 1. Problema y alcance

La tercera pata de la "lethal trifecta" es la capacidad de comunicarse hacia fuera: da igual que
el agente haya leído un secreto si no tiene por dónde sacarlo. `guardrails-egress-control`
intercepta las tools con capacidad de egreso —HTTP saliente, email, mensajería— **antes** de que
se ejecuten, extrae el destino de sus argumentos y lo contrasta contra una allowlist explícita.
Un destino que no esté en la lista, o que no se pueda determinar, produce `Deny`. La allowlist
está **vacía por defecto**: sin configuración, ningún egreso pasa.

**No-goals:**

- No adivina qué tools hacen egreso: el operador las declara. Una tool no declarada queda fuera
  del alcance de este guardrail y se permite — no es que sea segura, es que este módulo no opina
  sobre ella (ver Decisión de diseño 1). Quien tema olvidarse de declarar una, debe componer con
  `guardrails-authz` y `default-effect: DENY`, que ya obliga a enumerar las tools permitidas.
- No intercepta el tráfico de red real: actúa sobre la **declaración** de destino que la tool
  recibe en sus argumentos. Una tool que ignore su argumento y llame a otro sitio no es
  detectable desde aquí (ver Decisión de diseño 6).
- **No admite hosts internacionalizados (IDN).** `java.net.URI.getHost()` devuelve `null` para
  cualquier host con caracteres no ASCII, así que un destino como `https://josé.example.com` se
  considera indeterminable y por tanto se **deniega**, incluso si se lista en la allowlist. El
  efecto secundario es deseable —un homógrafo como `аpple.com` (con `а` cirílica) también se
  deniega— pero la limitación es real y hay que conocerla antes de desplegar.
- No controla puertos ni rutas: la allowlist es de **hosts**. `api.github.com` permite
  `https://api.github.com:8443/cualquier/cosa`. Si hace falta granularidad por puerto, este no
  es el módulo.
- No resuelve DNS ni valida que el destino exista, y no evalúa rangos CIDR: la allowlist es de
  hosts y dominios, comparados por etiquetas. Una IP escrita en otra base
  (`http://2130706433/` por `127.0.0.1`) no coincide con su forma canónica y, por tanto, se
  deniega.
- No inspecciona el resultado de la tool: no implementa `ResultGuardrail`. Lo que sale por la
  respuesta es competencia de `credential-leak-guard`.
- No registra nada en el bus de auditoría (ARCHITECTURE.md §5 prohíbe importar otro módulo
  `guardrails-*`). La traza queda en el `ChainVerdict` de core.
- No persiste nada. Sin store real ⇒ **sin Testcontainers** (ARCHITECTURE.md §8).
- Sin autoconfiguración aquí: el cableado va en `spring-boot-starter`, en su propia rama.

## 2. Modelo de dominio

Paquete `io.github.tikyparkinson.mcpguardrails.egress.domain`. JDK puro (`java.net.URI`),
autocontenido.

```java
/**
 * Host de destino ya normalizado: minúsculas, sin el punto final del FQDN y sin los corchetes
 * de una dirección IPv6 (URI.getHost() devuelve "[::1]", pero la allowlist se escribe "::1").
 * Invariantes: value no blank.
 * Factory: Destination.of(String host) — normaliza; blank ⇒ IllegalArgumentException.
 */
record Destination(String value) {}

/**
 * Resultado de intentar extraer un destino de un valor de argumento.
 * Modela explícitamente la ausencia, en vez de devolver null.
 */
sealed interface DestinationExtraction permits Extracted, NotDeterminable {}
record Extracted(Destination destination) implements DestinationExtraction {}
record NotDeterminable(String rawValueKind) implements DestinationExtraction {}

/**
 * Entrada de la allowlist. Dos formas:
 *   exacta   — "api.github.com" casa solo con ese host;
 *   comodín  — "*.internal.corp" casa con cualquier subdominio, pero NO con el apex
 *              ("internal.corp" hay que listarlo aparte). Convención de certificados/cookies.
 * Invariantes: pattern no blank; el comodín solo puede aparecer como etiqueta inicial "*.".
 * Factory: AllowedDestination.of(String pattern) — normaliza a minúsculas.
 * Método: matches(Destination) — comparación POR ETIQUETAS de dominio, nunca por substring:
 *         "*.example.com" no casa con "example.com.evil.com" ni "notexample.com".
 */
record AllowedDestination(String pattern, boolean wildcard) {
  boolean matches(Destination destination) { ... }
}

/**
 * Tool declarada como capaz de egreso y dónde mirar su destino.
 * Invariantes: toolName no blank; destinationArguments no vacía (una tool de egreso sin
 * argumento de destino no se puede verificar y sería un fail-open silencioso).
 * destinationArguments son rutas dentro de los argumentos: "url", "request.endpoint",
 * "recipients" (si resuelve a lista, cada elemento es un destino).
 */
record EgressTool(String toolName, List<String> destinationArguments) {}

/**
 * Política vigente: qué tools hacen egreso y a dónde pueden ir.
 * Invariantes: ambas listas inmutables, nunca null (pueden ser vacías).
 * Métodos: Optional<EgressTool> egressToolNamed(String toolName)  — nunca null;
 *          boolean allows(Destination)                            — false si la lista es vacía.
 */
record EgressPolicy(List<EgressTool> tools, List<AllowedDestination> allowedDestinations) {}

/**
 * Extractor puro de destinos. Clase final DestinationExtractor con:
 *   static DestinationExtraction extract(String rawValue)
 * Reglas, en orden:
 *   1. Si parsea como URI absoluta con host ⇒ ese host. Se usa java.net.URI, de modo que
 *      "https://evil.com@good.com/x" extrae "good.com" y no "evil.com" (el userinfo no engaña).
 *      Un host IDN hace que getHost() devuelva null y por tanto cae en la regla 4 (ver No-goals).
 *   2. Si contiene '@' y lo posterior a la ÚLTIMA '@' es un host válido ⇒ ese dominio (email).
 *   3. Si el valor entero es un host válido (etiquetas alfanuméricas separadas por puntos,
 *      o una IP literal) ⇒ ese host.
 *   4. En cualquier otro caso ⇒ NotDeterminable, que el caso de uso traduce a Deny.
 *
 * Clase final ArgumentPathResolver con:
 *   static List<String> resolve(Map<String,Object> arguments, String path)
 * Resuelve rutas con punto ("request.url"); si el valor final es String devuelve una lista de
 * un elemento; si es List devuelve sus elementos String; si no existe o es de otro tipo,
 * devuelve lista vacía (que el caso de uso trata como destino indeterminable).
 *
 * Nota: este resolutor NO reutiliza el ValueFlattener de credential-leak-guard —
 * ARCHITECTURE.md §5 prohíbe la dependencia entre guardrails— y además resuelve una ruta
 * concreta en vez de aplanar todo el mapa.
 */

/**
 * Veredicto del dominio sobre una invocación concreta.
 * Modela las tres situaciones distinguibles; el adaptador las traduce a GuardrailDecision.
 */
sealed interface EgressCheckResult permits NotAnEgressTool, DestinationsAllowed, EgressViolation {}
record NotAnEgressTool() implements EgressCheckResult {}
record DestinationsAllowed(List<Destination> destinations) implements EgressCheckResult {}

/**
 * Motivo del rechazo, sin ambigüedad para el operador.
 * violations: destinos concretos fuera de la allowlist (puede ser vacía si el problema es que
 *             no se pudo determinar ninguno).
 * undeterminedArguments: rutas cuyo valor no permitió extraer un destino.
 */
record EgressViolation(List<Destination> violations, List<String> undeterminedArguments)
    implements EgressCheckResult {}
```

## 3. Puertos (contratos de application)

### 3.1 `CheckEgressDestinationUseCase` — puerto de entrada

- Capa: `application.port.in`. Lo invoca `EgressGuardrail` (adapter-in).
- Lo implementa: `CheckEgressDestinationService`.

```java
public interface CheckEgressDestinationUseCase {
  /** Verifica los destinos declarados en los argumentos de la tool. Nunca null. */
  EgressCheckResult check(String toolName, Map<String, Object> arguments);
}
```

### 3.2 `EgressPolicyPort` — puerto de salida (lo plugable)

- Capa: `application.port.out`.
- Lo implementa: `InMemoryEgressPolicyAdapter` (por defecto, construido desde properties).
  Sustituible por un feed dinámico de política (CMDB, service catalog, API de red).

```java
public interface EgressPolicyPort {
  /** Política vigente. Nunca null; puede tener ambas listas vacías (todo egreso denegado). */
  EgressPolicy currentPolicy();
}
```

## 4. Caso de uso — `CheckEgressDestinationService`

Constructor: `(EgressPolicyPort policyPort)`.

1. Valida `toolName` y `arguments` no null.
2. `policy = policyPort.currentPolicy()`.
3. `policy.egressToolNamed(toolName)`:
   - vacío ⇒ `NotAnEgressTool` (fuera de alcance; el adaptador devolverá `Allow`).
4. Para cada ruta de `tool.destinationArguments()`:
   1. `values = ArgumentPathResolver.resolve(arguments, path)`.
   2. Si `values` está vacía ⇒ la ruta va a `undeterminedArguments`.
   3. Para cada valor: `DestinationExtractor.extract(value)`:
      - `Extracted(d)` ⇒ se acumula en `destinations`;
      - `NotDeterminable` ⇒ la ruta va a `undeterminedArguments`.
5. Si `undeterminedArguments` no está vacía ⇒ `EgressViolation(List.of(), undeterminedArguments)`
   — **fail closed**: una tool de egreso cuyo destino no se puede leer no se ejecuta. No hace
   falta comprobar además que `destinations` esté vacía: cada ruta declarada aporta o un destino
   o una entrada en `undeterminedArguments`, y una tool siempre declara al menos una ruta, así
   que "sin destinos y sin rutas ilegibles" es un estado inalcanzable.
6. `violations = destinations.stream().filter(d -> !policy.allows(d))`.
7. `violations` vacía ⇒ `DestinationsAllowed(destinations)`; si no ⇒
   `EgressViolation(violations, List.of())`.

La traducción a `GuardrailDecision` es del adaptador.

## 5. Adaptadores esperados

### 5.1 Adapter-in: `EgressGuardrail implements Guardrail`

Paquete `egress.adapter.in.chain`.

- `name()` = `"egress-control"`; `order()` = `70` — después de `credential-leak` (60) y antes de
  `ratelimit` (100). La cadena queda: audit (−100), tool-integrity (−50), authz (0),
  injection-guard (50), credential-leak (60), **egress-control (70)**, ratelimit (100).
- `evaluate(context)`:
  1. `result = useCase.check(context.toolName().value(), context.arguments())`.
  2. `NotAnEgressTool` ⇒ `Allow`.
  3. `DestinationsAllowed` ⇒ `Allow`.
  4. `EgressViolation` ⇒ acción configurada `onViolation` (default `DENY`), con motivo:
     - destinos fuera de lista: `"egress to a destination outside the allowlist (api.evil.com)"`;
     - destino ilegible: `"egress destination could not be determined from argument 'url'"`;
     - ambos: se enumeran los dos, separados por `"; "`.
     El motivo cita **hosts y rutas de argumento**, nunca el valor completo del argumento (podría
     llevar tokens en la query string). La enumeración se **trunca a los 5 primeros** elementos
     de cada lista, añadiendo `" and N more"`: una tool con doscientos destinatarios no puede
     generar un motivo de miles de caracteres que acabe en el contexto del modelo.

### 5.2 Adapter-out: `InMemoryEgressPolicyAdapter`

Paquete `egress.adapter.out.policy`. Recibe un `EgressPolicy` por constructor y lo devuelve. El
starter lo construirá desde las properties y lo registrará `@ConditionalOnMissingBean`.

### 5.3 Nota para el cableado del starter (fuera de esta rama)

Un typo en `tools[].name` es un **fail-open silencioso**: la tool real nunca se reconoce como de
egreso, pasa sin verificar, y el operador cree estar protegido. Cuando `spring-boot-starter`
integre este módulo debe contrastar los nombres declarados contra el catálogo de tools del
servidor y avisar (o fallar el arranque) si alguno no existe. Aquí no se puede hacer: el módulo
no conoce el catálogo, y depender de `guardrails-tool-integrity` para leerlo violaría
ARCHITECTURE.md §5.

## 6. Configuración Spring Boot

Prefijo `mcp.guardrails.egress`. Record `GuardrailsEgressProperties` con `@ConstructorBinding` y
`@DefaultValue` en cada parámetro.

| Propiedad | Tipo | Default | Significado |
|---|---|---|---|
| `enabled` | `boolean` | `true` | Registra el guardrail. |
| `on-violation` | `ViolationAction` (`DENY`/`ESCALATE`) | `DENY` | Qué hacer ante un destino no permitido o ilegible. No existe la opción `ALLOW`: eliminaría la garantía de fail-closed. |
| `allowed-destinations` | `List<String>` | **vacía** | Hosts o patrones `*.dominio` permitidos. Vacía ⇒ ningún egreso pasa. |
| `tools` | `List<ToolConfig>` | vacía | Tools con capacidad de egreso: `{ name, destination-arguments }`. |

```yaml
mcp:
  guardrails:
    egress:
      allowed-destinations: ["api.github.com", "*.internal.corp"]
      tools:
        - name: http_get
          destination-arguments: [url]
        - name: send_email
          destination-arguments: [to, cc]
```

`ViolationAction` vive en `adapter.in.chain`, junto al guardrail que la consume.

## 7. Dependencias Maven propuestas

| Dependencia | Scope | Por qué |
|---|---|---|
| `io.github.tikyparkinson:guardrails-core` | compile | El SPI `Guardrail`, `ToolInvocationContext` y los tipos de decisión que emite el adapter-in. |
| `org.springframework.boot:spring-boot` (BOM 4.1.0) | provided | Única anotación usada: `@ConfigurationProperties` (+ `@ConstructorBinding`/`@DefaultValue`) en `infrastructure`. |
| `org.junit.jupiter:junit-jupiter` (BOM 6.1.2) | test | Tests unitarios. |
| `org.mockito:mockito-core` | test | Doble de `EgressPolicyPort` en los tests del servicio. |

Ninguna dependencia nueva respecto a lo que el proyecto ya usa; las versiones salen de los BOM
del parent. Ningún módulo `guardrails-*` distinto de core (ARCHITECTURE.md §5). El parseo de
URIs usa `java.net.URI` del JDK, no una librería externa.

## 8. Diagrama del hexágono

```
                    MCP tool call
                          │
                          ▼
        ┌─────────────────────────────────────┐
        │ adapter/in/chain                     │
        │ EgressGuardrail (Guardrail, order 70)│
        │ Allow | Deny | Escalate              │
        └───────────────────┬──────────────────┘
                            │
                            ▼
        ┌─────────────────────────────────────────────────────┐
        │ application                                          │
        │  port.in  CheckEgressDestinationUseCase              │
        │  usecase  CheckEgressDestinationService              │
        │  port.out EgressPolicyPort  ◄───────────────────────┼── implementado por
        └───────────────────┬─────────────────────────────────┘   InMemoryEgressPolicyAdapter
                            │                                      (adapter/out/policy)
                            ▼
        ┌─────────────────────────────────────────────────────┐
        │ domain (JDK puro)                                    │
        │  Destination, AllowedDestination, EgressTool         │
        │  EgressPolicy                                        │
        │  DestinationExtraction: Extracted | NotDeterminable  │
        │  EgressCheckResult: NotAnEgressTool |                │
        │                     DestinationsAllowed |            │
        │                     EgressViolation                  │
        │  DestinationExtractor, ArgumentPathResolver          │
        └─────────────────────────────────────────────────────┘
```

## 9. Decisiones de diseño

1. **El operador declara qué tools hacen egreso; una tool no declarada se permite.** La
   alternativa —tratar toda tool como sospechosa de egreso— exigiría un destino a `add(a, b)` y
   denegaría el servidor entero. La frontera del módulo es explícita: *dentro* de las tools
   declaradas el comportamiento es estrictamente fail-closed; *fuera* de ellas, este guardrail no
   opina. Declarar mal una tool de egreso es un fallo de configuración del operador, y por eso
   `EgressTool` exige al menos un argumento de destino: no se puede registrar una tool de egreso
   "sin destino" y creer que está protegida.

   **No se añade una property `unlisted-tool-policy: DENY`** para cerrar ese hueco: sería duplicar
   `guardrails-authz` con `default-effect: DENY`, que ya obliga a enumerar las tools permitidas.
   Dos guardrails decidiendo lo mismo es peor que uno, y la composición correcta está documentada
   en No-goals y en el README. Tampoco se escanean heurísticamente los argumentos de las tools no
   declaradas buscando algo que parezca una URL: `save_note(text="mira https://x.com")` quedaría
   denegada, un falso positivo impredecible a cambio de una protección que `authz` da de forma
   determinista.

2. **La allowlist está vacía por defecto y no existe `on-violation: ALLOW`.** Es la única forma
   de que "fail closed" sea una propiedad del módulo y no una recomendación. Se permite
   `ESCALATE` como alternativa a `DENY` porque no debilita la garantía —la llamada tampoco se
   ejecuta— y encaja con el `approval-gate` del módulo 11.

3. **El matching es por etiquetas de dominio, nunca por substring.** `*.example.com` casa con
   `a.example.com` y `a.b.example.com`, pero **no** con `example.com` (apex, hay que listarlo
   aparte, como en certificados y cookies), ni con `example.com.evil.com`, ni con
   `notexample.com`. Un `endsWith` ingenuo aceptaría los tres últimos, que es exactamente el
   bypass que un atacante intentaría.

4. **La extracción usa `java.net.URI`, no expresiones regulares.** `https://evil.com@good.com/x`
   tiene host `good.com`: el userinfo antes de la `@` es la trampa clásica contra un parser
   casero. Delegar en el parser del JDK evita reimplementar la RFC 3986 con errores.

   Del mismo parser vienen dos comportamientos que hay que asumir conscientemente, verificados
   ejecutándolo: `getHost()` devuelve la dirección IPv6 **entre corchetes** (`[::1]`), por lo que
   `Destination.of` los elimina para que la allowlist se escriba de forma natural; y devuelve
   `null` ante cualquier host no ASCII, lo que deniega tanto el homógrafo `аpple.com` como el
   IDN legítimo `josé.example.com` (declarado en No-goals). No se usa `IDN.toASCII` para
   rescatar el segundo caso: no aporta seguridad —el homógrafo ya se deniega por fail-closed— y
   una normalización mal hecha sí podría quitarla.

5. **Un destino ilegible es una violación, no un caso "sin datos".** Si una tool declarada como
   de egreso recibe un argumento del que no se puede extraer host, el resultado es `Deny`: es
   justo el escenario en que un atacante ofuscaría el destino. Por eso `EgressViolation` lleva
   dos listas y el motivo distingue "fuera de la allowlist" de "no se pudo determinar" — el
   operador necesita saber si le falta una entrada o le falta parsear algo.

6. **Se verifica la declaración, no el tráfico.** Este guardrail vive en el proceso del servidor
   MCP y solo ve los argumentos; una tool que ignore su parámetro `url` y abra un socket a otro
   sitio no es detectable desde aquí. Contenerlo requiere control a nivel de red o sandbox de la
   tool, que está fuera del alcance de una librería de guardrails y se declara en No-goals para
   que nadie lo dé por cubierto.

7. **El motivo cita hosts y rutas de argumento, nunca el valor completo, y se trunca a 5.** Una
   URL denegada puede llevar un token en la query string, y el motivo acaba en el mensaje de
   error que lee el modelo — la misma regla que `credential-leak-guard` aplica a sus hallazgos.
   El truncado a cinco elementos por lista, con `" and N more"`, evita que una tool con
   doscientos destinatarios produzca un motivo de miles de caracteres: el operador necesita saber
   qué falló, no la lista completa.
