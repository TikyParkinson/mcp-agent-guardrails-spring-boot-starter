# Spec — guardrails-credential-leak-guard

> Módulo 8 según ARCHITECTURE.md §6. Autor: spec-architect.
> Paquete raíz: `io.github.tikyparkinson.mcpguardrails.credentialleak`.
> Prerequisitos cumplidos: `guardrails-tool-integrity-DONE.md` y
> `guardrails-core-outbound-spi-DONE.md` aprobados (este módulo es el primer consumidor del SPI
> de salida).

## 1. Problema y alcance

Las credenciales viajan en texto plano por las dos direcciones de una llamada MCP: el agente
puede **enviar** una API key como argumento (`deploy(token="sk-live-…")`) y una tool puede
**devolver** un `.env`, un log o un connection string con la contraseña dentro. En el segundo
caso el secreto acaba en la ventana de contexto del modelo, y de ahí en cualquier sitio al que
el modelo escriba después. `guardrails-credential-leak-guard` inspecciona ambos lados contra un
conjunto de patrones de detección, **deniega o escala** en la entrada y **redacta o bloquea** en
la salida, sin escribir nunca el valor detectado en ningún sitio.

Es el primer módulo que implementa los dos SPI de `guardrails-core`: `Guardrail` (entrada) y
`ResultGuardrail` (salida).

**No-goals:**

- No usa LLM-as-judge ni entropía estadística: detección determinista por patrones. El puerto
  `SecretPatternSetPort` permite enchufar otra fuente (Vault, un servicio de detección, reglas
  dinámicas).
- **No redacta los argumentos de entrada**: en la entrada decide (`Allow`/`Deny`/`Escalate`), no
  transforma. El SPI `Guardrail` de core no permite reescribir la invocación, y hacerlo sería
  peligroso: la tool recibiría datos distintos a los que el agente pidió, de forma invisible para
  ambos. Un secreto en los argumentos se para, no se maquilla. La redacción existe solo en la
  salida, donde el contenido ya no altera ninguna ejecución.
- No valida si la credencial es real ni la revoca: detecta formato, no vigencia.
- No redacta `structuredContent` de los resultados: el SPI de salida lo expone en solo lectura,
  así que un hallazgo ahí se responde con `Block` (ver Decisión de diseño 3).
- No inspecciona contenidos binarios (`ImageContent`, `BlobResourceContents`) ni `ResourceLink`:
  el SPI de salida solo expone texto redactable. Un secreto exfiltrado por una URI es un problema
  de egress (módulo 9), no de este guardrail.
- No registra nada en el bus de auditoría: ARCHITECTURE.md §5 prohíbe importar otro módulo
  `guardrails-*`. La traza queda en el `ChainVerdict`/`ResultVerdict` de core.
- No persiste nada. Sin store real ⇒ **sin Testcontainers** (ARCHITECTURE.md §8).
- No incluye autoconfiguración: el cableado (incluida la primera bean de `ResultGuardrailChain`
  del proyecto) va en `spring-boot-starter`, en su propia rama.

## 2. Modelo de dominio

Paquete `io.github.tikyparkinson.mcpguardrails.credentialleak.domain`. JDK puro
(`java.util.regex`), autocontenido, sin dependencias de core.

```java
/**
 * Confianza en que el valor detectado sea realmente una credencial.
 * CONFIRMED: el formato es inequívoco (prefijo propietario, estructura JWT, cabecera PEM).
 * SUSPECTED: heurística por palabra clave ("password=…"), puede dar falsos positivos.
 */
enum SecretSeverity { SUSPECTED, CONFIRMED }

/**
 * Patrón de detección. Invariantes: id no blank; pattern no null; severity no null;
 * secretGroup >= 0 y <= pattern.matcher("").groupCount().
 *
 * secretGroup identifica qué grupo de captura contiene el valor sensible:
 *   0 = el match completo (patrones de token puro, sin clave delante);
 *   n > 0 = solo ese grupo, cuando la regex incluye también la clave ("password=", "Bearer ").
 * Es el mismo mecanismo que el campo `secretGroup` de gitleaks, y evita que la redacción
 * destruya la estructura del texto (ver Decisión de diseño 2).
 *
 * Factories: SecretPattern.of(String id, String regex, SecretSeverity)                 [grupo 0]
 *            SecretPattern.of(String id, String regex, SecretSeverity, int secretGroup)
 *            SecretPattern.ofLiteral(String id, String value, SecretSeverity)
 * Las dos primeras compilan con Pattern.CASE_INSENSITIVE; regex inválida ⇒
 * IllegalArgumentException; secretGroup fuera de rango ⇒ IllegalArgumentException.
 * ofLiteral cita el valor con Pattern.quote y compila SIN CASE_INSENSITIVE (un secreto es
 * sensible a mayúsculas); es la vía para sets alimentados desde un gestor de secretos
 * (Vault, CyberArk) — ver Decisión de diseño 8.
 */
record SecretPattern(String id, Pattern pattern, SecretSeverity severity, int secretGroup) {}

/**
 * Hallazgo. NUNCA contiene el valor detectado ni un fragmento suyo: solo qué patrón saltó y
 * dónde. Es lo que hace que este guardrail no reintroduzca la fuga que previene.
 * location: ruta del valor, p. ej. "arguments.token", "arguments.items[2]",
 *           "result.text[0]", "result.structured.connection".
 * Invariantes: patternId y location no blank; severity no null.
 * Derivados: describe() -> "patternId@location" (ej. "openai-api-key@arguments.token"), la forma
 *            compacta que los adaptadores usan para construir el motivo de una decisión (§5.1 y
 *            §5.2). Sigue sin exponer el valor detectado.
 */
record SecretFinding(String patternId, SecretSeverity severity, String location) {}

/**
 * Resultado de escanear un conjunto de valores.
 * Invariantes: findings inmutable (List.copyOf), nunca null.
 * Derivados: clean() == findings.isEmpty();
 *            highestSeverity() -> Optional<SecretSeverity> (CONFIRMED > SUSPECTED).
 */
record SecretScanResult(List<SecretFinding> findings) {}

/**
 * Texto ya saneado más los hallazgos que lo justifican.
 * Invariantes: sanitizedText no null; findings inmutable.
 */
record RedactedText(String sanitizedText, List<SecretFinding> findings) {}

/**
 * Aplanador de estructuras arbitrarias a pares (ruta, texto), compartido por el escaneo de
 * argumentos y el de structuredContent. Clase final ValueFlattener:
 *   static List<FlattenedValue> flatten(Map<String, Object> values)
 * Recorre Map y List recursivamente hasta profundidad 8 (más profundo se ignora, para acotar
 * bombas de anidamiento); toma los String; ignora números, booleanos y null.
 * Rutas estilo "token", "db.password", "items[2]".
 */
record FlattenedValue(String path, String text) {}
final class ValueFlattener { ... }

/**
 * Escáner puro: aplica todos los patrones a todos los valores aplanados.
 * Clase final SecretScanner:
 *   static SecretScanResult scan(Map<String, Object> values, List<SecretPattern> patterns,
 *                                String locationPrefix)
 * locationPrefix se antepone a cada ruta ("arguments", "result.structured").
 */
final class SecretScanner { ... }

/**
 * Redactor puro. Clase final SecretRedactor:
 *   static RedactedText redact(String text, List<SecretPattern> patterns, String location)
 * Para cada coincidencia sustituye **solo el grupo `secretGroup`** del patrón (o el match
 * completo si `secretGroup == 0`) por el marcador "[REDACTED:<patternId>]". Así
 * "DB_PASSWORD=hunter2000" queda "DB_PASSWORD=[REDACTED:credential-assignment]" y no
 * "DB_[REDACTED:credential-assignment]" (ver Decisión de diseño 2).
 * No conserva prefijo ni sufijo del valor. Sin coincidencias devuelve el mismo texto y una
 * lista vacía.
 */
final class SecretRedactor { ... }

/**
 * Patrones por defecto. Clase final BuiltInSecretPatterns con
 *   static List<SecretPattern> defaults()
 * conteniendo exactamente estos 11, en este orden:
 */
```

| id | Regex (case-insensitive) | `secretGroup` | Severidad |
|---|---|---|---|
| `aws-access-key-id` | `AKIA[0-9A-Z]{16}` | 0 | CONFIRMED |
| `aws-secret-access-key` | `(aws_secret_access_key\s*[=:]\s*)(\S{40})` | 2 | CONFIRMED |
| `openai-api-key` | `sk-(?:proj-)?[A-Za-z0-9_-]{20,}` | 0 | CONFIRMED |
| `github-token` | `gh[pousr]_[A-Za-z0-9]{36,}` | 0 | CONFIRMED |
| `slack-token` | `xox[baprs]-[A-Za-z0-9-]{10,}` | 0 | CONFIRMED |
| `google-api-key` | `AIza[0-9A-Za-z_-]{35}` | 0 | CONFIRMED |
| `jwt` | `eyJ[A-Za-z0-9_-]{8,}\.eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}` | 0 | CONFIRMED |
| `private-key-block` | `-----BEGIN (?:RSA \|EC \|DSA \|OPENSSH \|PGP )?PRIVATE KEY-----` | 0 | CONFIRMED |
| `connection-string-password` | `((?:postgres(?:ql)?\|mysql\|mongodb(?:\+srv)?\|redis\|amqp)://[^:/\s]+:)([^@\s]+)(?=@)` | 2 | CONFIRMED |
| `bearer-token` | `(bearer\s+)([A-Za-z0-9._~+/-]{16,})` | 2 | CONFIRMED |
| `credential-assignment` | `((?:password\|passwd\|pwd\|secret\|api[_-]?key\|access[_-]?token)\s*[=:]\s*["']?)([^\s"',;]{8,})` | 2 | SUSPECTED |

Los 11 se verificaron ejecutándolos antes de fijarlos: cada uno detecta su caso real, la
redacción por `secretGroup` no deja rastro del valor, y ninguno dispara sobre texto benigno
(`"the weather is sunny today"`, `"user id 12345 logged in"`, una URL corriente).
`connection-string-password` usa un lookahead `(?=@)` para no incluir la arroba en el grupo.

## 3. Puertos (contratos de application)

### 3.1 `ScanToolArgumentsForSecretsUseCase` — puerto de entrada

- Capa: `application.port.in`. Lo invoca `CredentialLeakGuardrail` (adapter-in).
- Lo implementa: `ScanToolArgumentsForSecretsService`.

```java
public interface ScanToolArgumentsForSecretsUseCase {
  /** Escanea los argumentos con los patrones vigentes. Nunca null. */
  SecretScanResult scan(Map<String, Object> arguments);
}
```

### 3.2 `RedactToolResultUseCase` — puerto de entrada

- Capa: `application.port.in`. Lo invoca `CredentialLeakResultGuardrail` (adapter-in).
- Lo implementa: `RedactToolResultService`.

```java
public interface RedactToolResultUseCase {
  /**
   * Redacta los textos del resultado y escanea (sin redactar) su contenido estructurado.
   * Nunca null. sanitizedContents tiene siempre el mismo tamaño que textContents, tal como
   * exige el contrato de Redact en core.
   */
  ResultRedaction redact(List<String> textContents, Map<String, Object> structuredContent);
}

/**
 * Vive en application (no es dominio puro: agrega dos escaneos distintos).
 * Invariantes: las tres listas inmutables y no null.
 */
public record ResultRedaction(
    List<String> sanitizedContents,
    List<SecretFinding> textFindings,
    List<SecretFinding> structuredFindings) {}
```

### 3.3 `SecretPatternSetPort` — puerto de salida (lo plugable)

- Capa: `application.port.out`.
- Lo implementa: `InMemorySecretPatternSetAdapter` (por defecto). Sustituible por un feed
  dinámico de patrones.

```java
public interface SecretPatternSetPort {
  /** Patrones vigentes, en orden de evaluación. Nunca null; puede ser vacía. */
  List<SecretPattern> activePatterns();
}
```

Se invoca **en cada evaluación**, lo que permite rotar patrones en caliente sin reiniciar. La
contrapartida es que un adaptador que consulte un sistema remoto (Vault, CyberArk) **debe
cachear con TTL**: sin caché, su latencia se suma a todas las llamadas a tools. El puerto no
cachea por su cuenta para no imponer una política de frescura a quien no la quiera.

## 4. Casos de uso

### 4.1 `ScanToolArgumentsForSecretsService`

Constructor: `(SecretPatternSetPort patternSetPort)`.

1. Valida `arguments` no null.
2. `patterns = patternSetPort.activePatterns()`.
3. Devuelve `SecretScanner.scan(arguments, patterns, "arguments")`.

### 4.2 `RedactToolResultService`

Constructor: `(SecretPatternSetPort patternSetPort)`.

1. Valida `textContents` y `structuredContent` no null.
2. `patterns = patternSetPort.activePatterns()`.
3. Para cada texto `i`: `RedactedText r = SecretRedactor.redact(texts.get(i), patterns, "result.text[i]")`;
   acumula `r.sanitizedText()` y `r.findings()`.
4. `structuredFindings = SecretScanner.scan(structuredContent, patterns, "result.structured").findings()`.
5. Devuelve `new ResultRedaction(sanitized, textFindings, structuredFindings)`.

La traducción a `GuardrailDecision` / `ResultDecision` es de los adaptadores.

## 5. Adaptadores esperados

### 5.1 Adapter-in de entrada: `CredentialLeakGuardrail implements Guardrail`

Paquete `credentialleak.adapter.in.chain`.

- `name()` = `"credential-leak"`; `order()` = `60` — después de `injection-guard` (50): ambos
  miran contenido, y el orden fijo hace la traza reproducible.
- `evaluate(context)`:
  1. `result = useCase.scan(context.arguments())`.
  2. `result.clean()` ⇒ `Allow`.
  3. `highestSeverity() == CONFIRMED` ⇒ acción configurada `onConfirmedInput` (default `DENY`).
  4. Solo `SUSPECTED` ⇒ acción configurada `onSuspectedInput` (default `ESCALATE`).
  5. El `reason` enumera `patternId@location` (p. ej.
     `"credential detected in tool arguments (openai-api-key@arguments.token)"`), **nunca el
     valor**.

### 5.2 Adapter-in de salida: `CredentialLeakResultGuardrail implements ResultGuardrail`

Paquete `credentialleak.adapter.in.chain`.

- `name()` = `"credential-leak"`; `order()` = `0` (cadena de salida independiente; el nombre
  puede repetirse porque las cadenas son distintas).
- `inspect(context)`:
  1. `redaction = useCase.redact(context.textContents(), context.structuredContent())`.
  2. Si `structuredFindings` no está vacío ⇒ `Block("credential detected in structured result
     (<patternId@location>, …); structured content cannot be redacted")`. **Siempre bloquea**,
     con independencia de la severidad y de la configuración: no hay forma de sanearlo.
  3. Si no, y `textFindings` está vacío ⇒ `PassThrough`.
  4. Si hay `textFindings` ⇒ acción configurada `onOutputText` (default `REDACT`):
     - `REDACT` ⇒ `Redact(redaction.sanitizedContents(), "<patternId@location>, …")`;
     - `BLOCK` ⇒ `Block(...)` con el mismo detalle.

### 5.3 Adapter-out: `InMemorySecretPatternSetAdapter`

Paquete `credentialleak.adapter.out.patterns`. Recibe `List<SecretPattern>` por constructor y la
devuelve tal cual (copia inmutable). El starter la construirá con
`BuiltInSecretPatterns.defaults()` (si `built-in-patterns-enabled`) más los patrones extra de
properties, registrada `@ConditionalOnMissingBean`.

## 6. Configuración Spring Boot

Prefijo `mcp.guardrails.credential-leak`. Record `GuardrailsCredentialLeakProperties` con
`@ConstructorBinding` y `@DefaultValue` en cada parámetro.

| Propiedad | Tipo | Default | Significado |
|---|---|---|---|
| `enabled` | `boolean` | `true` | Registra ambos guardrails (entrada y salida). |
| `built-in-patterns-enabled` | `boolean` | `true` | Incluye los 11 patrones por defecto. |
| `on-confirmed-input` | `InputAction` (`DENY`/`ESCALATE`) | `DENY` | Credencial inequívoca en los argumentos. |
| `on-suspected-input` | `InputAction` (`DENY`/`ESCALATE`) | `ESCALATE` | Coincidencia heurística en los argumentos. |
| `on-output-text` | `OutputAction` (`REDACT`/`BLOCK`) | `REDACT` | Credencial en el texto del resultado. |
| `custom-patterns` | `List<CustomPattern>` | vacía | Patrones extra: `{ id, regex, severity, secretGroup }`. `secretGroup` por defecto `0` (redacta el match completo); usa un grupo cuando la regex incluya la clave además del valor. |

No hay propiedad para el contenido estructurado: es siempre `Block` (Decisión de diseño 3).
`InputAction` y `OutputAction` viven en `adapter.in.chain`, junto al guardrail que las consume.

## 7. Dependencias Maven propuestas

| Dependencia | Scope | Por qué |
|---|---|---|
| `io.github.tikyparkinson:guardrails-core` | compile | Los dos SPI (`Guardrail`, `ResultGuardrail`) y el modelo de decisiones que este módulo produce. |
| `org.springframework.boot:spring-boot` (BOM 4.1.0) | provided | Única anotación usada: `@ConfigurationProperties` (+ `@ConstructorBinding`/`@DefaultValue`) en `infrastructure`. |
| `org.junit.jupiter:junit-jupiter` (BOM 6.1.2) | test | Tests unitarios. |
| `org.mockito:mockito-core` | test | Doble de `SecretPatternSetPort` en los tests de los servicios. |

Sin dependencias nuevas respecto a las que el proyecto ya usa; las versiones salen de los BOM
importados en el parent (verificados como GA en su momento: Spring Boot 4.1.0, JUnit 6.1.2,
Mockito 5.23.0, MCP SDK 2.0.0). Ninguna dependencia de otro módulo `guardrails-*` distinto de
core (ARCHITECTURE.md §5).

## 8. Diagrama del hexágono

```
 inbound MCP call                                   tool result
        │                                                │
        ▼                                                ▼
┌───────────────────────────┐              ┌──────────────────────────────────┐
│ adapter/in/chain          │              │ adapter/in/chain                 │
│ CredentialLeakGuardrail   │              │ CredentialLeakResultGuardrail    │
│ (Guardrail, order 60)     │              │ (ResultGuardrail, order 0)       │
│ Allow | Deny | Escalate   │              │ PassThrough | Redact | Block     │
└─────────────┬─────────────┘              └────────────────┬─────────────────┘
              │                                             │
              ▼                                             ▼
┌───────────────────────────────────────────────────────────────────────────┐
│ application                                                                │
│  port.in  ScanToolArgumentsForSecretsUseCase   RedactToolResultUseCase     │
│  usecase  ScanToolArgumentsForSecretsService   RedactToolResultService     │
│  port.out SecretPatternSetPort ◄──────────────────────────────────────────┼── implementado por
└─────────────────────────────────┬─────────────────────────────────────────┘   InMemorySecretPatternSetAdapter
                                  │                                             (adapter/out/patterns)
                                  ▼
┌───────────────────────────────────────────────────────────────────────────┐
│ domain (JDK puro)                                                          │
│  SecretSeverity, SecretPattern, SecretFinding, SecretScanResult            │
│  RedactedText, FlattenedValue                                              │
│  ValueFlattener, SecretScanner, SecretRedactor, BuiltInSecretPatterns      │
└───────────────────────────────────────────────────────────────────────────┘
```

## 9. Decisiones de diseño

1. **El hallazgo nunca lleva el valor detectado**, ni truncado ni enmascarado parcialmente: solo
   `patternId` + `location`. Un guardrail anti-fugas que escribiera el secreto en el motivo de un
   `Deny` —que acaba en el mensaje de error visible para el modelo— reintroduciría exactamente la
   fuga que previene. Coherente con "Never persist tool arguments" del README raíz.

2. **La redacción sustituye solo el valor por `[REDACTED:<patternId>]`, nunca la clave que lo
   precede, y no conserva prefijo ni sufijo del secreto.** Dos partes:

   - *Qué se sustituye*: cuatro de los once patrones (`credential-assignment`, `bearer-token`,
     `connection-string-password`, `aws-secret-access-key`) necesitan la clave en la regex para
     reconocer el valor, pero redactar el match completo destruiría el texto:
     `DB_PASSWORD=hunter2000` quedaría como `DB_[REDACTED:…]` y
     `postgresql://admin:s3cr3t@db:5432/app` como `[REDACTED:…]db:5432/app`. Se adopta el
     mecanismo `secretGroup` de gitleaks (`config/rule.go`: *"SecretGroup is an int used to
     extract secret from regex match"*), que redacta únicamente el grupo del valor y deja
     `DB_PASSWORD=[REDACTED:credential-assignment]`. La estructura sobrevive y el modelo entiende
     qué falta y por qué.
   - *Qué formato*: `[REDACTED:<patternId>]` sigue el estándar de facto de Microsoft Presidio,
     cuyo operador `replace` usa `<ENTITY_TYPE>` por defecto (`operators/replace.py`). No se
     conserva ningún carácter del original: la regla PCI-DSS de *"first six and last four digits
     maximum"* aplica a números de tarjeta, no a tokens de alta entropía, y no existe guía
     OWASP/NIST que justifique preservar bytes de un secreto. Es equivalente a `gitleaks --redact`
     al 100%, que produce literalmente `REDACTED`.

3. **Un hallazgo en `structuredContent` siempre produce `Block`, sin propiedad que lo relaje.**
   El SPI de salida expone el contenido estructurado en solo lectura (por diseño de
   `guardrails-core-outbound-spi-spec.md`, Decisión 3), así que no existe forma de devolverlo
   saneado. Ofrecer un `ALLOW` configurable sería ofrecer un fail-open sobre una fuga confirmada.

   El coste práctico es bajo y está medido: en Spring AI, `@McpTool.generateOutputSchema()` es
   `false` por defecto, de modo que la mayoría de las tools nunca rellenan `structuredContent`.
   Y cuando sí lo rellenan, `Block` es la **única** defensa posible: la spec MCP solo *recomienda*
   (SHOULD, no MUST) duplicar el JSON en un `TextContent`, y Spring AI no lo hace —construye el
   resultado con `CallToolResult.builder().structuredContent(x).build()`, dejando `content`
   vacío—, así que el secreto no está también en el texto y no hay nada que redactar.

4. **Dos casos de uso separados** en vez de uno con dos métodos: entrada y salida son momentos
   distintos, con contratos de retorno distintos, y ARCHITECTURE.md §7 pide una responsabilidad
   por clase. Comparten el puerto `SecretPatternSetPort`, que es lo que debe ser común.

5. **`ValueFlattener` se reimplementa aquí en vez de reutilizar el `ArgumentScanner` de
   `guardrails-injection-guard`.** ARCHITECTURE.md §5 prohíbe que un guardrail dependa de otro. La
   duplicación (~30 líneas de recorrido recursivo) es el precio explícito de esa regla; si algún
   día molesta, la solución es promover el aplanador a `guardrails-core` como extensión aditiva
   (§5.1), no importar el módulo vecino.

6. **El mismo `name()` (`"credential-leak"`) en los dos guardrails.** Son cadenas separadas con
   validación de unicidad independiente, y la traza gana claridad al ver que ambas decisiones
   provienen del mismo módulo.

7. **La severidad decide solo en la entrada.** En la salida, lo que determina la acción es si el
   hallazgo es redactable (texto ⇒ `Redact`) o no (estructurado ⇒ `Block`); un secreto `SUSPECTED`
   en el texto se redacta igual que uno `CONFIRMED`, porque redactar un falso positivo cuesta un
   `[REDACTED:…]` de más, mientras que no redactar un verdadero positivo cuesta la credencial.

8. **`SecretPattern.ofLiteral(id, value, severity)` para secretos gestionados.** El puerto ya
   permitía alimentar los patrones desde Vault, CyberArk o Azure Key Vault, pero obligaba a
   envolver cada valor en `Pattern.quote(...)` a mano; olvidarlo convierte un secreto con `.` o
   `+` en un regex que matchea de más, o que ni compila. El factory lo hace explícito y compila
   **sin** `CASE_INSENSITIVE`, porque un secreto sí distingue mayúsculas y relajarlo solo añadiría
   falsos positivos.

   Advertencia que el README debe recoger: un set alimentado con valores reales mantiene los
   secretos en claro en la memoria del proceso —precisamente donde este guardrail intenta que no
   estén—, el escaneo pasa a ser lineal en el número de secretos gestionados, y el adaptador es
   responsable de cachear con TTL (ver §3.3). Es una decisión consciente del operador, no el modo
   por defecto: el set por defecto detecta **por formato** y no necesita conocer ningún valor.
