# Escaneo completo de argumentos — F-10 y F-11

> Corrige **F-10** (alta) y **F-11** (baja) del informe de validación de 0.2.0.
> Toca `guardrails-credential-leak-guard` y `guardrails-injection-guard`.

## 1. Problema y alcance

Ambos guardrails aplanan los argumentos y paran en `MAX_DEPTH = 8`. Todo lo que hay más abajo no
se mira, así que envolver el payload en nueve capas los salta enteros:

```
profundidad 7   {"payload":{"l1":…{"l7":"AKIAIOSFODNN7EXAMPLE"}}}   denegado
profundidad 8   lo mismo, un nivel más                              PERMITIDO
profundidad 12  lo mismo                                            PERMITIDO
```

Igual con `injection-guard`, e igual con listas anidadas en vez de mapas. No cuesta nada al
atacante y no exige saber más que el número 8.

**El límite no protege de lo que parece.** Medido:

| Estructura | Nodos aplanados | 1000 escaneos |
|---|---|---|
| profundidad 8 | 9 | 25 ms |
| profundidad 1000 | 9 | 16 ms |
| anchura 1000 campos | 2000 | 175 ms (×100) |
| anchura 10000 campos | 20000 | 1019 ms (×100) |

La profundidad no cuesta CPU: el límite corta antes. Lo que cuesta es el **número de nodos**, y la
anchura no tiene ningún límite hoy. Un atacante que busque agotar CPU usa 10.000 campos planos, no
nueve niveles.

Lo único de lo que protege la profundidad es del desbordamiento de pila por recursión — y ni eso,
porque un recorrido sin límite aguanta 20.000 niveles sin romperse.

Así que hoy el límite es lo peor de los dos mundos: no evita el coste real y sí abre un bypass
trivial.

**No-goals**

- No se elimina el tope: una entrada hostil tiene que seguir estando acotada. Lo que cambia es
  **qué** se acota y **qué pasa** cuando se llega al tope.
- No se toca `guardrails-anomaly-detector`, que tiene el mismo `MAX_DEPTH` en
  `CanonicalArguments`. Ver decisión de diseño 4.
- No se recorre en profundidad ilimitada. Ver decisión 3.
- No se cambian los patrones, las reglas, los veredictos ni los puertos.

## 2. Modelo de dominio

Un tipo nuevo por módulo, con la misma forma en los dos — no se comparte, porque §5 prohíbe que un
guardrail dependa de otro y esto es demasiado poco para justificar una extensión de core:

```java
/** How much of a structure a scan is allowed to walk, and whether it fitted. */
public record ScanBudget(int maxNodes, int maxDepth) {
  public static ScanBudget defaults();   // 10_000 nodes, 64 levels
}
```

Y el resultado del aplanado deja de ser una lista pelada:

```java
/** The values found, and whether the walk finished or ran out of budget. */
public record FlattenedArguments(List<FlattenedValue> values, boolean complete) {}
```

**Invariantes**

- `complete == false` significa **exactamente** «hay partes de estos argumentos que nadie ha
  mirado». No es una advertencia: es la señal que hace que el guardrail deniegue.
- `values` siempre contiene lo que sí se recorrió, aunque `complete` sea `false`. Un secreto
  encontrado antes de agotar el presupuesto se reporta igual, y con su motivo real en vez de con
  el genérico.
- `maxNodes` y `maxDepth` son positivos.

## 3. Puertos

**Ninguno nuevo.** `SecretPatternSetPort` e `InjectionRuleSetPort` no cambian.

## 4. Caso de uso

Ambos servicios ganan el mismo paso, y ninguno cambia de firma.

1. Se aplana con presupuesto. El recorrido lleva la cuenta de nodos visitados y de profundidad.
2. Si se agota cualquiera de los dos topes, el recorrido para y marca `complete = false`.
3. Se aplican los patrones o las reglas a lo recorrido, como hasta ahora.
4. **Si hay hallazgos**, gana el hallazgo: el veredicto es el de siempre (`Deny` / `Escalate`), con
   su motivo concreto. Que además el recorrido quedara incompleto no cambia nada — ya hay una razón
   mejor para parar la llamada.
5. **Si no hay hallazgos y `complete == false`**, el veredicto es `Deny` con el motivo
   `arguments too large to scan (limit: N nodes, M levels)`. Un argumento que no se ha podido
   mirar no es un argumento limpio.
6. Si no hay hallazgos y el recorrido fue completo, `Allow`, como siempre.

El orden del punto 4 importa: **el motivo que llega al agente debe ser el más específico
disponible**. «Se detectó una credencial» le dice qué corregir; «no pude escanear» le dice que
pruebe con otra forma.

## 5. Adaptadores esperados

Ninguno nuevo. `CredentialLeakGuardrail` e `InjectionGuardrail` traducen el resultado como ya hacen.

## 6. Configuración Spring Boot

Dos properties nuevas por módulo, con el mismo nombre y el mismo valor en ambos:

| Property | Tipo | Default |
|---|---|---|
| `mcp.guardrails.credential-leak.max-scan-nodes` | `int` | `10000` |
| `mcp.guardrails.credential-leak.max-scan-depth` | `int` | `64` |
| `mcp.guardrails.injection-guard.max-scan-nodes` | `int` | `10000` |
| `mcp.guardrails.injection-guard.max-scan-depth` | `int` | `64` |

Los valores por defecto salen de la medición, no de la intuición: 10.000 nodos son ~500 ms de
escaneo en el peor caso medido, y 64 niveles son ocho veces lo que había sin acercarse al
desbordamiento de pila. Una llamada legítima no se acerca ni de lejos — los argumentos de las
dieciséis tools de la demo son escalares, profundidad 1.

Son configurables porque el umbral correcto depende del despliegue, y porque subirlos es una
decisión que un operador puede necesitar tomar sin recompilar. Bajarlos hasta hacer que todo
falle es posible, pero falla cerrado: el peor resultado es denegar de más.

## 7. Dependencias Maven propuestas

**Ninguna nueva** en ninguno de los dos módulos.

## 8. Diagrama del hexágono

```
   argumentos de la tool
            │
            ▼
   ┌─────────────────────────────────────────┐
   │ ValueFlattener / ArgumentScanner        │
   │   recorre con ScanBudget                │
   │   cuenta nodos y niveles                │
   └───────────────┬─────────────────────────┘
                   │
                   ▼
        FlattenedArguments(values, complete)
                   │
        ┌──────────┴───────────┐
        ▼                      ▼
   hay hallazgos          sin hallazgos
        │                      │
        ▼              ┌───────┴────────┐
   Deny / Escalate     ▼                ▼
   con su motivo   complete=false   complete=true
                        │                │
                        ▼                ▼
                  Deny "too large"     Allow
```

## Decisiones de diseño

1. **Se acota por nodos, no por profundidad, porque eso es lo que cuesta.** La medición lo deja
   claro: profundidad 1000 aplana nueve nodos y cuesta lo mismo que profundidad 8, mientras que
   10.000 campos planos cuestan 10 ms por escaneo y hoy no los limita nadie. El tope de
   profundidad se conserva —en 64— solo como red contra la recursión, no como control de coste.

2. **Agotar el presupuesto deniega, no permite.** Es el cambio que cierra F-10. Hoy el recorrido
   se queda a medias y el guardrail responde `Allow`, que es afirmar «he mirado esto y está
   limpio» cuando lo cierto es «he dejado de mirar». `ARCHITECTURE.md` y el README raíz declaran
   fail-closed como principio; esto lo aplica donde no estaba aplicado.

   El riesgo es denegar entradas legítimas enormes. Se acepta, con dos mitigaciones: los umbrales
   son configurables, y el motivo dice exactamente qué límite se alcanzó, así que un operador que
   lo vea sabe qué subir en vez de tener que investigarlo.

3. **El tope de profundidad sube a 64 en vez de desaparecer.** Un recorrido recursivo sin tope
   aguantó 20.000 niveles en la medición, así que 64 es holgadísimo — pero el margen de pila
   depende de la JVM, del hilo y de si el recorrido corre en un hilo virtual, y una entrada hostil
   no debería poder tocar esa variable. 64 mantiene la red sin volver a ser un límite tras el que
   uno se pueda esconder, porque agotarlo ya no permite: deniega.

4. **`anomaly-detector` no se toca, aunque tenga el mismo `MAX_DEPTH`.** Su recorrido no busca
   nada: canonicaliza los argumentos para calcular una huella. Truncar hace que dos llamadas que
   solo difieren por debajo del nivel 8 produzcan la **misma** huella, o sea que se cuenten como
   repeticiones — lo que aumenta la detección, no la evade. Cambiarlo sería scope creep, y en la
   dirección equivocada.

5. **El hallazgo concreto gana al genérico.** Si en lo recorrido ya apareció una credencial, el
   agente recibe «credential detected», no «arguments too large». Un motivo específico le dice qué
   ha hecho mal; el genérico le sugiere que reintente con otra forma, que es justo lo que no
   queremos enseñarle.

6. **Los dos módulos implementan lo mismo por separado.** Duplicar `ScanBudget` en dos sitios
   incomoda, pero §5 prohíbe que un guardrail dependa de otro y llevarlo a `guardrails-core`
   significaría una sexta extensión del SPI para dos records de tres líneas que nadie más va a
   usar. Si un tercer módulo llega a necesitar lo mismo, entonces sí toca subirlo a core — con su
   spec, como las cinco anteriores.

7. **F-11 se documenta, no se sube.** El techo de 64 KB del decodificador Base64 es la misma clase
   de frontera, pero subirlo solo la mueve: siempre habrá un tamaño justo por encima. Cerrarlo de
   verdad exige decodificar en streaming y buscar sobre la marcha, que es un rediseño
   desproporcionado para un vector que exige preparación deliberada. Va a la sección de límites
   del README, junto al secreto partido.

---

**Listo para `domain-builder`: implementar `docs/specs/guardrails-scan-budget-spec.md`.**
