package dev.gaphunter.testscaffoldcompanion.generate

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.search.GlobalSearchScope

/**
 * Layer 2, "mocks" half (DEVELOPMENT_PLAN.md section Capa 2, second
 * bullet): if the class under test has a primary constructor with
 * object-typed parameters AND Mockito's own `mock()` entry point
 * resolves against the module's real classpath, generate one `mock(...)`
 * field declaration per constructor dependency. If Mockito isn't on the
 * classpath, this returns an empty plan -- never adds Mockito as a
 * dependency of the plugin itself, per the plan's explicit constraint.
 *
 * Deliberately does NOT wire the mocked fields into an actual
 * `new ClassName(mock1, mock2)` instance construction -- that's real
 * test-authoring intent (which constructor overload, whether to spy
 * instead of mock, whether some dependencies should be real) this
 * plugin has no business deciding on the user's behalf. The generated
 * `instance` variable in [TestSkeletonWriter] still carries its own
 * honest TODO even when mock fields exist, same "degrade explicitly,
 * never guess" rule as everywhere else in Layer 1/2 (section 1.3).
 */
object MockFieldPlanner {

    private const val MOCKITO_MOCK_FQN = "org.mockito.Mockito"

    data class Plan(val fieldDeclarations: List<String>)

    fun plan(psiClass: PsiClass): Plan {
        val module = com.intellij.openapi.module.ModuleUtilCore.findModuleForPsiElement(psiClass)
            ?: return Plan(emptyList())
        if (!mockitoAvailable(module)) return Plan(emptyList())

        val constructor = psiClass.constructors.maxByOrNull { it.parameterList.parametersCount } ?: return Plan(emptyList())
        val objectParams = constructor.parameterList.parameters.filter { it.type is PsiClassType }
        if (objectParams.isEmpty()) return Plan(emptyList())

        val declarations = objectParams.mapNotNull { param ->
            val typeName = (param.type as? PsiClassType)?.className ?: return@mapNotNull null
            "private val ${param.name} = org.mockito.Mockito.mock($typeName::class.java)"
        }
        return Plan(declarations)
    }

    /**
     * Wrapped in `runReadAction` for the same reason as
     * [dev.gaphunter.testscaffoldcompanion.detect.TestFrameworkDetector.detect]
     * -- [JavaPsiFacade.findClass] requires a read action even off the
     * EDT. Today's only caller ([TestSkeletonWriter.render]) already
     * runs inside a wider read action of its own, so this is reentrant
     * defense in depth, not the sole guard -- kept anyway so this
     * function stays safe to call in isolation later.
     *
     * Uses `moduleWithDependenciesAndLibrariesScope(module, includeTests
     * = true)`, same fix and same reason as
     * [dev.gaphunter.testscaffoldcompanion.detect.TestFrameworkDetector.detect]:
     * Mockito is conventionally declared `testImplementation`, only ever
     * on the module's *test* classpath -- `moduleWithLibrariesScope`
     * (no test dependencies) would never find it in a real project.
     */
    private fun mockitoAvailable(module: com.intellij.openapi.module.Module): Boolean =
        ApplicationManager.getApplication().runReadAction<Boolean> {
            val facade = JavaPsiFacade.getInstance(module.project)
            val scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, /* includeTests = */ true)
            facade.findClass(MOCKITO_MOCK_FQN, scope) != null
        }
}
