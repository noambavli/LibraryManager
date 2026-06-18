package com.mh.librarymanager.data.xlsx

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal, dependency-free writer for `.xlsx` (Office Open XML) workbooks.
 *
 * Mirrors [XlsxReader]: an xlsx is just a zip of XML parts. We emit the four
 * structural parts plus a single worksheet whose cells are written as
 * **inline strings** (`t="inlineStr"`). Inline strings avoid a shared-string
 * table and round-trip cleanly back through [XlsxReader] (which already
 * understands the `inlineStr` cell type), so a sheet exported here re-imports
 * without loss.
 *
 * Everything is written as text — numbers included — because the catalog
 * importer treats every cell as a trimmed string anyway. Hebrew is preserved
 * via UTF-8 throughout.
 */
object XlsxWriter {

    /** Serialise [rows] to an in-memory `.xlsx`, e.g. to embed inside another archive. */
    fun toBytes(rows: List<List<String>>, sheetName: String = "Sheet1"): ByteArray {
        val out = ByteArrayOutputStream()
        write(out, rows, sheetName)
        return out.toByteArray()
    }

    /** Write [rows] (each a list of cell strings) as the first/only sheet. */
    fun write(output: OutputStream, rows: List<List<String>>, sheetName: String = "Sheet1") {
        val safeSheet = sanitizeSheetName(sheetName)
        ZipOutputStream(output).use { zip ->
            zip.putUtf8("[Content_Types].xml", CONTENT_TYPES)
            zip.putUtf8("_rels/.rels", ROOT_RELS)
            zip.putUtf8("xl/workbook.xml", workbookXml(safeSheet))
            zip.putUtf8("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            zip.putUtf8("xl/worksheets/sheet1.xml", sheetXml(rows))
        }
    }

    private fun ZipOutputStream.putUtf8(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun workbookXml(sheetName: String): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="${escapeAttr(sheetName)}" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

    private fun sheetXml(rows: List<List<String>>): String {
        val sb = StringBuilder(256 + rows.size * 64)
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
        for ((rowIndex, row) in rows.withIndex()) {
            val rowNum = rowIndex + 1
            sb.append("<row r=\"").append(rowNum).append("\">")
            for ((colIndex, value) in row.withIndex()) {
                if (value.isEmpty()) continue
                val ref = columnName(colIndex) + rowNum
                sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                sb.append(escapeText(value))
                sb.append("</t></is></c>")
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    /** Zero-based column index → spreadsheet column name (0 → A, 26 → AA). */
    private fun columnName(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (i >= 0) {
            sb.insert(0, ('A' + (i % 26)))
            i = i / 26 - 1
        }
        return sb.toString()
    }

    /** Excel sheet names: max 31 chars, none of : \ / ? * [ ]. */
    private fun sanitizeSheetName(name: String): String {
        val cleaned = name.map { if (it in INVALID_SHEET_CHARS) ' ' else it }
            .joinToString("")
            .trim()
            .take(31)
        return cleaned.ifBlank { "Sheet1" }
    }

    private val INVALID_SHEET_CHARS = charArrayOf(':', '\\', '/', '?', '*', '[', ']')

    private fun escapeText(value: String): String {
        val sb = StringBuilder(value.length + 8)
        for (ch in value) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                // Strip characters that are illegal in XML 1.0 so a stray control
                // byte in user data can never produce an unopenable workbook.
                else -> if (isLegalXmlChar(ch)) sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun escapeAttr(value: String): String =
        escapeText(value).replace("\"", "&quot;")

    private fun isLegalXmlChar(ch: Char): Boolean {
        val code = ch.code
        return ch == '\t' || ch == '\n' || ch == '\r' ||
            (code in 0x20..0xD7FF) || (code in 0xE000..0xFFFD)
    }

    private const val CONTENT_TYPES =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

    private const val ROOT_RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private const val WORKBOOK_RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""
}
