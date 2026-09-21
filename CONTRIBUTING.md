# Contributing to Mod Loader

Thank you for taking the time to improve Mod Loader. Contributions are welcome when they make the application safer, clearer, easier to maintain, or more useful within its existing purpose.

## Before you start

Please use English for issues, pull requests, commit messages, and project documentation. Localized Android resources and agreement translations should use their intended language.

Mod Loader works with privileged file access, untrusted archives, and game data. A change that looks small can affect recovery or a user’s original files. Explain the problem you are solving, keep the change focused, and include the checks you actually ran. For a large architectural change, open an issue first so the approach can be discussed.

## Development checklist

1. Open the repository root in Android Studio and let Gradle sync.
2. Make the smallest coherent change that solves the problem.
3. Add a meaningful test when behavior or a security boundary changes.
4. Run the JVM tests, debug build, and Android lint.
5. Compile the Android tests. Run privileged tests only on an isolated device or emulator.
6. Update user-facing documentation and translations when behavior changes.

```sh
./gradlew test :app:assembleDebug :app:lintDebug :engine:assembleDebugAndroidTest
```

Use `gradlew.bat` on Windows.

## Pull requests and commits

Write commit messages that say what a change accomplishes, for example `fix(engine): preserve recovery data after a disconnect`. A pull request should explain the user-visible result, the risk it addresses, and the validation performed. Mention any testing that could not be completed.

Keep unrelated formatting or dependency changes out of a functional patch. Never weaken path checks, size limits, Binder authentication, backup verification, update URL validation, or explicit activation confirmation merely to make a test archive pass.

## Content and licensing

Do not commit signing keys, passwords, local SDK paths, private logs, device identifiers, real game bundles, proprietary assets, or someone else’s mod archives. Test fixtures must be project-created and safe to redistribute.

Contributions to original code and documentation are provided under GNU GPL version 3 (`GPL-3.0-only`). Only submit work you have the right to contribute under those terms. Preserve third-party notices and refresh the dependency inventory when dependencies change.

Artwork and game identity have separate rights from the source code. Read [CREDITS.md](CREDITS.md) and [SECURITY.md](SECURITY.md) before adding assets or changing trust boundaries.
