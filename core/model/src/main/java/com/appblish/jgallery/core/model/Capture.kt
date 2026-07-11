package com.appblish.jgallery.core.model

/**
 * Which kind of item a "capture straight into album" flow records (C1-09 item 9, APP-424). It selects
 * the system-camera action (`ACTION_IMAGE_CAPTURE` vs `ACTION_VIDEO_CAPTURE`) and the public media root
 * the captured file lands under (`Pictures/<album>/` vs `Movies/<album>/`). A pure enum with no Android
 * dependency so it can cross the §1.6 storage boundary and be named by the feature layer (APP-297).
 */
enum class CaptureKind { PHOTO, VIDEO }
