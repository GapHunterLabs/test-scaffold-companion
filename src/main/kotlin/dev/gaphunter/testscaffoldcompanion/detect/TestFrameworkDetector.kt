package dev.gaphunter.testscaffoldcompanion.detect

import com.intellij.openapi.module.Module
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
     * class actually resolves against [module]'s own dependency scope,
     * or null if none do -- callers must treat null as "cannot generate
     * safely here", never fall back to guessing one. A module with zero
     * detected test framework is exactly the case Squaretest/TestMe get
     * wrong silently (see DEVELOPMENT_PLAN.md section 0): generating
     * JUnit imports into a TestNG-only module produces a skeleton that
     * never compiles.
     */
    fun detect(module: Module): TestFramework? {
        val facade = JavaPsiFacade.getInstance(module.project)
        val scope = GlobalSearchScope.moduleWithLibrariesScope(module)
        return TestFramework.entries.firstOrNull { framework ->
            facade.findClass(framework.markerAnnotationFqn, scope) != null
        }
    }
}
