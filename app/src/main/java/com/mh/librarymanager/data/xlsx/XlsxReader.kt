package com.mh.librarymanager.data.xlsx

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Minimal, dependency-free reader for `.xlsx` (Office Open XML) workbooks.
 *
 * Why custom: Apache POI is large (~10MB), pulls reflection-heavy XML stacks
 * that crash on modern Android toolchains, and we only need to read text from
 * the first sheet. An xlsx file is a zip containing XML; the two parts we care
 * about are `xl/sharedStrings.xml` (the de-duplicated string table) and
 * `xl/worksheets/sheet1.xml` (the row/cell grid). This implementation streams
 * the zip once, then parses both XMLs with the platform XmlPullParser.
 */
object XlsxReader {

    /** Reads sheet1 and returns rows of trimmed cell strings. */
    fun readFirstSheet(input: InputStream): List<List<String>> {
        var sharedStringsXml: ByteArray? = null
        var sheetXml: ByteArray? = null

        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                when {
                    name == "xl/sharedStrings.xml" -> sharedStringsXml = zip.readBytes()
                    name == "xl/worksheets/sheet1.xml" -> sheetXml = zip.readBytes()
                }
                zip.closeEntry()
            }
        }

        val sheet = sheetXml ?: error("xlsx is missing xl/worksheets/sheet1.xml")
        val sharedStrings = sharedStringsXml?.let { parseSharedStrings(it) } ?: emptyList()
        return parseSheet(sheet, sharedStrings)
    }

    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(xml.inputStream(), "UTF-8")
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inSi = false
        var inT = false

        while (true) {
            val event = parser.next()
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> { inSi = true; current.setLength(0) }
                    "t" -> if (inSi) inT = true
                }
                XmlPullParser.TEXT -> if (inT) current.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inT = false
                    "si" -> { result.add(current.toString()); inSi = false }
                }
                XmlPullParser.END_DOCUMENT -> return result
            }
        }
    }

    private fun parseSheet(xml: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(xml.inputStream(), "UTF-8")

        val rows = mutableListOf<List<String>>()
        var currentRow: MutableMap<Int, String>? = null
        var currentRowMaxCol = -1
        var cellRef: String? = null
        var cellType: String? = null
        var inValue = false
        var inInlineString = false
        val valueText = StringBuilder()

        while (true) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> {
                        currentRow = mutableMapOf()
                        currentRowMaxCol = -1
                    }
                    "c" -> {
                        cellRef = parser.getAttributeValue(null, "r")
                        cellType = parser.getAttributeValue(null, "t")
                        valueText.setLength(0)
                    }
                    "v" -> inValue = true
                    "t" -> if (cellType == "inlineStr" || cellType == "str") inInlineString = true
                }
                XmlPullParser.TEXT -> if (inValue || inInlineString) valueText.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v" -> inValue = false
                    "t" -> inInlineString = false
                    "c" -> {
                        val raw = valueText.toString()
                        val resolved = when (cellType) {
                            "s" -> raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) }.orEmpty()
                            "b" -> if (raw == "1") "TRUE" else "FALSE"
                            else -> raw
                        }.trim()
                        val col = columnIndex(cellRef)
                        if (col >= 0) {
                            currentRow?.put(col, resolved)
                            if (col > currentRowMaxCol) currentRowMaxCol = col
                        }
                    }
                    "row" -> {
                        val row = currentRow
                        if (row != null && row.isNotEmpty()) {
                            val list = ArrayList<String>(currentRowMaxCol + 1)
                            for (i in 0..currentRowMaxCol) list.add(row[i].orEmpty())
                            rows.add(list)
                        }
                        currentRow = null
                    }
                }
                XmlPullParser.END_DOCUMENT -> return rows
            }
        }
    }

    /** Converts cell reference like `B3` → zero-based column 1. */
    private fun columnIndex(ref: String?): Int {
        if (ref.isNullOrEmpty()) return -1
        var idx = 0
        for (c in ref) {
            if (c in 'A'..'Z') {
                idx = idx * 26 + (c - 'A' + 1)
            } else if (c in 'a'..'z') {
                idx = idx * 26 + (c - 'a' + 1)
            } else break
        }
        return idx - 1
    }
}
