package com.mh.librarymanager.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AtomicFileWriteTest {

    @Test
    fun replacesExistingFile() {
        val dir = createTempDir()
        val target = File(dir, "catalog.json")
        target.writeText("""{"version":4,"books":[]}""")
        atomicWriteText(target, """{"version":4,"books":[{"id":"a"}]}""")
        assertEquals("""{"version":4,"books":[{"id":"a"}]}""", target.readText())
    }

    @Test
    fun createsNewFile() {
        val dir = createTempDir()
        val target = File(dir, "new.json")
        atomicWriteText(target, "ok")
        assertEquals("ok", target.readText())
    }

    @Test
    fun leavesNoStrayTmpOnSuccess() {
        val dir = createTempDir()
        val target = File(dir, "data.json")
        atomicWriteText(target, "v1")
        atomicWriteText(target, "v2")
        assertEquals("v2", target.readText())
        assertTrue(dir.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
    }
}
