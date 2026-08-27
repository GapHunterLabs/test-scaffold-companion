<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Test Scaffold Companion Changelog

## [Unreleased]

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

[Unreleased]: https://github.com/GapHunterLabs/test-scaffold-companion/compare/0.1.1...HEAD
[0.1.1]: https://github.com/GapHunterLabs/test-scaffold-companion/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/GapHunterLabs/test-scaffold-companion/commits/0.1.0
