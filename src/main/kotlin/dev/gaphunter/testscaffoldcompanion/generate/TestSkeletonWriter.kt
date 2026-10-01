package dev.gaphunter.testscaffoldcompanion.generate

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import dev.gaphunter.testscaffoldcompanion.detect.TestFramework

/**
 * Renders the actual test-class source text -- pure string generation,
 * no PSI mutation, no I/O. Kept separate from [InMemoryValidator] on
 * purpose: this class answers "what should the text look like", the
 * validator answers "is this text actually safe to write to disk" --
 * same separation of concerns already proven in this catalog between
 * FormatConverterCompanion's converters (pure text transform) and its
 * caller (decides whether/where to write).
 *
 * Layer 2 (see DEVELOPMENT_PLAN.md section 1.2/1.3): every method gets
 * a real [AssertionInferrer] call, not a guess -- if it returns null
 * (return type too complex/unknown to default safely), the generated
 * method body is the same honest TODO marker Layer 1 always used.
 * There is no in-between "best effort" assertion: either the plugin is
 * confident enough to assert something real, or it says so and leaves
 * it to the user, per the plugin's whole reason for existing (section
 * 0 of the plan).
 *
 * Deliberately does NOT attempt constructor-argument inference or
 * dependency mocking for non-static methods (that's the "mocks" half
 * of Layer 2 in DEVELOPMENT_PLAN.md, tracked separately in
 * [MockFieldPlanner]) -- a non-static method call is rendered against
 * an `instance` variable the user must assign themselves, with an
 * honest TODO, rather than guessing at a no-args constructor that may
 * not exist or may have real side effects.
 */
object TestSkeletonWriter {

    fun render(psiClass: PsiClass, methods: List<PsiMethod>, framework: TestFramework, packageName: String?): String {
        val className = psiClass.name ?: "UnknownClass"
        val testClassName = "${className}Test"
        val mockPlan = MockFieldPlanner.plan(psiClass)
        // The class as the test (same package) can refer to it: Outer.Inner for a nested class.
        val typeRef = psiClass.qualifiedName?.let { fq ->
            if (!packageName.isNullOrEmpty() && fq.startsWith("$packageName.")) fq.removePrefix("$packageName.") else fq
        } ?: className
        // Non-static calls go through an `instance` the test DECLARES (lateinit, assigned by the user): before 0.1.3
        // they referenced an `instance` that was never declared, so the generated file didn't compile (found
        // 2026-10-01). A generic class gets no declaration (a raw type isn't valid Kotlin) and no executable calls.
        val needsInstance = methods.any { !it.hasModifierProperty(PsiModifier.STATIC) }
        val instanceDeclared = needsInstance && !psiClass.hasTypeParameters()
        val renderedMethods = methods.map { method -> renderMethod(method, typeRef, instanceDeclared) }
        val needsAssertNotNullImport = renderedMethods.any { it.usesAssertNotNull }

        val header = buildString {
            if (!packageName.isNullOrEmpty()) {
                appendLine("package $packageName")
                appendLine()
            }
            appendLine("import ${framework.importFqn}")
            if (needsAssertNotNullImport) {
                appendLine("import ${framework.assertNotNullFqn}")
            }
            appendLine()
            appendLine("class $testClassName {")
            appendLine()
            mockPlan.fieldDeclarations.forEach { appendLine("    $it") }
            if (mockPlan.fieldDeclarations.isNotEmpty()) appendLine()
            if (instanceDeclared) {
                val from = if (mockPlan.fieldDeclarations.isNotEmpty()) " (the mocks above are its constructor's dependencies)" else ""
                appendLine("    // TODO(test-scaffold): assign a real or mocked $typeRef to `instance` before the tests run$from")
                appendLine("    private lateinit var instance: $typeRef")
                appendLine()
            } else if (needsInstance) {
                appendLine("    // TODO(test-scaffold): $typeRef is generic -- create an instance with concrete type arguments")
                appendLine()
            }
        }

        val body = renderedMethods.joinToString(separator = "\n") { it.text }
        val footer = "\n}\n"
        return header + body + footer
    }

    private data class RenderedMethod(val text: String, val usesAssertNotNull: Boolean)

    private fun renderMethod(method: PsiMethod, typeRef: String, instanceDeclared: Boolean): RenderedMethod {
        val testName = "test${method.name.replaceFirstChar { it.uppercase() }}"
        val args = method.parameterList.parameters.map { defaultValueFor(it.type) }
        val callArgs = args.joinToString(", ") { it ?: "null" }
        val isStatic = method.hasModifierProperty(PsiModifier.STATIC)
        val receiver = if (isStatic) typeRef else "instance"
        val call = "$receiver.${method.name}($callArgs)"
        val isVoid = method.returnType == PsiTypes.voidType()
        val inferred = AssertionInferrer.inferAssertion(method)
        // Only code that compiles is emitted: a call needs a declared receiver and a typed placeholder for every
        // argument (a `null` for an object parameter is left to the user in a TODO, never executed).
        val executable = (isStatic || instanceDeclared) && args.all { it != null }
        var usesAssertNotNull = false

        val text = buildString {
            appendLine("    @Test")
            appendLine("    fun $testName() {")
            when {
                inferred == InferredAssertion.NotNull && executable -> {
                    appendLine("        val result = $call")
                    appendLine("        assertNotNull(result)")
                    usesAssertNotNull = true
                }
                isVoid -> {
                    appendLine("        // TODO(test-scaffold): call $call and assert its observable side effect")
                }
                inferred == InferredAssertion.NotNull -> {
                    appendLine("        // TODO(test-scaffold): call $call with real arguments and assert the result is not null")
                }
                else -> {
                    appendLine("        // TODO(test-scaffold): no safe default assertion for ${method.name}()'s return type -- fill in manually")
                }
            }
            appendLine("    }")
        }
        return RenderedMethod(text, usesAssertNotNull)
    }

    /**
     * A placeholder argument that compiles in Kotlin for this Java/Kotlin parameter type, or null when there is no
     * safe one (any object type other than String). Typed literals: Kotlin doesn't widen an integer literal to
     * Double/Float, so a `double` parameter needs `0.0` -- before 0.1.3 every numeric placeholder was `0`, which
     * doesn't compile for those. Deliberately minimal, never meaningful test data (honest placeholders, never
     * invented "real-looking" values that could be mistaken for actual test intent).
     */
    internal fun defaultValueFor(type: PsiType): String? = when (type) {
        PsiTypes.booleanType() -> "false"
        PsiTypes.doubleType() -> "0.0"
        PsiTypes.floatType() -> "0.0f"
        PsiTypes.longType() -> "0L"
        PsiTypes.charType() -> "' '"
        is PsiPrimitiveType -> "0"
        // "String" alone: an unresolved String (no JDK configured) still is the java.lang one in practice
        else -> if (type.canonicalText == "java.lang.String" || type.canonicalText == "String") "\"\"" else null
    }
}
