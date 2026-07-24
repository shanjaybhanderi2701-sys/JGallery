# Crashlytics data-handling standing rule (APP-619 Finding 3)

**Owner:** Security Engineer · **Applies to:** all production code that can throw, and all Crashlytics
usage · **Origin:** APP-618 security review of APP-614 (Firebase Crashlytics + bounded egress guard).

Filora is a **file manager**. Crashlytics uploads, off-device, the **message + stack frames** of every
uncaught exception (and anything sent via its `log()` / `recordException` / custom-keys APIs). In a file
manager, exception text routinely embeds **real user file paths and names** (e.g. an `EACCES` on
`/storage/emulated/0/.../private.vaultkey`, cf. APP-574). That is user content leaving the device.

## The rule (normative)

1. **No user content in exception messages or Crashlytics channels.** Never put a file path, file name,
   album name, search query, URI, or any other user-derived string into:
   - a `throw ...(message)` we author,
   - a `FirebaseCrashlytics.log(...)`, `setCustomKey(...)`, or `recordException(...)` call.

   Describe the *condition*, not the *content*: `error("item no longer exists")`, not
   `error("cannot delete /storage/.../vacation.jpg")`. Identify items by an **opaque id** (MediaStore
   id / stable index), never by display name or path.

2. **No Crashlytics Kotlin/Java API without Security review.** Today the app uses **zero** Crashlytics
   API calls — it auto-initialises via its ContentProvider and reports uncaught exceptions only (good;
   the smallest possible surface). Adding `log`, `setCustomKey`, `setUserId`, or `recordException`
   re-opens the content-leak channel and requires Security sign-off **and** a Play Data-safety re-review.

3. **Honest Data-safety declaration.** The Play Data-safety form (APP-616) must reflect the truth: until
   the sanitizer in §"Residual risk" ships, crash reports **can** contain file paths/names. Declare
   "App activity / Crash logs" and "Files and docs" *may* be collected via diagnostics, unless/until the
   sanitizer removes paths — do not under-declare.

## What is already safe (baseline, verified APP-619)

- Every exception **we author** in `core/storage` (`FileOperationEngine`, `TrashEngine`) already uses
  generic, content-free messages (`"item no longer exists"`, `"copied, but the source could not be
  removed"`). No path/name interpolation. ✅
- No `FirebaseCrashlytics` API usage anywhere in `app/`, `core/`, `feature/`. ✅
- Analytics / AdID / SSAID auto-collection disabled (APP-614 Finding 2); crash-data-only. ✅

## Residual risk the rule cannot cover — the sanitizer (delegated: APP-6xx)

Rule (1) governs **our** strings. It cannot govern **platform/framework** exception text: the OS builds
messages like `java.io.IOException: open failed: EACCES (Permission denied): /storage/emulated/0/DCIM/x.jpg`,
and Crashlytics captures those verbatim on any uncaught crash. To close that gap, install a message
sanitizer as the app's uncaught-exception handler, chained **in front of** the Crashlytics handler
(Crashlytics installs its own during auto-init, so install ours in `Application.onCreate` and delegate
to the one it set). Reference design:

```kotlin
// Installed from JGalleryApplication.onCreate(), AFTER super.onCreate() so Crashlytics auto-init
// has already registered its handler as our delegate.
object CrashlyticsMessageSanitizer {
    // Absolute Android storage paths + trailing filename; also bare filenames with an extension.
    private val ABS_PATH = Regex("""/(?:storage|data|sdcard|mnt)/[^\s"')]+""")
    private val FILENAME = Regex("""\b[\w%.\-]+\.(?:jpg|jpeg|png|gif|webp|heic|mp4|mkv|mov|vaultkey|[A-Za-z0-9]{1,5})\b""")

    fun scrub(message: String?): String? = message
        ?.replace(ABS_PATH, "/<redacted-path>")
        ?.replace(FILENAME, "<redacted-file>")

    fun install() {
        val delegate = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            delegate?.uncaughtException(thread, sanitize(throwable))
        }
    }

    // Rebuild the throwable chain with scrubbed messages, preserving type name + stack frames.
    private fun sanitize(t: Throwable): Throwable { /* copy type, scrub message, recurse cause */ }
}
```

Constraints for the implementer:
- Must run **after** `super.onCreate()` and capture the *current* default handler as delegate, so the
  Crashlytics upload path is preserved (do not replace it).
- Preserve exception **type** and **stack frames** (those are needed for triage and normally do not
  contain user content); scrub only the **message** string of the throwable and its cause chain.
- Verify on-device: force an `IOException` carrying a path, confirm the path is `<redacted-path>` in the
  Crashlytics dashboard before wiring it into the Data-safety declaration.

## Enforcement

- Code review: reject any `throw`/`error`/`require` message or Crashlytics call that interpolates a
  path/name/query. Grep aid: `grep -rn "error(\|throw .*Exception(" core feature app | grep -E '\$|path|name|uri'`.
- The [egress guard](../../build-logic/convention/src/main/kotlin/com/appblish/jgallery/convention/EgressGuardConventionPlugin.kt)
  keeps the *transport* bounded (Crashlytics only); this rule keeps the *payload* clean.
