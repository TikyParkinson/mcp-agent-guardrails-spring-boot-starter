# guardrails-core — `ScanBudget` como tipo compartido

> Extensión de `guardrails-core` bajo `ARCHITECTURE.md §5.1`.
> Consumidores reales: `guardrails-injection-guard` y `guardrails-credential-leak-guard`.

## 1. Problema

El spec del presupuesto de escaneo (`guardrails-scan-budget-spec.md`, §2) decidió declarar
`ScanBudget` **una vez por módulo**, con este razonamiento: §5 prohíbe que un guardrail dependa de
otro, y el tipo parecía demasiado pequeño para justificar tocar core.

Al implementarlo los dos ficheros salieron idénticos byte a byte salvo la línea del `package`:
22 líneas duplicadas que SonarCloud reporta como tales en la PR. La duplicación no es el problema
de fondo, es el síntoma.

El problema de fondo es que **los dos números tienen que coincidir**. `injection-guard` y
`credential-leak` recorren el mismo mapa de argumentos en la misma invocación. Si uno se rinde a
los 10.000 nodos y el otro a los 5.000, el mismo payload queda truncado en dos sitios distintos;
un operador no tiene forma de ver esa diferencia y un atacante sí puede medirla, probando dónde
deja de mirar cada uno. Dos constantes separadas que «casualmente» valen lo mismo son una invariante
sin nadie que la sostenga: el primer cambio en un módulo la rompe en silencio.

## 2. Qué se añade a core

Un único tipo de valor, sin comportamiento más allá de su propia validación:

```java
package io.github.tikyparkinson.mcpguardrails.core.domain;

/** How much of a structure a scan is allowed to walk. */
public record ScanBudget(int maxNodes, int maxDepth) {
  public static ScanBudget defaults();   // 10_000 nodes, 64 levels
}
```

No hay puerto nuevo, ni método nuevo en un puerto existente, ni nada que registrar. Los dos módulos
que lo usan ya dependen de `guardrails-core`, así que no aparece ninguna dependencia nueva en
ningún `pom.xml`.

## 3. Por qué core y no otro sitio

- **No hay módulo de utilidades y crear uno es peor.** Un `guardrails-commons` sería un módulo más
  que publicar, versionar y documentar para alojar un `record` de dos enteros, y el primer tipo que
  entra en un cajón así nunca es el último.
- **No cabe en ninguno de los dos guardrails.** Ponerlo en uno y hacer que el otro lo importe es
  exactamente lo que prohíbe §5, y es la violación que esta misma rama está quitando de otros tres
  módulos.
- **Core ya es el sitio donde viven los tipos que comparten los guardrails.** `ToolInvocationContext`,
  `GuardrailDecision` y `AgentId` están ahí por la misma razón: los necesita más de un módulo y
  ninguno de ellos es su dueño.
- **Sirve a quien escriba un guardrail fuera de este repositorio.** Core es el SPI publicado; un
  tercero que recorra argumentos hereda el mismo tope y el mismo criterio sin copiarlo.

## 4. Comprobación contra las condiciones de §5.1

| Condición de §5.1 | Cumplimiento |
|---|---|
| **Aditivo únicamente** | Tipo nuevo. Ningún tipo existente de core cambia de firma ni de semántica. |
| **Neutro si no se usa** | Es un valor inmutable sin efectos: un guardrail que lo ignore se comporta igual que en la versión anterior. No hay nada que registrar ni ningún bean que cambie. |
| **Motivado por un consumidor real** | Dos, ya implementados en esta misma rama. No es especulativo. |
| **Spec propio** | Este documento. |
| **No es un breaking change** | Sí lo es para quien importara `injectionguard.domain.ScanBudget` o `credentialleak.domain.ScanBudget` por su FQN, que existieron solo dentro de esta rama y nunca en una versión publicada. Para 0.2.0-SNAPSHOT hacia fuera, es puramente aditivo. |

## 5. Decisiones de diseño

1. **`defaults()` se queda en el tipo, no en cada módulo.** Es lo que hace que la invariante se
   sostenga sola: los dos guardrails no comparten «un número», comparten *el* número. Un módulo que
   necesite otro tope construye su propia instancia — el tipo no lo impide, solo hace que apartarse
   del valor común sea visible en el código.

2. **No se toca `guardrails-anomaly-detector`.** Sigue con su `MAX_DEPTH = 8` en
   `CanonicalArguments`, y es correcto: ahí truncar no es perder cobertura sino **aumentar** la
   detección, porque hace colisionar las huellas de llamadas parecidas. El concepto es el mismo, el
   criterio no, y forzarle este tipo cambiaría su comportamiento sin motivo.

3. **El tipo no valida contra un máximo.** Un operador puede configurar 10 millones de nodos y
   hacerse daño. Se prefiere eso a inventar un techo arbitrario: el javadoc da el coste medido
   (unos 3 ms en el peor caso que admite el valor por defecto) para que la decisión sea informada.

4. **Sigue viviendo en `domain`, no en `application/port`.** No es un punto de extensión que nadie
   implemente: es un valor. Ponerlo entre los puertos sugeriría que se puede sustituir, y no hay
   nada que sustituir.
