# KICKOFF_PROMPT — pegar esto en Claude Code

> Antes de pegarlo: coloca `../ARCHITECTURE.md` en la raíz del repo, y la carpeta `skills/` dentro
> de `.claude/skills/` del repo (o de `~/.claude/skills/` si quieres que estén disponibles en
> cualquier proyecto). Ver instrucciones abajo del prompt.

---

Vamos a construir desde cero el proyecto `mcp-agent-guardrails-spring-boot-starter`: un starter
de Spring Boot con guardrails de seguridad/gobernanza para agentes MCP en Java. El repo está
vacío salvo por `../ARCHITECTURE.md` en la raíz, que es la ley del proyecto y debes respetar sin
excepción.

Tienes disponibles 5 skills en `.claude/skills/`: `spec-architect`, `domain-builder`,
`adapter-builder`, `test-engineer`, `code-reviewer`. Cada módulo del proyecto se construye
pasando obligatoriamente por esos 5 agentes **en ese orden exacto**, uno por uno, sin saltarte
ninguno y sin paralelizar agentes distintos sobre el mismo módulo.

Reglas de ejecución:

1. Sigue el orden de módulos definido en `../ARCHITECTURE.md` sección 6:
   `guardrails-core → guardrails-audit → guardrails-authz → guardrails-injection-guard →
   guardrails-ratelimit → spring-boot-starter`.
2. Para cada módulo: invoca `spec-architect` → espera a que termine → invoca `domain-builder`
   → invoca `adapter-builder` → invoca `test-engineer` → invoca `code-reviewer`.
3. Si `code-reviewer` rechaza el módulo, vuelve a invocar únicamente el agente responsable que
   él indique, corrige, y vuelve a pasar por `code-reviewer` antes de seguir. No avances al
   siguiente módulo con un rechazo pendiente.
4. No inventes versiones de dependencias de memoria: verifica la última GA real de Spring Boot,
   Spring AI, el MCP Java SDK y las librerías de testing antes de fijarlas en cualquier
   `../pom.xml`. Si no puedes verificarlo en el momento, dilo explícitamente en vez de adivinar.
5. Empieza ahora mismo por el primer módulo (`guardrails-core`) invocando `spec-architect`, y
   después de cada agente muéstrame un resumen corto de lo que hizo antes de continuar con el
   siguiente — quiero poder frenarte entre agentes si algo no me cuadra.
6. No generes el módulo `spring-boot-starter` (el ensamblaje final) hasta que los cuatro
   guardrails tengan su `docs/specs/<modulo>-DONE.md` aprobado por `code-reviewer`.

Confírmame que entendiste el flujo y arranca con la spec de `guardrails-core`.

---

## Cómo instalar las skills en tu repo (antes de pegar el prompt de arriba)

```bash
# Desde la raíz de tu repo (vacío o recién creado con `git init`)
mkdir -p .claude/skills
cp -r ruta/al/kit/skills/* .claude/skills/
cp ruta/al/kit/ARCHITECTURE.md .
git add .claude ARCHITECTURE.md
git commit -m "chore: agent kit (skills + architecture) para mcp-agent-guardrails-spring-boot-starter"
```

Abre `claude` (Claude Code) en esa carpeta y pega el prompt de arriba tal cual.
