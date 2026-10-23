package pl.finrev.smssamsung

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale

object CsvImport {
    const val MAX_BYTES = 131072

    fun read(input: InputStream): List<Recipient> {
        // read() may return a partial buffer. Never allocate an unbounded response.
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (output.size() <= MAX_BYTES) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val count = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - output.size()))
            if (count < 0) break
            if (count == 0) {
                val byte = input.read()
                if (byte < 0) break
                output.write(byte)
            } else output.write(buffer, 0, count)
        }
        val bytes = output.toByteArray()
        require(bytes.size <= MAX_BYTES) { "Plik przekracza 128 KiB" }
        val text = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) { throw IllegalArgumentException("Plik musi być poprawnym UTF-8") }
        return parse(text.removePrefix("\uFEFF"))
    }

    fun parse(text: String): List<Recipient> {
        require(text.length <= MAX_BYTES) { "Plik przekracza limit" }
        var quotedHeader = false
        var semicolons = 0
        var commas = 0
        for (c in text) {
            if (c == '"') quotedHeader = !quotedHeader
            if (!quotedHeader) when (c) {
                ';' -> semicolons++
                ',' -> commas++
                '\n', '\r' -> break
            }
        }
        val delimiter = if (semicolons > commas) ';' else ','
        val rows = records(text, delimiter)
        require(rows.size >= 2) { "Brak klientów w pliku" }
        val header = rows.first().map { it.trim().lowercase(Locale.ROOT) }
        val phoneColumns = header.indices.filter { header[it] in listOf("phone", "telefon", "numer", "mobile") }
        val nameColumns = header.indices.filter { header[it] in listOf("name", "imie", "imię", "klient", "nazwisko") }
        require(phoneColumns.size == 1 && nameColumns.size <= 1) { "Wymagana jedna kolumna phone/telefon i najwyżej jedna name/imię" }
        val candidates = rows.drop(1).mapIndexed { index, cells ->
            require(cells.size == header.size) { "Niewłaściwa liczba pól w wierszu ${index + 2}" }
            val phone = try { Phones.normalize(cells[phoneColumns.single()]) } catch (_: Exception) {
                throw IllegalArgumentException("Niepoprawny numer w wierszu ${index + 2}")
            }
            val name = nameColumns.singleOrNull()?.let { cells[it].trim() }.orEmpty()
            Recipient(phone, name)
        }
        val batch = RecipientBatch()
        batch.replace(candidates)
        require(batch.all().isNotEmpty()) { "Brak klientów w pliku" }
        return batch.all()
    }

    // RFC4180 quoting (including escaped quotes, delimiters and embedded newlines),
    // with a semicolon variant for Polish exports. Invalid quoting fails closed.
    private fun records(text: String, delimiter: Char): List<List<String>> {
        val result = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var closed = false
        var i = 0
        fun finishCell() { row.add(cell.toString()); cell.setLength(0); closed = false }
        fun finishRow() {
            finishCell()
            if (row.size > 1 || row.any { it.isNotBlank() }) result.add(row.toList())
            require(result.size <= 1000) { "Zbyt wiele wierszy CSV" }
            row = mutableListOf()
        }
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' -> {
                    if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ }
                    else { quoted = false; closed = true }
                }
                quoted -> cell.append(c)
                c == delimiter -> finishCell()
                c == '\r' || c == '\n' -> {
                    finishRow()
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                }
                c == '"' && cell.isEmpty() && !closed -> quoted = true
                else -> {
                    require(!closed && c != '"') { "Niepoprawne cudzysłowy CSV" }
                    cell.append(c)
                }
            }
            i++
        }
        require(!quoted) { "Niezamknięte pole CSV" }
        if (cell.isNotEmpty() || row.isNotEmpty() || closed) finishRow()
        return result
    }
}
