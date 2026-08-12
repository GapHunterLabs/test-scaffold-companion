# Test Scaffold Companion — Plan de desarrollo (v1)

Escrito 2026-08-11, antes de escribir código. Ver `CONSTITUTION.md` §1
(segunda excepción documentada) para la decisión de negocio: sin ancla
de mercado (los 4 competidores verificados — Squaretest, TestMe, AI
Test Case Generator, UnitTestBot — son FREE), construcción autorizada
igual, como apuesta consciente, mismo tratamiento que Refactor
Simulator.

## 0. Qué significa "ir un paso más adelante" acá — el diferencial real

Auditoría de reviews reales (`api/plugins/<id>/comments`, 2026-08-11)
contra los 4 competidores identificó un patrón repetido, no un caso
aislado:

- Squaretest: *"generating a allot of useless, non working tests"*
- TestMe: *"No tests generated. Tests generated didn't pass at all"*
- TestMe: errores de plataforma sin resolver (`getComponent` deprecado
  — `KeymapManager`), `NullPointerException` en parsing de templates
- JUnitGenerator V2.0: abandono documentado desde 2009-2010, garbled
  text detectado recién en 2025 (nadie lo mira)

**El defecto #1 de la categoría entera es el mismo defecto #1 que
motivó `CONSTITUTION.md` §6** ("cómputo pesado fuera del EDT" existe
por el mismo tipo de problema en otra categoría): generan texto que
*parece* código pero no compila, o rompe con casos reales (frameworks
modernos, tipos genéricos, herencia). Ninguno de los 4 competidores
verifica el output antes de escribirlo a disco.

**El diferencial de este plugin no es "generar más tests" — es
generar CERO tests rotos.** Concretamente: todo skeleton generado se
valida contra un `PsiFile` en memoria (nunca tocando el archivo real)
ANTES de escribirse a disco. Si no compila o el motor de tipos no
puede resolver algo con confianza, el plugin degrada con gracia
(genera un TODO explícito con el motivo, nunca una aserción
inventada/adivinada) en vez de escribir un test que falla en silencio
— mismo espíritu que `xsd-companion`'s "unresolved locations flagged
inline en vez de crashear" (`INTELLIJ_PLATFORM_KNOWLEDGE.md`, patrón
ya citado en la sección de BPMN como el estándar del catálogo).

## 1. Cómo esto reduce falsos positivos — mapeo directo a los mapas ya existentes

Un "falso positivo" acá es un test generado que parece correcto pero
no lo es: no compila, no corre, o corre pero no prueba nada real
(assert siempre-verdadero). Tres mecanismos, cada uno anclado en algo
que el catálogo ya probó en otro plugin:

### 1.1 Validación en memoria antes de escribir a disco (motor central)

Reusa **verbatim** el patrón de `refactor-simulator`
(`INTELLIJ_PLATFORM_KNOWLEDGE.md`, sección "Interactive Refactoring
Simulator — Investigación de Plataforma", subsección B):

```
PsiFileFactory.getInstance(project)
    .createFileFromText(name, language, generatedTestSourceText)
```

El archivo generado nunca se conecta a un `PsiDirectory` real hasta
que pasa la validación. Validación = 2 chequeos baratos, ninguno
requiere compilar de verdad:
1. `PsiErrorElement` walk sobre el `PsiFile` en memoria — cualquier
   nodo de error (sintaxis inválida) bloquea la escritura a disco.
   Mismo tipo de chequeo que ya usa `mermaid-companion` para su propio
   lexer (`INTELLIJ_PLATFORM_KNOWLEDGE.md` sección 1, "Real syntax
   validation").
2. Resolución de referencias: cada símbolo que el skeleton generado
   usa (nombre de clase bajo test, tipos de parámetros del framework
   de test — `@Test`, `assertEquals`, el mock que se genere) debe
   resolver vía `PsiReference.resolve()` contra el classpath real del
   proyecto — el mismo mecanismo de "resolución real, no adivinada"
   que usan `xsd-companion`/`json-schema-companion`/`openapi-companion`
   para `$ref`/`schemaLocation`. Si algo no resuelve (import
   faltante, framework de test no detectado en el classpath), el
   plugin no genera ese test — reporta por qué, no genera un import
   roto.

### 1.2 Inferencia de tipos vía la misma vía "estándar y estable" ya elegida en el catálogo

`CONSTITUTION.md` §6 ya documenta la lección real (caso
`PasswordSafe.getAsync()` vs. `get()` síncrono, y el caso reciente de
`ActionUtil.performAction` vs. `invokeAction` en refactor-simulator):
cuando la plataforma ofrece un camino nuevo/async y uno viejo/estable,
preferir el viejo. Aplicado acá: para inferir qué aserción generar
por defecto (ej. `assertNotNull` vs. un placeholder), usar
`PsiType`/`PsiMethod.getReturnType()` — API PSI plana y estable, NUNCA
la Analysis API/K1-K2 (el mismo split ya auditado y confirmado limpio
en `api-security-companion`, ver `SDK_GOTCHAS.md` §13 — este plugin
reutiliza el mismo patrón K1/K2-neutral que
`KotlinTypeAnnotationResolver.kt` ya demostró, no re-decide esa
elección desde cero).

### 1.3 Degradación explícita, nunca aserción inventada

Regla dura de diseño (equivalente a la de refactor-simulator "nunca
tocar el archivo real sin confirmación explícita"): si el motor de
tipos no puede resolver con confianza qué aserción generar (tipo
genérico complejo, resultado de una llamada a red/IO detectada en el
cuerpo del método), el skeleton generado marca ese método con un
comentario `// TODO(test-scaffold): no se pudo inferir un assert
seguro — revisar manualmente` en vez de escribir
`assertEquals(null, null)` o cualquier aserción trivialmente
verdadera. Esto es precisamente lo que Squaretest/TestMe NO hacen
(de ahí "tests generados que no pasan" en sus propias reviews) — la
métrica de éxito de este plugin no es "% de métodos con aserción
generada", es "% de tests generados que compilan y pasan en verde
la primera vez", con el resto marcado honestamente como manual.

## 2. Alcance v1 — Knowledge Layer primero, luego capas de dependencia real (`CONSTITUCIÓN` §5.3)

### Capa 0 — Knowledge Layer (antes de cualquier código)

- [ ] Confirmar en `SDK_GOTCHAS.md`/`INTELLIJ_PLATFORM_KNOWLEDGE.md`
      si ya existe una firma de API confirmada por `javap` para
      "listar métodos públicos de una `PsiClass`/`KtClass`" en el
      rango de versiones objetivo (243-262, mismo rango que
      `refactor-simulator`) — si no, correr `javap` real antes de
      codear, no asumir la firma.
- [ ] Confirmar mecanismo de detección del framework de test ya
      presente en el proyecto (JUnit4 vs JUnit5 vs TestNG — mismo
      patrón "detección por contenido real, no adivinar" que
      `nginx-companion`/`gitlab-ci-companion` ya aplican, acá vía
      `bundledPlugin`/dependencias del módulo Gradle/Maven real, no
      un dropdown de configuración manual).
- [ ] Registrar en `INTELLIJ_PLATFORM_KNOWLEDGE.md` cualquier hallazgo
      de esta capa apenas se confirme — no esperar al cierre de sesión
      (regla dura §5.2).

### Capa 1 — Skeleton puro — ✅ COMPLETA Y VERIFICADA EN VIVO (2026-08-12)

- [x] Acción de editor: click derecho sobre una clase Java/Kotlin →
      "Generate Test Skeleton" (context menu de editor + Project view).
- [x] Un método de test vacío por cada método público de la clase,
      nombre según convención (`test<NombreMétodo>`), framework
      detectado real ya importado correctamente.
- [x] Motor de validación en memoria (sección 1.1) integrado desde el
      primer commit.
- [x] Tests + `verifyPlugin` 6/6 — y además **confirmado funcionando
      de punta a punta en un `runIde` real** contra un proyecto demo
      real, no solo en tests unitarios.
- **4 bugs reales encontrados y arreglados, ninguno detectado por
  `test`/`buildPlugin`/`verifyPlugin` — solo por `runIde` en vivo**
  (detalle completo en `INTELLIJ_PLATFORM_KNOWLEDGE.md`, sección "Test
  Scaffold Companion"):
  1. Acceso a PSI/índices desde hilo pooled sin `runReadAction`.
  2. `GlobalSearchScope.moduleWithLibrariesScope`/`moduleWithDependenciesAndLibrariesScope(includeTests=true)`
     nunca ven dependencias `testImplementation` de un módulo hermano
     `.test` en un proyecto Gradle con separación por source set —
     hubo que buscar el módulo `.test` explícitamente.
  3. Error de string propio: el nombre del módulo hermano se
     construía mal (`<nombre>.main.test` en vez de `<base>.test`) —
     ahora con test de regresión unitario directo
     (`TestFrameworkDetectorTest`).
  4. `PsiManager.dropPsiCaches()` exige el EDT específicamente, no
     solo un read action — la llamada era innecesaria y se sacó.

### Capa 2 — Inferencia de aserciones + mocks básicos — ✅ COMPLETA (2026-08-11)

- [x] Aserción por defecto vía `PsiType`/`getReturnType()` (sección
      1.2) para métodos con tipo de retorno simple (String, colección
      de tipo conocido — primitivos deliberadamente sin aserción, ver
      `AssertionInferrer.kt`: NotNull en un primitivo sería
      trivialmente verdadero). `MockFieldPlanner.kt` genera campos de
      mock (Mockito) solo si Mockito ya resuelve en el classpath real
      del módulo del usuario — nunca agregado como dependencia del
      plugin mismo.
- [x] Degradación explícita (sección 1.3) para todo lo que no
      resuelva con confianza — void, primitivos, tipos desconocidos
      siguen generando el TODO honesto de Capa 1, nunca una aserción
      inventada.
- [x] `./gradlew test` 16/16 verde, `buildPlugin` limpio,
      `verifyPlugin` 6/6 IDEs Compatible.
- Bug real encontrado y corregido en el camino:
  `PsiType.getCanonicalText()` no devuelve el FQN completo en un
  fixture de test liviano sin JDK indexado — documentado en
  `INTELLIJ_PLATFORM_KNOWLEDGE.md`, sección "Test Scaffold Companion".

### Capa 3 — Documentación + verificación final

- [x] README completo (competidores citados, "Why built this way",
      "Free, forever" — sin plan de monetización todavía, deferido a
      uso real, mismo criterio que Fase 4 de refactor-simulator).
- [x] `CHANGELOG.md` completo.
- [x] Repo público en GitHub (`GapHunterLabs/test-scaffold-companion`).
- [x] Cierre de mapas: los 4 bugs reales de plataforma ya están en
      `INTELLIJ_PLATFORM_KNOWLEDGE.md`, sección "Test Scaffold
      Companion", cada uno en el momento en que se encontró.
- [x] Al menos 2 screenshots reales (3 guardadas), Full Screen, con
      datos de demo realistas — CONSTITUTION.md §7 punto 5.
- [x] `marketplace-listing-template.md` — bloque completo de 6
      subsecciones agregado 2026-08-12 (corrección: la regla real es
      agregarlo apenas el plugin llega a "shipped" — pushed + tests/
      verifyPlugin verde + screenshots —, no recién cuando ya está
      listado, error de lectura propio corregido en memoria).
- [x] Listado en Marketplace: subido con toda la información
      (listing completo, screenshots), **enviado a moderación**
      (status real: "Under review" / "Submitted"), 2026-08-12.
      Primer upload hecho manualmente por el usuario, como corresponde
      (no hay vía Gradle/`publishPlugin` para la primera subida de un
      plugin nuevo).

## 3. Explícitamente fuera de alcance v1

- Extract/generación de tests para métodos privados o protegidos
  (mismo criterio de "scope cut documentado, no silenciosamente
  omitido" que refactor-simulator usa para Extract Function).
- Generación de datos de prueba parametrizados (`@ParameterizedTest`)
  — v2 candidato si hay señal de uso real.
- Cualquier llamada a un servicio de IA externo (a diferencia de "AI
  Test Case Generator", que envía datos del método a la API de Groq)
  — 100% local, cero red, mismo estándar de privacidad que todo el
  catálogo (`api-security-companion`/`asyncapi-companion` como
  ejemplo ya citado en este mismo documento).

## Fuentes de este plan

- `CONSTITUTION.md` §1 (segunda excepción documentada, agregada
  2026-08-11), §5.2, §5.3, §6.
- `INTELLIJ_PLATFORM_KNOWLEDGE.md`, sección "Interactive Refactoring
  Simulator — Investigación de Plataforma" (subsección B, PSI en
  memoria).
- `SDK_GOTCHAS.md` §13 (patrón K1/K2-neutral ya confirmado en
  `api-security-companion`).
- Auditoría de reviews reales 2026-08-11 vía
  `plugins.jetbrains.com/api/plugins/<id>/comments` contra Squaretest
  (10405), TestMe (9471), AI Test Case Generator (31249), UnitTestBot
  (19445), JUnitGenerator V2.0 (3064) — citas textuales en la
  conversación que originó este plan, no repetidas acá en detalle
  para evitar duplicar la fuente primaria.
