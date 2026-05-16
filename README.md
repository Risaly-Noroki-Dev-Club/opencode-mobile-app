# OpenCode Mobile App

Native Android client for controlling OpenCode sessions through `opencode-mobile-agent`.

The server-side agent lives in the separate `opencode-mobile-agent` repository.

Recommended stack:

- Kotlin
- Jetpack Compose
- Material 3
- Material 3 Expressive where stable enough
- Android 12+ dynamic color
- OkHttp HTTP/WebSocket
- Kotlin Serialization
- DataStore for server profiles and tokens
- Navigation Compose

Current Gradle dependencies include:

- `androidx.activity:activity-compose`
- `androidx.compose.material3:material3`
- `androidx.navigation:navigation-compose`
- `androidx.datastore:datastore-preferences`
- `com.squareup.okhttp3:okhttp`
- `org.jetbrains.kotlinx:kotlinx-serialization-json`

Initial screens:

- Connect screen
- Workspace list
- Session list
- Session chat
- Permission request sheet
- Diff viewer

Theme requirements:

- Use dynamic light/dark color schemes on Android 12+.
- Use a custom fallback color scheme on older Android versions.
- Keep destructive/high-risk permission UI independent from dynamic color when needed.

Build:

- `./gradlew :app:assembleDebug`
- Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`
