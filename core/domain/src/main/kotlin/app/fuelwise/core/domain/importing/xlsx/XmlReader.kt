package app.fuelwise.core.domain.importing.xlsx

/**
 * A small pull reader for the XML inside .xlsx files. Spreadsheet XML is plain elements, attributes and text, so
 * this avoids a parser dependency and its entity-expansion risks: DOCTYPE declarations are refused outright and only
 * the five predefined entities plus numeric references are decoded. Namespace prefixes are dropped from names.
 */
internal class XmlReader(private val xml: String) {
    sealed interface Event {
        data class Start(val name: String, val attributes: Map<String, String>, val selfClosing: Boolean) : Event

        data class End(val name: String) : Event

        data class Text(val text: String) : Event
    }

    private var pos = 0

    /** The next event, or null at the end of the document. */
    fun next(): Event? {
        var event: Event? = null
        while (event == null && pos < xml.length) {
            event = when {
                xml[pos] != '<' -> Event.Text(decode(readUntil('<')))
                xml.startsWith("<?", pos) -> skipPast("?>")
                xml.startsWith("<!--", pos) -> skipPast("-->")
                xml.startsWith("<![CDATA[", pos) -> cdata()
                xml.startsWith("<!", pos) -> throw IllegalArgumentException("DOCTYPE is not allowed")
                xml.startsWith("</", pos) -> end()
                else -> start()
            }
        }
        return event
    }

    /** All remaining events, in document order. */
    fun events(): Sequence<Event> = generateSequence { next() }

    private fun cdata(): Event.Text {
        val begin = pos + CDATA_OPEN
        val close = xml.indexOf("]]>", begin)
        require(close >= 0) { "Unclosed CDATA" }
        pos = close + CDATA_CLOSE
        return Event.Text(xml.substring(begin, close))
    }

    private fun end(): Event.End {
        val close = xml.indexOf('>', pos)
        require(close >= 0) { "Unclosed end tag" }
        val name = xml.substring(pos + 2, close).trim()
        pos = close + 1
        return Event.End(localName(name))
    }

    private fun start(): Event.Start {
        pos++
        val nameStart = pos
        while (pos < xml.length && !endsName(xml[pos])) pos++
        val name = localName(xml.substring(nameStart, pos))
        val attributes = mutableMapOf<String, String>()
        while (pos < xml.length) {
            skipWhitespace()
            when {
                xml.startsWith("/>", pos) -> {
                    pos += 2
                    return Event.Start(name, attributes, selfClosing = true)
                }

                xml[pos] == '>' -> {
                    pos++
                    return Event.Start(name, attributes, selfClosing = false)
                }

                else -> readAttribute(attributes)
            }
        }
        throw IllegalArgumentException("Unclosed start tag <$name")
    }

    private fun readAttribute(into: MutableMap<String, String>) {
        val keyStart = pos
        while (pos < xml.length && xml[pos] != '=' && !xml[pos].isWhitespace()) pos++
        val key = xml.substring(keyStart, pos)
        skipWhitespace()
        require(pos < xml.length && xml[pos] == '=') { "Attribute $key has no value" }
        pos++
        skipWhitespace()
        val quote = xml[pos]
        require(quote == '"' || quote == '\'') { "Attribute $key is not quoted" }
        val close = xml.indexOf(quote, pos + 1)
        require(close >= 0) { "Unclosed attribute $key" }
        into[localName(key)] = decode(xml.substring(pos + 1, close))
        pos = close + 1
    }

    private fun readUntil(stop: Char): String {
        val begin = pos
        val at = xml.indexOf(stop, pos)
        pos = if (at < 0) xml.length else at
        return xml.substring(begin, pos)
    }

    /** Skips a declaration or comment; it produces no event. */
    private fun skipPast(marker: String): Event? {
        val at = xml.indexOf(marker, pos)
        require(at >= 0) { "Unclosed $marker" }
        pos = at + marker.length
        return null
    }

    private fun endsName(c: Char) = c.isWhitespace() || c == '>' || c == '/'

    private fun skipWhitespace() {
        while (pos < xml.length && xml[pos].isWhitespace()) pos++
    }

    companion object {
        private const val CDATA_OPEN = 9
        private const val CDATA_CLOSE = 3
        private const val HEX = 16

        private fun localName(name: String) = name.substringAfter(':')

        /** Decodes the predefined entities and numeric character references. */
        fun decode(text: String): String {
            if ('&' !in text) return text
            val out = StringBuilder(text.length)
            var i = 0
            while (i < text.length) {
                val c = text[i]
                val semi = if (c == '&') text.indexOf(';', i) else -1
                if (semi < 0) {
                    out.append(c)
                    i++
                    continue
                }
                out.append(entity(text.substring(i + 1, semi)))
                i = semi + 1
            }
            return out.toString()
        }

        private fun entity(name: String): String = when {
            name == "amp" -> "&"
            name == "lt" -> "<"
            name == "gt" -> ">"
            name == "quot" -> "\""
            name == "apos" -> "'"
            name.startsWith("#x") -> codePoint(name.substring(2).toInt(HEX))
            name.startsWith("#") -> codePoint(name.substring(1).toInt())
            else -> "&$name;"
        }

        private fun codePoint(value: Int) = String(Character.toChars(value))

        /** Escapes text for element content and attribute values. */
        fun escape(text: String): String = buildString(text.length) {
            text.forEach { c ->
                when (c) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    else -> if (allowed(c)) append(c)
                }
            }
        }

        /** XML 1.0 forbids control characters other than tab and line breaks; they're dropped. */
        private fun allowed(c: Char) = c.code >= SPACE || c in "\t\n\r"

        private const val SPACE = 0x20
    }
}
