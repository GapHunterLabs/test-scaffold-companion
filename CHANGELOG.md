<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Test Scaffold Companion Changelog

## [Unreleased]

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

[Unreleased]: https://github.com/GapHunterLabs/test-scaffold-companion/compare/0.1.0...HEAD
[0.1.0]: https://github.com/GapHunterLabs/test-scaffold-companion/commits/0.1.0
