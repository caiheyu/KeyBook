package com.github.caiheyu.keybook.core.security

import android.os.Build
import java.io.File
import com.github.caiheyu.keybook.BuildConfig

object RootDetector {
    fun isLikelyRooted(): Boolean = hasRootIndicators(
        buildTags = Build.TAGS,
        checkPath = { File(it).exists() },
        includeTestKeys = !BuildConfig.DEBUG,
    )

    internal fun hasRootIndicators(
        buildTags: String?,
        checkPath: (String) -> Boolean,
        includeTestKeys: Boolean,
    ): Boolean = (includeTestKeys && buildTags.orEmpty().contains("test-keys")) ||
        ROOT_PATHS.any(checkPath)

    private val ROOT_PATHS = listOf(
        "/system/app/Superuser.apk",
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/data/adb/magisk",
    )
}
