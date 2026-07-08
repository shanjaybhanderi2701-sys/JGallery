package com.appblish.jgallery.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API
import com.android.tools.lint.detector.api.Issue

/** Registers JGallery's custom lint checks. Declared via the `Lint-Registry-v2` jar manifest. */
class StorageBoundaryIssueRegistry : IssueRegistry() {

    override val issues: List<Issue> = listOf(RawStorageAccessDetector.ISSUE)

    override val api: Int = CURRENT_API

    // Compatible with lint distributed since AGP 7.0; keep loose so wrapper bumps don't break.
    override val minApi: Int = 10

    override val vendor: Vendor = Vendor(
        vendorName = "appblish — JGallery",
        identifier = "com.appblish.jgallery:lint-storage-boundary",
        feedbackUrl = "https://github.com/appblish/jgallery/issues",
    )
}
