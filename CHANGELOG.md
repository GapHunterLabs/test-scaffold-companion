<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Test Scaffold Companion Changelog

## [Unreleased]

### Changed

- `PRIVACY.md` describes the values the plugin keeps in the IDE's local
  settings.

## [0.1.3]

### Fixed

- The generated test didn't compile: calls used an `instance` that was
  never declared. The test now declares
  `private lateinit var instance: YourClass` with a TODO to assign it
  (a generic class gets a TODO instead).
- A `double`, `float`, `long` or `char` argument got the placeholder
  `0`, which Kotlin rejects; placeholders are now typed (`0.0`, `0.0f`,
  `0L`, `' '`). A call that would need `null` for an object argument is
  left as a TODO instead of being executed.
- With a test source root but no package directory under it yet, the
  test was written next to the class in `src/main`, where test
  dependencies aren't on the classpath. The package directories are now
  created under the module's test source root.
- The generated file is opened in the editor.
- The listing said each unresolved reference is replaced by a TODO; the
  in-memory check is a syntax check, and the listing now says so.

## [0.1.2]

### Fixed

- Review/star CTA now links to this plugin's own Marketplace
  reviews page instead of the vendor's generic plugin list.

## [0.1.1]

### Added

- Review/star CTA: after 5 successful test-skeleton generations (never
  counted for the "already exists" no-op or a failed safety check), a
  one-time notification asks whether to rate the plugin on
  Marketplace, with a permanent "Don't ask again" option.

## [0.1.0]

### Added

- "Generate Test Skeleton" action (editor + Project view context menu):
  one empty test method per public method of the class under the
  caret/selection, named `test<MethodName>`.
- Test framework detection by resolving a real marker class (JUnit 4,
  JUnit 5, or TestNG) against the module's own classpath — never a
  config dropdown, never a guess.
- In-memory syntax validation before anything is written to disk: a
  generated skeleton that fails to parse is never offered to the user,
  refused with a clear notification instead.
- Default assertion inference for simple return types (`String`, known
  collections) via `PsiMethod.getReturnType()` — everything else
  (primitives, custom types, `void`) gets an honest `TODO` comment
  instead of a placeholder assertion.
- Optional Mockito mock-field generation for constructor dependencies,
  only when Mockito is already on the target project's own classpath —
  never added as a dependency of this plugin.
- Works for both Java and Kotlin classes.

### Fixed

- Several real threading/classpath-resolution bugs found only by live
  `runIde` testing against a real multi-module Gradle project, none
  caught by unit tests or `verifyPlugin`: PSI/index access off the EDT
  needing `runReadAction`, test-scoped (`testImplementation`)
  dependencies never being visible through a "main" module's own
  search scope in a Gradle "separate module per source set" project,
  and `PsiManager.dropPsiCaches()` requiring the EDT specifically.
  Confirmed working end-to-end afterward: JUnit5 detection, Mockito
  mock generation, and honest per-method `TODO`s all verified against
  a real demo project.

[Unreleased]: https://github.com/GapHunterLabs/test-scaffold-companion/compare/0.1.3...HEAD
[0.1.3]: https://github.com/GapHunterLabs/test-scaffold-companion/compare/0.1.2...0.1.3
[0.1.2]: https://github.com/GapHunterLabs/test-scaffold-companion/compare/0.1.1...0.1.2
[0.1.1]: https://github.com/GapHunterLabs/test-scaffold-companion/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/GapHunterLabs/test-scaffold-companion/commits/0.1.0
