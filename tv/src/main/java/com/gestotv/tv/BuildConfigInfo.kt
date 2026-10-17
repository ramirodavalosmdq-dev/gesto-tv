package com.gestotv.tv

import android.content.Context

object BuildConfigInfo {
    fun version(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (e: Exception) { "?" }
}
