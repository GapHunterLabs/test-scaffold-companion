package dev.gaphunter.testscaffoldcompanion.detect

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope

/**
 * Which JVM test framework a module actually depends on, detected by
 * resolving a real marker class against the module's own classpath --
 * never a config dropdown, never a guess from the project's build file
 * syntax. Same "detect by real content, not by assumption" discipline
 * this catalog already applies to file-type detection
 * (nginx-companion/gitlab-ci-companion) and $ref resolution
 * (xsd-companion/json-schema-companion), applied here to framework
 * choice instead of file content.
 *
 * Order matters: a module can accumulate more than one test framework
 * on its classpath over time (a JUnit4 legacy suite migrated
 * incrementally to JUnit5, for example) -- JUnit5 is checked first
 * since it's the modern default this plugin should prefer when both
 * are present, TestNG last since it's the least likely default for a
 * Kotlin/IntelliJ-ecosystem project.
 */
enum class TestFramework(val markerAnnotationFqn: String, val importFqn: String, val assertNotNullFqn: String) {
    JUNIT5("org.junit.jupiter.api.Test", "org.junit.jupiter.api.Test", "org.junit.jupiter.api.Assertions.assertNotNull"),
    JUNIT4("org.junit.Test", "org.junit.Test", "org.junit.Assert.assertNotNull"),
    TESTNG("org.testng.annotations.Test", "org.testng.annotations.Test", "org.testng.Assert.assertNotNull"),
}

object TestFrameworkDetector {

    /**
     * Returns the first [TestFramework] whose marker `@Test` annotation
     * class actually resolves against [module]'s own dependency scope
     * OR its Gradle "test" sibling module's scope, or null if neither
     * does -- callers must treat null as "cannot generate safely here",
     * never fall back to guessing one. A module with zero detected test
     * framework is exactly the case Squaretest/TestMe get wrong
     * silently (see DEVELOPMENT_PLAN.md section 0): generating JUnit
     * imports into a TestNG-only module produces a skeleton that never
     * compiles.
     *
     * **Two real bugs found here, both only by live `runIde`
     * diagnosis, neither by test/buildPlugin/verifyPlugin -- full
     * incident in INTELLIJ_PLATFORM_KNOWLEDGE.md "Test Scaffold
     * Companion" section:**
     *
     * 1. `GlobalSearchScope.moduleWithLibrariesScope(module)` excludes
     *    test-scoped dependencies entirely -- a `testImplementation`
     *    library is never visible through it, in any project.
     * 2. Switching to `moduleWithDependenciesAndLibrariesScope(module,
     *    includeTests = true)` was NOT enough either. A live diagnostic
     *    notification dumping the real `ModuleRootManager.orderEntries`
     *    of the "main" module in a real Gradle "separate module per
     *    source set" project (the modern default) showed it has NO
     *    entry pointing at its own "test" sibling module at all -- the
     *    dependency direction in Gradle's own IDE model is the
     *    opposite of what `includeTests` assumes (a module's own test
     *    sources depending on itself, not "main" depending on "test").
     *    `includeTests=true` only ever widens a scope to include a
     *    module's OWN test sources -- it does nothing when the test
     *    framework lives in a completely separate module ([module] +
     *    `.test` suffix) that [module] has no dependency edge to at
     *    all.
     *
     * **Actual fix:** explicitly look up the sibling module by Gradle's
     * own naming convention (see [testSiblingModuleName]) via
     * [ModuleManager], and also search its scope if it exists -- this is
     * the only approach confirmed to work against a real project, not a
     * scope flag that assumes a dependency edge Gradle never creates.
     *
     * Wrapped in [ApplicationManager.getApplication] `runReadAction` --
     * [JavaPsiFacade.findClass] touches the stub/file-based index, which
     * requires a read action even off the EDT. Confirmed the hard way
     * by a real `runIde` crash (`RuntimeExceptionWithAttachments: Read
     * access is allowed from inside read-action only`) the first time
     * this was called from [ApplicationManager.executeOnPooledThread]
     * without one -- neither `test`/`buildPlugin`/`verifyPlugin` caught
     * it, only a live invocation did.
     */
    fun detect(module: Module): TestFramework? =
        ApplicationManager.getApplication().runReadAction<TestFramework?> {
            val facade = JavaPsiFacade.getInstance(module.project)
            val scopes = candidateScopes(module)
            TestFramework.entries.firstOrNull { framework ->
                scopes.any { scope -> facade.findClass(framework.markerAnnotationFqn, scope) != null }
            }
        }

    /**
     * [module]'s own scope, plus its Gradle "test" sibling module's
     * scope when one exists. A single-module project (no source-set
     * split) has no such sibling -- [module]'s own scope, which already
     * includes its test dependencies directly, is enough there.
     *
     * **Real bug in an earlier version of this fix, found by a second
     * live `runIde` failure after the first "fix" still didn't work:**
     * the sibling name is derived from the module's OWN base name
     * (`acme-order-service` for both `acme-order-service.main` and
     * `acme-order-service.test`), never by suffixing [module]'s own
     * name -- `"${module.name}.test"` against a module already named
     * `acme-order-service.main` built the string
     * `"acme-order-service.main.test"`, which does not exist and so
     * silently found nothing (`findModuleByName` returns null on a
     * miss, no error). Confirmed the real names via the sandbox's own
     * `idea.log` (`grep "Module 'acme-order-service"` showed exactly
     * `acme-order-service`, `acme-order-service.main`,
     * `acme-order-service.test` -- never anything with two suffixes).
     * Fix: strip a trailing `.main` (or `.test`) before appending
     * `.test`, so the sibling lookup always targets the shared base
     * name, regardless of which of the pair [module] itself is.
     *
     * Not private -- [dev.gaphunter.testscaffoldcompanion.generate.MockFieldPlanner]
     * needs the exact same candidate-scopes logic for its own Mockito
     * classpath check, same real bug, same fix.
     */
    fun candidateScopes(module: Module): List<GlobalSearchScope> {
        val ownScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, /* includeTests = */ true)
        val testSibling = ModuleManager.getInstance(module.project).findModuleByName(testSiblingModuleName(module.name))
        val siblingScope = testSibling?.let { GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(it, /* includeTests = */ true) }
        return listOfNotNull(ownScope, siblingScope)
    }

    /**
     * Pure string derivation, kept separate from [candidateScopes] so it
     * has direct unit test coverage without needing a real multi-module
     * project fixture -- this exact function had the real off-by-a-suffix
     * bug documented above (`"${module.name}.test"` instead of stripping
     * `.main` first), caught only by a live `runIde` reproduction because
     * no test exercised it in isolation before this fix.
     */
    fun testSiblingModuleName(moduleName: String): String =
        "${moduleName.removeSuffix(".main").removeSuffix(".test")}.test"
}
