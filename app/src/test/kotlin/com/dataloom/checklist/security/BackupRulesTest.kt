package com.dataloom.checklist.security

import com.dataloom.checklist.data.profile.KeysetProfileAeadProvider
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * The wrapped profile keyset must never reach a cloud backup or a device transfer (security.md).
 * Guards against the file being renamed in code without updating the rules, and against a rules
 * section being dropped.
 */
class BackupRulesTest {

    // Gradle runs unit tests with the module directory (app/) as the working directory.
    private val xmlDir = File("src/main/res/xml")
    private val keysetFile = KeysetProfileAeadProvider.PREFS_FILE + ".xml"
    private val manifest = File("src/main/AndroidManifest.xml").readText()

    private fun excludesKeyset(file: String, section: String) {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(xmlDir, file))
        val sections = document.getElementsByTagName(section)
        assertTrue("$file has no <$section>", sections.length == 1)
        val excludes = (sections.item(0) as Element).getElementsByTagName("exclude")
        val excluded = (0 until excludes.length).map { excludes.item(it) as Element }.any {
            it.getAttribute("domain") == "sharedpref" && it.getAttribute("path") == keysetFile
        }
        assertTrue("$file <$section> must exclude sharedpref $keysetFile", excluded)
    }

    @Test
    fun `cloud backup and device transfer exclude the profile keyset on Android 12+`() {
        excludesKeyset("data_extraction_rules.xml", "cloud-backup")
        excludesKeyset("data_extraction_rules.xml", "device-transfer")
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
    }

    @Test
    fun `auto backup excludes the profile keyset on Android 11 and lower`() {
        excludesKeyset("backup_rules.xml", "full-backup-content")
        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
    }

    @Test
    fun `item photos are backed up with the checklists on every Android version`() {
        // A photo whose row survives a restore but whose file does not would show as a missing picture.
        listOf("data_extraction_rules.xml", "backup_rules.xml").forEach { file ->
            val text = File(xmlDir, file).readText()
            assertTrue("$file must not exclude the photo folder", !text.contains("item_photos"))
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(xmlDir, file))
            val excludes = document.getElementsByTagName("exclude")
            val domains = (0 until excludes.length).map { (excludes.item(it) as Element).getAttribute("domain") }
            assertTrue("$file must not exclude the app files folder", "file" !in domains)
        }
    }

    @Test
    fun `the database is backed up so a restored phone keeps its checklists and can be migrated`() {
        // Only the keyset is excluded (security.md, db-migrations.md); the pre-migration copy sits in noBackupFilesDir.
        listOf("data_extraction_rules.xml", "backup_rules.xml").forEach { file ->
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(xmlDir, file))
            val excludes = document.getElementsByTagName("exclude")
            val domains = (0 until excludes.length).map { (excludes.item(it) as Element).getAttribute("domain") }
            assertTrue("$file must not exclude the database", "database" !in domains)
            assertTrue("$file may only exclude shared preferences", domains.all { it == "sharedpref" })
        }
    }
}
