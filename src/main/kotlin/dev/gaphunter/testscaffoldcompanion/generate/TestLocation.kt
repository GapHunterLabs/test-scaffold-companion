package dev.gaphunter.testscaffoldcompanion.generate

/**
 * Where the generated test file goes, as a path -- pure, no VFS access.
 *
 * Before 0.1.3 the test went to the mirrored `src/test/...` directory only
 * if that exact package directory already existed, and otherwise next to
 * the class under `src/main` -- in production code, where test-scoped
 * dependencies like JUnit aren't on the classpath, so the test didn't
 * compile. With a module that has a test source root, the package
 * directory is now created under it (found 2026-10-01).
 */
object TestLocation {

    /**
     * @param sourceDirPath the directory of the class under test.
     * @param sourceRootPath the source root that directory belongs to, if known.
     * @param testRootPaths the module's test source roots.
     * @param packageName the class's package ("" or null for the default package).
     * @return the directory to write to (possibly not existing yet), or null when the module has no test source root.
     */
    fun targetPath(
        sourceDirPath: String,
        sourceRootPath: String?,
        testRootPaths: List<String>,
        packageName: String?,
        exists: (String) -> Boolean,
    ): String? {
        val mirrored = sourceDirPath.replace("/src/main/", "/src/test/")
        if (mirrored != sourceDirPath && exists(mirrored)) return mirrored
        if (testRootPaths.isEmpty()) return null
        val mirroredRoot = sourceRootPath?.replace("/src/main/", "/src/test/")
        val root = testRootPaths.firstOrNull { it == mirroredRoot } ?: testRootPaths.first()
        val packagePath = packageName.orEmpty().replace('.', '/')
        return if (packagePath.isEmpty()) root else "$root/$packagePath"
    }
}
