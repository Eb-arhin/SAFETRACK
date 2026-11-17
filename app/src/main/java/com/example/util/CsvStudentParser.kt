package com.example.util

data class ParsedStudentRow(
    val admissionNumber: String,
    val fullName: String,
    val className: String,
    val emergencyContactName: String,
    val emergencyContactPhone: String,
    val homeDestination: String,
    val authorisedPickup: String
)

object CsvStudentParser {

    const val SAMPLE_CSV_TEMPLATE =
        "AdmissionNumber,FullName,ClassName,EmergencyContact,EmergencyPhone,HomeDestination,AuthorisedPickup\n" +
        "ADM-101,Kwame Mensah,Primary 4A,Abena Mensah,+233244000111,East Legon,Abena Mensah (Mother)\n" +
        "ADM-102,Ama Boateng,Primary 4A,Kofi Boateng,+233244000222,Airport Residential,Kofi Boateng (Father)\n" +
        "ADM-103,Yaw Owusu,JHS 1A,Efua Owusu,+233244000333,Cantonments,Efua Owusu (Aunt)"

    fun parse(rawText: String, fallbackClassName: String): List<ParsedStudentRow> {
        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()

        val result = mutableListOf<ParsedStudentRow>()
        lines.forEachIndexed { index, line ->
            val cols = splitCsvLine(line)
            if (cols.isEmpty()) return@forEachIndexed

            // Skip header row if detected
            val firstLower = cols.first().lowercase()
            val secondLower = cols.getOrNull(1)?.lowercase().orEmpty()
            if (index == 0 && (
                    firstLower.contains("admission") ||
                    firstLower.contains("student") ||
                    firstLower.contains("name") ||
                    secondLower.contains("full") ||
                    secondLower.contains("name")
                )
            ) {
                return@forEachIndexed
            }

            if (cols.size == 1) {
                val name = cols[0].trim()
                if (name.length >= 2) {
                    result.add(
                        ParsedStudentRow(
                            admissionNumber = "ADM-${100 + result.size + 1}",
                            fullName = name,
                            className = fallbackClassName,
                            emergencyContactName = "",
                            emergencyContactPhone = "",
                            homeDestination = "Home",
                            authorisedPickup = ""
                        )
                    )
                }
            } else {
                // If column 0 looks like a name (has spaces, no digits) and column 1 looks like ADM, handle gracefully;
                // standard order: AdmissionNumber, FullName, ClassName, EmergencyContact, EmergencyPhone, HomeDestination, AuthorisedPickup
                val c0 = cols.getOrNull(0)?.trim().orEmpty()
                val c1 = cols.getOrNull(1)?.trim().orEmpty()
                val (adm, fullName) = if (c1.length >= 2 && c0.isNotEmpty()) {
                    c0 to c1
                } else {
                    "ADM-${100 + result.size + 1}" to c0.ifBlank { c1 }
                }
                if (fullName.length >= 2) {
                    result.add(
                        ParsedStudentRow(
                            admissionNumber = adm,
                            fullName = fullName,
                            className = cols.getOrNull(2)?.trim()?.ifBlank { fallbackClassName } ?: fallbackClassName,
                            emergencyContactName = cols.getOrNull(3)?.trim().orEmpty(),
                            emergencyContactPhone = cols.getOrNull(4)?.trim().orEmpty(),
                            homeDestination = cols.getOrNull(5)?.trim()?.ifBlank { "Home" } ?: "Home",
                            authorisedPickup = cols.getOrNull(6)?.trim().orEmpty()
                        )
                    )
                }
            }
        }
        return result
    }

    private fun splitCsvLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == ',' && !inQuotes -> {
                    cells.add(current.toString().trim())
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        cells.add(current.toString().trim())
        return cells
    }
}
