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
        val renderedMethods = methods.map { method -> renderMethod(method, className, mockPlan) }
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
        }
        val body = renderedMethods.joinToString(separator = "\n") { it.text }
        val footer = "\n}\n"
        return header + body + footer
    }

    private data class RenderedMethod(val text: String, val usesAssertNotNull: Boolean)

    private fun renderMethod(method: PsiMethod, className: String, mockPlan: MockFieldPlanner.Plan): RenderedMethod {
        val testName = "test${method.name.replaceFirstChar { it.uppercase() }}"
        val callArgs = method.parameterList.parameters.joinToString(", ") { defaultValueFor(it.type) }
        val isStatic = method.hasModifierProperty(PsiModifier.STATIC)
        val receiver = if (isStatic) className else "instance"
        val call = "$receiver.${method.name}($callArgs)"
        val isVoid = method.returnType == PsiTypes.voidType()
        val inferred = AssertionInferrer.inferAssertion(method)
        var usesAssertNotNull = false

        val text = buildString {
            appendLine("    @Test")
            appendLine("    fun $testName() {")
            if (!isStatic && mockPlan.fieldDeclarations.isEmpty()) {
                appendLine("        // TODO(test-scaffold): assign a real or mocked $className to `instance` before calling $call")
            }
            when {
                inferred == InferredAssertion.NotNull -> {
                    appendLine("        val result = $call")
                    appendLine("        assertNotNull(result)")
                    usesAssertNotNull = true
                }
                isVoid -> {
                    appendLine("        // TODO(test-scaffold): call $call and assert its observable side effect")
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
     * A syntactically valid placeholder argument per parameter type --
     * needed so the generated call itself compiles (Layer 1's own
     * in-memory validation would otherwise flag every non-nullary method
     * as a false positive syntax error). Deliberately minimal: 0 for
     * numeric primitives, false for boolean, an empty string literal for
     * String, null for anything else nullable. Not an attempt at
     * meaningful test data -- that's a user decision this plugin never
     * makes for them (section 0 of the plan: honest placeholders, never
     * invented "real-looking" values that could be mistaken for actual
     * test intent).
     */
    private fun defaultValueFor(type: PsiType): String = when {
        type == PsiTypes.booleanType() -> "false"
        type is PsiPrimitiveType -> "0"
        type.canonicalText == "java.lang.String" -> "\"\""
        else -> "null"
    }
}
