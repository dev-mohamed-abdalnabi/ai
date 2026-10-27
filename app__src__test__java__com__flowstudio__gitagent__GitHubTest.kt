package com.flowstudio.gitagent

import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubTest {
    @Test
    fun blobShaMatchesGit() {
        assertEquals("ce013625030ba8dba906f756967f9e9ca394464a", gitBlobSha("hello\n".toByteArray()))
        assertEquals("e69de29bb2d1d6434b8b29ae775ad8c2e48c5391", gitBlobSha(ByteArray(0)))
    }

    @Test
    fun stripsSingleTopFolder() {
        val m = normalizeZipPaths(listOf("proj/", "proj/a.txt", "proj/src/b.kt"), true)
        assertEquals(mapOf("proj/a.txt" to "a.txt", "proj/src/b.kt" to "src/b.kt"), m)
    }

    @Test
    fun keepsRootLayoutWhenNotSingleFolder() {
        val m = normalizeZipPaths(listOf("a.txt", "src/b.kt"), true)
        assertEquals(mapOf("a.txt" to "a.txt", "src/b.kt" to "src/b.kt"), m)
    }

    @Test
    fun dropsJunkAndZipSlip() {
        val m = normalizeZipPaths(listOf("__MACOSX/x", "a/.DS_Store", "a/.git/config", "../evil.txt", "a/ok.txt", "b/c.txt"), false)
        assertEquals(mapOf("a/ok.txt" to "a/ok.txt", "b/c.txt" to "b/c.txt"), m)
    }

    @Test
    fun modesAndEncoding() {
        assertEquals("100755", fileMode("gradlew"))
        assertEquals("100644", fileMode("src/Main.kt"))
        assertEquals("a%20b/c.txt", enc("a b/c.txt"))
    }
}
