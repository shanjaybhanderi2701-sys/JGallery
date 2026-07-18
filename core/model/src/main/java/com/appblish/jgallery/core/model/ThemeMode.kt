package com.appblish.jgallery.core.model

/**
 * App-wide theme preference (design G2 Settings §3, SET-02). Persisted in the Settings DataStore and
 * threaded to the root [JGalleryTheme] so the whole NavHost re-themes on change.
 *
 * [SYSTEM] follows the device dark-mode setting (`isSystemInDarkTheme()`); [LIGHT]/[DARK] force a
 * scheme. Default is [SYSTEM] — the historical Wave-1 behavior (light-only) is preserved on a
 * light device.
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }
