# Changelog

## 0.2.0 (2026-05-16)

### Major
- Migrated from Material 3 (Jetpack Compose M3) to Material 2 (M2) for improved user efficiency
- Replaced 6 separate `ModalBottomSheet` composables with a single `ModalBottomSheetLayout` + sealed `SheetContent` class
- Preserved Android 12+ Dynamic Color via a bridge function (`dynamicM2Colors`) that maps M3 color slots to M2

### Changes
- All UI components (Card, Button, Surface, Scaffold, TopAppBar, etc.) migrated to M2 equivalents
- Typography: Switched from M3 Typography to M2 Typography (Roboto, consistent with platform)
- Shapes: New `Shapes.kt` with Material 2 defaults (small=2dp, medium=4dp, large=8dp)
- Color palette: Professional deep blue-grey theme with separate light/dark schemes
- ElevatedFilterChip → FilterChip
- Removed all `import androidx.compose.material3.*` from source files (M3 retained only for dynamic color extraction)

### Infrastructure
- Added `material` + `material-icons-extended` dependencies
- Added `Shapes.kt` for centralized shape definitions
- Dynamic Color: M3 retained as build dependency for `dynamicColorScheme` API only

## 0.1.0 (2026-05-XX)

### Initial MVP
- Connect to `opencode-mobile-agent` via URL + bearer token
- Read agent health, projects, and sessions
- Create new OpenCode sessions
- SSE streaming chat interface
- Permission approval dialog
- Session diff viewer
- Navi command palette (templates, commands, model picker)
- Provider and model management
- i18n: English + Simplified Chinese
