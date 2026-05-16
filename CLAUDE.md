# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

Native Android client (Kotlin + Jetpack Compose + Material 2) that talks to a separate `opencode-mobile-agent` HTTP/SSE server, which in turn proxies to a local `opencode serve` instance. The Android side here is intentionally thin: state is kept in Compose `remember` blocks; everything substantive lives behind the agent's REST + SSE API. The agent itself is at https://github.com/Risaly-Noroki-Dev-Club/opencode-mobile-agent and is NOT in this tree.

The app recently migrated from Material 3 to Material 2 (May 2026). M3 dependencies remain only for dynamic color extraction (`dynamicDarkColorScheme`/`dynamicLightColorScheme`). All UI components use `androidx.compose.material.*` (M2). Bottom sheets use a single `ModalBottomSheetLayout` with a sealed `SheetContent` class.

## Build / run

```bash
./gradlew :app:assembleDebug        # debug APK -> app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug         # install on connected device
```

Requires Android SDK; `local.properties` points to it (`sdk.dir=...`). Toolchain: AGP 8.7.3, Kotlin 2.1.0, JVM 17, compileSdk/targetSdk 35, minSdk 26. No test sources currently exist — `assembleDebug` is the only meaningful build target. The Gradle wrapper expects the SDK to be installed locally; it is not bundled.

## Architecture

Two-screen state machine in `ui/OpenCodeMobileApp.kt`: a nullable `ChatConnection` decides whether the user sees `ConnectScreen` (enter URL + bearer token, validates via `/health` + `/workspaces`, persists to DataStore via `SettingsStore`) or `SessionScreen` (chat over a single session). There is no `NavHost` despite the `navigation-compose` dependency — navigation is a single state toggle. The README's planned multi-workspace / session-list / timeline screens do not exist yet; current UI is one active session at a time.

`data/AgentClient.kt` is the entire network layer — one OkHttp + kotlinx.serialization class that wraps every endpoint the app uses. Two things to know about it:

1. **Dual send path.** `SessionScreen` calls `promptAsync` first (fire-and-forget; assistant output arrives through the SSE stream). Only if that throws does it fall back to the synchronous `sendMessage`. When editing the send flow, preserve this — losing the async path means losing streaming.
2. **SSE parsing in `streamEvents` + `parseStreamEvent`.** The agent emits multiple naming variants for the same logical event (e.g. `session.next.text.delta` and `session.next.text.delta.1`); the parser handles both. Unknown event names are silently dropped (`else -> null`). When adding support for a new event, add it to the `when (name)` branch and a corresponding case in `SessionScreen`'s `streamEvents { event -> ... }` collector. New event types also need a new `OpenCodeStreamEvent` sealed subtype.

`SessionScreen` runs two `LaunchedEffect`s keyed differently: one creates the session + loads history/commands/models on `connection` change; the other (re-)opens the SSE stream on `(connection, sessionId)` change. Stream events are dispatched back to the main thread before mutating `messages`. Assistant text is built incrementally by `appendAssistantDelta` and replaced wholesale by `finalizeAssistantText` on `TextEnded`.

The Navi bottom sheet (`NaviSheet`) is the command palette: prompt templates (string-resource backed), the agent's `/opencode/command` list, the diff viewer trigger, and the model picker all live there. Typing `/` in the message input opens it. Model selection defaults to provider `rakuraku` / model `gpt-5.5` if present, otherwise the first model returned.

Permission requests from the agent (`permission.asked` SSE event) raise a `PermissionSheet` with Reject / Once / Always buttons that POST to `/opencode/permission/{id}/reply`.

## Conventions

- All UI strings are in `res/values/strings.xml` + `res/values-zh-rCN/strings.xml`. Add both when introducing user-facing text; `zh-rCN` activates automatically on Simplified Chinese system locale, otherwise English.
- Theme uses Android 12+ dynamic color via a bridge function (`dynamicM2Colors` in `ui/theme/Color.kt`) that maps extracted M3 scheme colors to M2 `Colors`. Pre-Android 12 falls back to static palettes (deep blue-grey). The `Shapes.kt` file defines M2 defaults (2/4/8dp).
- `android:usesCleartextTraffic="true"` is set in the manifest because users may run the agent on a self-signed/HTTP server. Don't tighten this without a real plan for cert handling.
- Bearer token + server URL are stored in plain Preferences DataStore (`SettingsStore`). No encryption currently; treat the token as low-sensitivity test data only.
