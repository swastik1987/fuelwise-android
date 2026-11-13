package app.fuelwise.core.domain.scan

/**
 * Recognised text as ML Kit returns it for Indian pump displays, receipts and clusters. Hand-written from typical
 * dispenser layouts (Tokheim, Gilbarco, Midco), OMC receipt printers and car clusters, with the misreads seen on
 * 7-segment LEDs: O/0, S/5, B/8, l/1 and dropped decimal points.
 *
 * TODO(F-15 AC, PRD D9): replace and extend with the text of the 50 real pump-display photos once collected; the
 *  accuracy test in PumpMeterParserTest then measures the ≥85% auto-fill target.
 */
internal object ScanFixtures {
    fun lines(vararg text: String) = text.map { OcrLine(it) }

    /** A line at row [row] and column [col] of a 100 px grid, for layouts with labels and digits as separate lines. */
    fun at(text: String, row: Int, col: Int, width: Int = 90) =
        OcrLine(text, OcrBox(left = col * 100, top = row * 100, right = col * 100 + width, bottom = row * 100 + 60))

    /** A pump display fixture with the values it should read: total paise, quantity, rate paise. */
    data class Pump(val name: String, val lines: List<OcrLine>, val total: Long, val quantity: Double, val rate: Long)

    val pumpDisplays = listOf(
        Pump(
            "clean labelled petrol",
            lines("AMOUNT ₹ 2000.00", "VOLUME L 19.43", "RATE ₹/L 102.92"),
            total = 200_000,
            quantity = 19.43,
            rate = 10_292,
        ),
        Pump(
            "decimal points dropped",
            lines("SALE 200000", "LITRE 1943", "RATE 10292"),
            total = 200_000,
            quantity = 19.43,
            rate = 10_292,
        ),
        Pump(
            "7-segment confusions O S B l",
            lines("AMOUNT S00.0O", "VOLUME 5.5B", "PRICE B9.62"),
            total = 50_000,
            quantity = 5.58,
            rate = 8_962,
        ),
        Pump(
            "labels under the digits, density shown",
            lines("1500.00", "SALE", "14.58", "LITRES", "102.86", "RATE", "DENSITY 745.3 kg/m3"),
            total = 150_000,
            quantity = 14.58,
            rate = 10_286,
        ),
        Pump(
            "CNG in kg",
            lines("AMOUNT 450.00", "KG 5.09", "RATE Rs/Kg 88.40"),
            total = 45_000,
            quantity = 5.09,
            rate = 8_840,
        ),
        Pump(
            "no labels read",
            lines("2000.00", "19.43", "102.92"),
            total = 200_000,
            quantity = 19.43,
            rate = 10_292,
        ),
        Pump(
            "decimal point read as a gap",
            lines("SALE Rs 2000 00", "VOLUME L 19 43", "RATE 102 92"),
            total = 200_000,
            quantity = 19.43,
            rate = 10_292,
        ),
        Pump(
            "diesel with 3-decimal volume and totalizer noise",
            lines("Rs 3000.00", "Ltrs 33.562", "Rs/L 89.38", "TOTALIZER 1234567.89", "NOZZLE 2"),
            total = 300_000,
            quantity = 33.56,
            rate = 8_938,
        ),
        Pump(
            "labels and digits as separate boxed lines",
            listOf(
                at("AMOUNT", 0, 0),
                at("1000.00", 0, 1),
                at("VOLUME", 1, 0),
                at("9.72", 1, 1),
                at("RATE", 2, 0),
                at("102.88", 2, 1),
            ),
            total = 100_000,
            quantity = 9.72,
            rate = 10_288,
        ),
        Pump(
            "Rs prefix glued to digits",
            lines("SALE Rs.1200.00", "VOL 11.66l", "RATE Rs.102.92/L"),
            total = 120_000,
            quantity = 11.66,
            rate = 10_292,
        ),
        // Real dispenser, Bengaluru, 13 Nov 2026 (owner's photo): Gilbarco Veeder-Root LCD, amount and litres
        // stacked with the ₹ glyph beside the amount, density between them and the rate, header and footer text.
        Pump(
            "Gilbarco LCD with density, header and footer",
            listOf(
                at("ENSURE ZERO BEFORE DELIVERY", 0, 0, width = 400),
                at("2887.14", 1, 1, width = 300),
                at("₹", 1, 4, width = 30),
                at("29.32", 2, 2, width = 200),
                at("Litre", 3, 2),
                at("821.4", 4, 0, width = 150),
                at("Density Kg/m3", 5, 0, width = 150),
                at("98.47", 6, 0, width = 150),
                at("₹/Litre", 7, 0),
                at("GILBARCO VEEDER-ROOT", 6, 3, width = 200),
                at("Minimum Measured Quantity = 2 Litre", 8, 0, width = 400),
            ),
            total = 288_714,
            quantity = 29.32,
            rate = 9_847,
        ),
        Pump(
            "Gilbarco LCD, segment gaps misread and points dropped",
            lines(
                "ENSURE ZERO BEFORE DELIVERY",
                "2BB7.14 Z",
                "2932",
                "Litre",
                "B2 1.4",
                "Density Kg/m",
                "9B.47",
                "Z/Litre",
            ),
            total = 288_714,
            quantity = 29.32,
            rate = 9_847,
        ),
    )

    val receipt = lines(
        "WELCOME",
        "HP SERVICE STATION",
        "100 FEET ROAD INDIRANAGAR",
        "BENGALURU",
        "TEL: 080-00000000",
        "GSTIN 29ABCDE1234F1Z5",
        "RECEIPT NO: 004512",
        "DATE: 16/10/2026 TIME: 18:42",
        "NOZZLE NO: 3",
        "PRODUCT: PETROL",
        "RATE(RS/L): 102.92",
        "VOLUME(L): 19.43",
        "AMOUNT(RS): 2000.00",
        "VEHICLE NO: NOT ENTERED",
        "THANK YOU. VISIT AGAIN",
    )

    val cngReceipt = lines(
        "TAX INVOICE",
        "INDIAN OIL",
        "SHREE GANESH FUELS",
        "SECTOR 18 NOIDA",
        "Date 03.09.2026 08:15 AM",
        "Bill No 7781",
        "Product CNG",
        "Qty(Kg) 5.09",
        "Rate 88.40",
        "Sale Amt",
        "450.00",
    )

    /** Columns read as separate lines; rows rebuilt from boxes. */
    val columnReceipt = listOf(
        at("BHARAT PETROLEUM", 0, 0, width = 300),
        at("SAI BABA AUTOMOBILES", 1, 0, width = 300),
        at("Date", 2, 0), at("16-Oct-26", 2, 1), at("19:05", 2, 2),
        at("Rate", 3, 0), at("104.21", 3, 1),
        at("Volume", 4, 0), at("28.79", 4, 1),
        at("Amount", 5, 0), at("3000.00", 5, 1),
    )
}
