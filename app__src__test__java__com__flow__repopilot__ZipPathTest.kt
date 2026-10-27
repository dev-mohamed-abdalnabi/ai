package com.flow.repopilot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ZipPathTest {
    @Test fun prefixesSafePaths() {
        assertEquals("app/src/Main.kt", safeZipPath("src/Main.kt", "app"))
    }

    @Test fun ignoresGitMetadata() {
        assertNull(safeZipPath(".git/config", ""))
    }

    @Test fun rejectsTraversalPaths() {
        assertThrows(IllegalArgumentException::class.java) { safeZipPath("../../secrets.txt", "") }
    }

    @Test fun normalizesWindowsSeparators() {
        assertEquals("src/lib/file.kt", safeZipPath("src\\lib\\file.kt", ""))
    }
}
