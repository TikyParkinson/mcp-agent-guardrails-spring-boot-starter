# guardrails-credential-leak-guard — secretos ofuscados y claves de mapa

> Corrige **F-2** y **F-5** del informe de validación de 0.2.0.
> Toca únicamente `guardrails-credential-leak-guard`.

## 1. Problema y alcance

**F-5.** El escáner de entrada recorre los argumentos y aplana los mapas, pero solo mira los
**valores**: la clave se usa para construir el `path` y nunca se compara contra los patrones.

```
in : {"payload":{"AKIAIOSFODNN7EXAMPLE":"valor"}}   →  permitido
out: Payload stored: {[REDACTED:aws-access-key-id]=valor}
```

La cadena de salida sí la redactó cuando la tool la devolvió, así que la defensa en profundidad
funcionó, pero la entrada tiene un hueco que no debería tener.

**F-2.** Un secreto ofuscado no se detecta. De las tres formas que probó el informe, solo una
merece código:

| Forma | ¿El receptor recupera el secreto? | Decisión |
|---|---|---|
| `sk-proj-A1b2…` en claro | — | ya se detecta |
| Base64 del mismo secreto | **sí**, decodificando | se detecta (nuevo) |
| Partido por un espacio | sí, uniendo | no se persigue |
| Invertido | sí, invirtiendo | no se persigue |

La diferencia no es el esfuerzo del atacante sino qué escenario cubre el módulo. Su propósito
declarado es mantener las credenciales fuera del bucle, y el caso que domina es el **accidente**:
un `.env` que una tool devuelve, un token que el agente pega en un argumento. Un `.env` serializado
en base64 dentro de un JSON es exactamente ese caso y hoy pasa entero. Partir una clave por la
mitad o invertirla, en cambio, no ocurre por accidente jamás.

**No-goals**

- No se persigue el secreto partido ni el invertido. Ver decisión de diseño 2.
- No se descifra nada. Base64 es una codificación, no cifrado; un secreto cifrado con AES está
  fuera del alcance de cualquier detector por patrones y perseguirlo es una promesa falsa.
- No se decodifica recursivamente. Ver decisión de diseño 4.
- No se normalizan homoglifos, al contrario que en `injection-guard`. Ver decisión de diseño 5.
- No se cambian los veredictos, los puertos ni las acciones configurables.

## 2. Modelo de dominio

Un tipo nuevo en `credentialleak.domain`:

```java
/** Recovers text hidden inside a Base64 payload so the patterns can see it. */
public final class Base64Decoder {
  private Base64Decoder() {}

  /** The decoded text, or empty when the value is not decodable Base64 worth scanning. */
  public static Optional<String> decode(String value);
}
```

**Invariantes**

- `null` de entrada lanza `NullPointerException`.
- Devuelve vacío, nunca `null`, cuando el valor no es base64 decodificable o queda fuera de los
  límites de tamaño.
- **No lanza jamás por una entrada mal formada.** Un argumento que casi parece base64 es lo normal,
  no una excepción.
- Solo devuelve texto imprimible. Decodificar bytes arbitrarios produce basura binaria que no
  puede contener un secreto en forma de texto y solo añadiría ruido a los patrones.

`FlattenedValue` gana la noción de que un valor puede venir de una clave, no de un valor:

```java
record FlattenedValue(String path, String value) {}   // sin cambios de forma
```

El origen se distingue en el `path`, no en el tipo. Ver decisión de diseño 3.

## 3. Puertos

**Ninguno nuevo.** `SecretPatternSetPort` no cambia.

## 4. Caso de uso

`ScanToolArgumentsForSecretsService` no cambia de forma. Cambian dos pasos del aplanado y uno del
escaneo:

1. `ValueFlattener` recorre los argumentos hasta `MAX_DEPTH = 8`, como ya hace.
2. **Nuevo (F-5):** al recorrer un mapa, además del valor emite la **clave** como valor escaneable,
   con el path `<padre>{<clave>}`. Las llaves distinguen «el secreto estaba en el nombre del campo»
   de «estaba en su contenido», que son incidentes distintos para quien investiga.
3. **Nuevo (F-2):** por cada valor de texto, si `Base64Decoder.decode` devuelve algo, ese texto
   decodificado se escanea **además** del original, con el path `<path>(base64)`.
4. Los patrones se aplican como hasta ahora. Los veredictos no cambian: `CONFIRMED` ⇒ la acción
   configurada para entrada confirmada, `SUSPECTED` ⇒ la suya, limpio ⇒ `Allow`.

El sufijo del path es la única señal de que hubo decodificación. El valor decodificado **no se
registra en ningún sitio**, igual que el original: el módulo nunca escribe lo que detecta.

## 5. Adaptadores esperados

Ninguno nuevo. `CredentialLeakGuardrail` y `CredentialLeakResultGuardrail` no se tocan.

La cadena de salida (`SecretRedactor`) **no** decodifica base64. Ver decisión de diseño 6.

## 6. Configuración Spring Boot

Ninguna property nueva. Se consideró un interruptor para la decodificación y se descartó: su único
efecto sería debilitar la detección, y el coste medido no justifica ofrecer la opción de apagarla.

## 7. Dependencias Maven propuestas

**Ninguna nueva.** `java.util.Base64` viene en el JDK.

## 8. Diagrama del hexágono

```
adapter-in                     application                    domain
┌───────────────────────┐      ┌──────────────────┐          ┌───────────────────────────┐
│ CredentialLeak        │─────▶│ ScanToolArguments│─────────▶│ ValueFlattener            │
│   Guardrail           │      │  ForSecretsUse   │          │   claves + valores  ← F-5 │
│  (sin cambios)        │      │  Case            │          ├───────────────────────────┤
├───────────────────────┤      │                  │          │ Base64Decoder     ← nuevo │
│ CredentialLeakResult  │─────▶│ RedactToolResult │          │   escanea tambien el      │
│   Guardrail           │      │  UseCase         │          │   contenido decodificado  │
│  (sin cambios,        │      │  (sin cambios)   │          ├───────────────────────────┤
│   no decodifica)      │      └────────┬─────────┘          │ SecretScanner / Redactor  │
└───────────────────────┘               │                    └───────────────────────────┘
                                        ▼
                              SecretPatternSetPort
                                        │
                                        ▼
                              InMemorySecretPatternSetAdapter (sin cambios)
```

## Decisiones de diseño

1. **Base64 sí, y no por completitud.** Medido: decodificar revela el secreto que el patrón no veía,
   y mil decodificaciones de una cadena de 56 caracteres cuestan **0,77 ms**. El filtro previo —que
   el valor tenga pinta de base64— descarta el texto corriente antes de intentarlo: de seis valores
   típicos de una llamada real, sólo dos entraban a decodificar, y ambos daban basura que no casa
   con ningún patrón.

   El argumento de fondo no es el coste, es el escenario. Un `.env` o un JSON de configuración
   codificado en base64 es algo que ocurre sin que nadie intente engañar a nadie, y es justo lo que
   este módulo existe para atrapar.

2. **El secreto partido y el invertido no se persiguen, y conviene decir por qué.** Ambos preservan
   el secreto —el receptor puede rehacerlo— así que técnicamente son exfiltración igual que el
   base64. La diferencia es que **no ocurren por accidente**. Detectarlos exigiría probar
   permutaciones del texto, y el ratio de falsos positivos de buscar «un secreto partido en
   cualquier sitio» haría el guardrail inservible.

   Y hay un límite honesto que conviene reconocer: contra un agente que quiera exfiltrar
   deliberadamente, ningún detector por patrones sirve — le basta con cifrar. Este módulo cubre el
   accidente y el descuido, que es el 99 % de las fugas reales, y el README debe decirlo con esas
   palabras en vez de dejar que se suponga otra cosa.

3. **El origen «clave» se marca en el path, no en un tipo nuevo.** Añadir un campo a
   `FlattenedValue` obligaría a tocar a todos sus consumidores para transportar algo que solo se usa
   al construir el mensaje. `arguments.payload{AKIA…}` frente a `arguments.payload.token` ya
   distingue los dos casos para quien lee el motivo, y las llaves no aparecen hoy en ningún path
   generado, así que no hay ambigüedad.

4. **No se decodifica recursivamente.** Un base64 dentro de otro base64 es ofuscación deliberada, no
   accidente, y una decodificación recursiva sin tope es una bomba de descompresión esperando a
   alguien. Un nivel cubre el caso real.

5. **No se normalizan homoglifos aquí, al revés que en `injection-guard`.** Es la simetría que
   parece obvia y sería un error. Verificado: `"sk-proj-ABC".equals("sk-pr`+`о`+`j-ABC")` es
   `false`, así que un secreto con una `о` cirílica **no autentica contra nada** y no es un secreto.
   En `injection-guard` el destinatario es un modelo, que lee glifos y obedece; aquí es una API, que
   compara bytes. Normalizar sólo produciría falsos positivos sobre texto que no es una credencial.

6. **La cadena de salida no decodifica.** Detectar un secreto dentro de un base64 en la respuesta
   plantearía qué redactar: sustituir el blob entero destruye datos legítimos que viajaban con él, y
   re-codificar una versión redactada cambia lo que la tool devolvió sin que nadie lo sepa — que es
   exactamente lo que el módulo se prohíbe a sí mismo en la entrada («los argumentos nunca se
   reescriben»). La entrada puede permitirse decodificar porque solo **decide**; la salida
   **transforma**, y ahí la regla es no tocar lo que no se entiende.

7. **Sin property para apagarlo.** Una opción que solo sirve para detectar menos no es una opción, es
   una trampa para el operador que la encuentra buscando cómo quitarse un falso positivo de encima.

---

**Listo para `domain-builder`: implementar
`docs/specs/guardrails-credential-leak-obfuscation-spec.md`.**
