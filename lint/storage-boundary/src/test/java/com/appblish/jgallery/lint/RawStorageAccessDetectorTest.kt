package com.appblish.jgallery.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test

class RawStorageAccessDetectorTest {

    @Test
    fun `flags raw File construction in feature code`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.appblish.jgallery.feature.albums
                    import java.io.File
                    class AlbumLoader {
                        fun load() = File("/sdcard/DCIM").listFiles()
                    }
                    """,
                ).indented(),
            )
            .issues(RawStorageAccessDetector.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun `flags MediaStore reference`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.appblish.jgallery.feature.photos
                    object Q {
                        val uri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    """,
                ).indented(),
            )
            .issues(RawStorageAccessDetector.ISSUE)
            .run()
            .expectErrorCount(1)
    }

    @Test
    fun `clean when going through the storage abstraction`() {
        lint()
            .files(
                kotlin(
                    """
                    package com.appblish.jgallery.feature.albums
                    import com.appblish.jgallery.core.storage.StorageAccess
                    class AlbumLoader(private val storage: StorageAccess) {
                        suspend fun load() = storage.listMedia()
                    }
                    """,
                ).indented(),
                // Minimal stub so the reference resolves during the test compile.
                kotlin(
                    """
                    package com.appblish.jgallery.core.storage
                    interface StorageAccess { suspend fun listMedia(): List<String> }
                    """,
                ).indented(),
            )
            .issues(RawStorageAccessDetector.ISSUE)
            .run()
            .expectClean()
    }
}
