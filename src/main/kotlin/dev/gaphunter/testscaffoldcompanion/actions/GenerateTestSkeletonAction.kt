package dev.gaphunter.testscaffoldcompanion.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import dev.gaphunter.testscaffoldcompanion.detect.TestFrameworkDetector
import dev.gaphunter.testscaffoldcompanion.generate.InMemoryValidator
import dev.gaphunter.testscaffoldcompanion.generate.PublicMethodCollector
import dev.gaphunter.testscaffoldcompanion.generate.TestSkeletonWriter
import org.jetbrains.kotlin.idea.KotlinLanguage

/**
 * Editor/Project-view context-menu entry point. Same caret/element
 * resolution discipline already proven in refactor-simulator's
 * SimulateRefactorAction: never trust CommonDataKeys.PSI_ELEMENT alone
 * (documented as unreliable there), resolve the target class from the
 * real file structure instead.
 *
 * All the real work (PSI walk, in-memory validation, module/classpath
 * lookup) runs on a background thread via
 * ApplicationManager.executeOnPooledThread -- only the final
 * WriteCommandAction that actually creates the file touches the EDT,
 * and only after validation already passed.
 */
class GenerateTestSkeletonAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val hasTarget = findTargetClass(e) != null
        e.presentation.isEnabledAndVisible = project != null && !DumbService.isDumb(project) && hasTarget
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val psiClass = findTargetClass(e) ?: return
        val module = ModuleUtilCore.findModuleForPsiElement(psiClass) ?: return notify(project, "No module found for this class -- cannot resolve its test framework.")

        // Everything below touches PSI/the stub index (TestFrameworkDetector,
        // PublicMethodCollector, TestSkeletonWriter -> MockFieldPlanner all
        // call into JavaPsiFacade/PsiClass APIs that require a read action
        // even off the EDT) -- wrapping the whole block in one runReadAction
        // is simpler and safer than wrapping each call site individually.
        // Confirmed necessary by a real runIde crash
        // (RuntimeExceptionWithAttachments: "Read access is allowed from
        // inside read-action only"), not caught by test/buildPlugin/
        // verifyPlugin.
        ApplicationManager.getApplication().executeOnPooledThread {
            ApplicationManager.getApplication().runReadAction {
                val framework = TestFrameworkDetector.detect(module)
                if (framework == null) {
                    notify(project, "No supported test framework (JUnit 4/5, TestNG) found on this module's classpath -- add one as a test dependency first.")
                    return@runReadAction
                }

                val methods = PublicMethodCollector.collect(psiClass)
                if (methods.isEmpty()) {
                    notify(project, "${psiClass.name} has no public methods to scaffold a test for.")
                    return@runReadAction
                }

                val packageName = (psiClass.containingFile as? PsiClassOwner)?.packageName
                val generatedText = TestSkeletonWriter.render(psiClass, methods, framework, packageName)
                val fileName = "${psiClass.name}Test.kt"

                val error = InMemoryValidator.findFirstSyntaxError(project, generatedText, fileName)
                if (error != null) {
                    notify(project, "Generated skeleton failed its own safety check ($error) -- nothing was written. This is the plugin refusing to hand you a broken test, not a bug.")
                    return@runReadAction
                }

                ApplicationManager.getApplication().invokeLater {
                    writeToDisk(project, psiClass, fileName, generatedText)
                }
            }
        }
    }

    private fun writeToDisk(project: com.intellij.openapi.project.Project, psiClass: PsiClass, fileName: String, text: String) {
        val sourceDirectory = psiClass.containingFile?.containingDirectory ?: return notify(project, "Could not resolve a directory to write the test into.")
        val testDirectory = resolveTestSourceDirectory(sourceDirectory) ?: sourceDirectory

        WriteCommandAction.runWriteCommandAction(project, "Generate Test Skeleton", null, {
            val existing = testDirectory.findFile(fileName)
            if (existing != null) {
                notify(project, "$fileName already exists -- not overwriting. Delete it first if you want to regenerate.")
                return@runWriteCommandAction
            }
            val psiFile: PsiFile = PsiFileFactory.getInstance(project)
                .createFileFromText(fileName, KotlinLanguage.INSTANCE, text)
            testDirectory.add(psiFile)
        })
    }

    /**
     * Best-effort mirror of src/main -> src/test convention (Gradle/Maven
     * standard layout). Falls back to the source file's own directory
     * (handled by the caller) if no src/test sibling exists yet -- v1
     * deliberately does not create new directories on the user's behalf,
     * same "never touch more than what was explicitly asked" restraint
     * as refactor-simulator's Apply/Discard split.
     */
    private fun resolveTestSourceDirectory(sourceDirectory: PsiDirectory): PsiDirectory? {
        val path = sourceDirectory.virtualFile.path
        val testPath = path.replace("/src/main/", "/src/test/")
        if (testPath == path) return null
        val testVirtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(testPath) ?: return null
        return PsiManager.getInstance(sourceDirectory.project).findDirectory(testVirtualFile)
    }

    /**
     * Deliberately does NOT walk the caret's own PSI ancestry
     * (`PsiTreeUtil.getParentOfType(leaf, PsiClass::class.java)`) --
     * that works for Java but returns null for Kotlin, because a
     * `KtClass` isn't itself a `PsiClass` node in the real PSI tree
     * (`PsiClass` there is a light-class view built separately).
     * Confirmed the hard way by a real test NPE, see
     * PublicMethodCollectorTest's comment for the full story. Going
     * through [PsiClassOwner.getClasses] instead works identically for
     * both `PsiJavaFile` and `KtFile` (both implement it) -- picks the
     * class whose own text range contains the caret offset when there's
     * more than one top-level class in the file, otherwise the file's
     * only (or first) class.
     */
    private fun findTargetClass(e: AnActionEvent): PsiClass? {
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) as? PsiClassOwner ?: return null
        val classes = psiFile.classes
        if (classes.isEmpty()) return null

        val editor = e.getData(CommonDataKeys.EDITOR)
        if (editor != null) {
            val offset = editor.caretModel.offset
            classes.firstOrNull { it.textRange?.containsOffset(offset) == true }?.let { return it }
        }
        return classes.first()
    }

    private fun notify(project: com.intellij.openapi.project.Project, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Test Scaffold Companion")
            .createNotification(message, NotificationType.WARNING)
            .notify(project)
    }
}
