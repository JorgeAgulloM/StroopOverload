# StroopOverload

Android (Kotlin + Jetpack Compose + Firebase). See `.claude/TASK_PROGRESS.md` for active/past work logs.

## Internationalization (mandatory)

The app ships in 6 languages: English (default), Spanish, Japanese, French, German, Brazilian Portuguese.
All string resources live in `app/src/main/res/values/strings.xml` (English/default) with translated
siblings in `values-es`, `values-ja`, `values-fr`, `values-de`, `values-pt-rBR`.

**Never hardcode user-visible text in Kotlin source.** This includes:
- Composable `Text(...)`, labels, `contentDescription`, button text — use `stringResource(R.string.xxx)`.
- ViewModels and other non-Composable classes that produce user-facing messages (errors, success
  messages) — resolve via `context.getString(R.string.xxx)` (ViewModels needing this must be
  `AndroidViewModel` so they have a `Context`; see `AuthViewModel`).
- Domain models that carry display text (e.g. achievement titles, color names) — store a
  `@StringRes Int` field instead of a `String`, resolved at the point of display. See
  `Achievement.titleRes`/`descriptionRes`, `StroopColor.displayNameRes`, `XpBreakdown.baseLabelRes`.
- Validation/error results that cross a layer without `Context` access (e.g. `AuthService`,
  `MultiplayerViewModel`) — return a sealed error type (see `RegistrationError`, `LoginError`,
  `MultiplayerErrorReason`) and resolve the string at the UI layer, not in the data/domain layer.

When adding a new user-facing string: add the English key to `values/strings.xml` first, then add
the same key with a translation to all 5 other `values-*/strings.xml` files before considering the
feature done — do not leave a language behind.
