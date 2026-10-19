package app.fuelwise.core.domain.export

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Packs an export's CSV files into one .zip with `java.util.zip`: no library, no temporary files. */
object ZipWriter {
    /** Every entry is stamped [atUtcMs] so the same export makes the same bytes. */
    fun write(files: List<CsvFile>, atUtcMs: Long): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            files.forEach { file ->
                zip.putNextEntry(ZipEntry(file.name).apply { time = atUtcMs })
                zip.write(file.text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
