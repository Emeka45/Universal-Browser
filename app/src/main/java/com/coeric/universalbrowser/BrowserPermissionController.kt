package com.coeric.universalbrowser

import android.app.Activity
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

/** Central per-site permission policy. Decisions are kept per origin; private tabs are session-only. */
object BrowserPermissionController {
    private const val PREFS = "universal_site_permissions"
    private const val ALLOW = "allow_"
    private const val DENY = "deny_"
    private const val REQUEST = 7401

    fun attach(activity: Activity, session: GeckoSession) {
        session.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onAndroidPermissionsRequest(session: GeckoSession, permissions: Array<String>, callback: GeckoSession.PermissionDelegate.Callback) {
                val missing = permissions.filter { ActivityCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED }
                if (missing.isEmpty()) callback.grant() else {
                    ActivityCompat.requestPermissions(activity, missing.toTypedArray(), REQUEST)
                    Toast.makeText(activity, "Permission requested by the current site", Toast.LENGTH_SHORT).show()
                    callback.reject()
                }
            }

            override fun onContentPermissionRequest(session: GeckoSession, perm: GeckoSession.PermissionDelegate.ContentPermission): GeckoResult<Int>? {
                if (session.settings.usePrivateMode) return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                val origin = originOf(perm.uri)
                val permissionKey = perm.permission.toString()
                val prefs = activity.getSharedPreferences(PREFS, 0)
                if (prefs.getBoolean(ALLOW + origin + permissionKey, false)) return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                if (prefs.getBoolean(DENY + origin + permissionKey, false)) return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)

                perm.notifyShown()
                val result = GeckoResult<Int>()
                android.app.AlertDialog.Builder(activity)
                    .setTitle("Allow site permission?")
                    .setMessage("$origin wants ${friendly(perm.permission)}.")
                    .setNegativeButton("Block") { _, _ -> remember(activity, origin, permissionKey, false); result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY) }
                    .setPositiveButton("Allow") { _, _ -> remember(activity, origin, permissionKey, true); result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW) }
                    .setOnCancelListener { result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY) }
                    .show()
                return result
            }

            override fun onMediaPermissionRequest(session: GeckoSession, uri: String, video: Array<GeckoSession.PermissionDelegate.MediaSource>?, audio: Array<GeckoSession.PermissionDelegate.MediaSource>?, callback: GeckoSession.PermissionDelegate.MediaCallback) {
                val origin = originOf(uri)
                if (session.settings.usePrivateMode) { callback.reject(); return }
                android.app.AlertDialog.Builder(activity).setTitle("Media access")
                    .setMessage("$origin wants access to camera/microphone media.")
                    .setNegativeButton("Block") { _, _ -> callback.reject() }
                    .setPositiveButton("Allow") { _, _ -> callback.grant(video?.firstOrNull(), audio?.firstOrNull()) }
                    .setOnCancelListener { callback.reject() }
                    .show()
            }
        }
    }

    fun clearSiteDecisions(activity: Activity) = activity.getSharedPreferences(PREFS, 0).edit().clear().apply()

    private fun remember(activity: Activity, origin: String, permission: String, allow: Boolean) {
        activity.getSharedPreferences(PREFS, 0).edit().putBoolean((if (allow) ALLOW else DENY) + origin + permission, true).apply()
    }

    private fun friendly(permission: Int) = when (permission) {
        GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> "your location"
        GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> "notifications"
        GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> "persistent storage"
        GeckoSession.PermissionDelegate.PERMISSION_TRACKING -> "tracking"
        GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE, GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE -> "autoplay"
        else -> "additional browser access"
    }

    private fun originOf(uri: String): String = try {
        val u = android.net.Uri.parse(uri)
        u.scheme.orEmpty() + "://" + u.host.orEmpty()
    } catch (_: Throwable) { uri.take(120) }
}
