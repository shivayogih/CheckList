package com.dataloom.checklist.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Item photos need no permission: the Photo Picker hands out only what the user selects, and the
 * camera app writes to a temporary file this app shares. The merged manifest (this app plus all
 * libraries) must never gain a camera or storage permission, for example from a new dependency.
 */
@RunWith(RobolectricTestRunner::class)
class PhotoManifestTest {

    private val forbidden = setOf(
        Manifest.permission.CAMERA,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        Manifest.permission.ACCESS_MEDIA_LOCATION,
    )

    // Gradle runs unit tests with the module directory (app/) as the working directory.
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test
    fun `the merged manifest requests no camera, storage or media permission`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions.orEmpty().toSet()
        assertEquals("Forbidden permissions requested: $requested", emptySet<String>(), requested intersect forbidden)
    }

    @Test
    fun `the manifest asks to see camera apps and backports the Photo Picker`() {
        assertTrue(manifest.contains("android.media.action.IMAGE_CAPTURE"))
        assertTrue(manifest.contains("<queries>"))
        assertTrue(manifest.contains("com.google.android.gms.metadata.ModuleDependencies"))
        assertTrue(manifest.contains("photopicker_activity:0:required"))
        forbidden.forEach { assertTrue("$it must not be declared", !manifest.contains("android:name=\"$it\"")) }
    }

    @Test
    fun `the file provider shares only cache folders`() {
        val paths = File("src/main/res/xml/file_paths.xml").readText()
        assertTrue(paths.contains("<cache-path"))
        assertTrue("Item photos in filesDir must never be shared", !paths.contains("<files-path"))
        assertTrue(!paths.contains("<external"))
        assertTrue(!paths.contains("<root-path"))
    }
}
