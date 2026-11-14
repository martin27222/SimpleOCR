package io.github.martin27222.simpleocr.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class CrashReportTest {
    @Test
    fun readableStringsKeepNamesAndDropBinary() {
        val bytes = byteArrayOf(1, 2) + "libonnxruntime.so".toByteArray() + byteArrayOf(0, 9) +
            "ab".toByteArray() + byteArrayOf(0) + "SIGSEGV".toByteArray()
        assertEquals("libonnxruntime.so\nSIGSEGV\n", CrashReport.readableStrings(bytes))
    }
}
