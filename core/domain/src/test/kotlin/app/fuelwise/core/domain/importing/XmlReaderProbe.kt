package app.fuelwise.core.domain.importing

import app.fuelwise.core.domain.importing.xlsx.XmlReader

/** Reads all text of a small XML document, for testing the reader's safety rules. */
internal object XmlReaderProbe {
    fun read(xml: String): String {
        val reader = XmlReader(xml)
        val out = StringBuilder()
        while (true) {
            val event = reader.next() ?: break
            if (event is XmlReader.Event.Text) out.append(event.text)
        }
        return out.toString()
    }
}
