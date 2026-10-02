package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.AccountType
import com.bragadev.fiscal.domain.model.DetectedMonth
import java.nio.file.Path
import java.text.Normalizer
import java.time.YearMonth

/**
 * Identifica o mês e a conta a partir do caminho de uma pasta, seguindo a estrutura usada no pendrive:
 *
 * `CONTAS CONGREGAÇÃO/ANO DE SERVIÇO 2025-2026/4. TRIMESTRE Jun-Jul-Ago/1. JUNHO` → junho de 2026.
 *
 * - A pasta do mês aceita variações como "1. JUNHO", "10.Outubro", "2.  Outubro", "AGOSTO", "3. Março 2024".
 * - O ano vem do próprio nome do mês, ou da pasta-mãe mais próxima que tenha ano.
 * - No ano de serviço "2025-2026", setembro a dezembro pertencem a 2025 e janeiro a agosto a 2026;
 *   um "5. Trimestre" nesse ano de serviço já pertence ao ano seguinte.
 */
object MonthFolderParser {
    private const val SERVICE_YEAR_FIRST_MONTH = 9
    private const val QUARTERS_PER_SERVICE_YEAR = 4

    private val monthNames = mapOf(
        "JANEIRO" to 1, "FEVEREIRO" to 2, "MARCO" to 3, "ABRIL" to 4, "MAIO" to 5, "JUNHO" to 6,
        "JULHO" to 7, "AGOSTO" to 8, "SETEMBRO" to 9, "OUTUBRO" to 10, "NOVEMBRO" to 11, "DEZEMBRO" to 12,
    )
    private val monthSegment = Regex("""^(?:\d+\s*[.\-]?\s*)?([A-Z]+)(?:\s*(?:DE\s+)?(\d{4}))?$""")
    private val yearRange = Regex("""(20\d{2})\s*-\s*(20\d{2})""")
    private val singleYear = Regex("""(?<!\d)(20\d{2})(?!\d)""")
    private val quarterNumber = Regex("""^(\d+)\s*\.?\s*TRIMESTRE""")

    fun detectMonth(folder: Path): DetectedMonth? {
        val segments = segmentsOf(folder)
        for (index in segments.indices.reversed()) {
            val match = monthSegment.matchEntire(normalize(segments[index])) ?: continue
            val month = monthNames[match.groupValues[1]] ?: continue
            val year = match.groupValues[2].toIntOrNull() ?: yearFromAncestors(segments.take(index), month) ?: return null
            return DetectedMonth(YearMonth.of(year, month), segments[index])
        }
        return null
    }

    fun detectAccount(folder: Path): AccountType? = segmentsOf(folder).reversed().firstNotNullOfOrNull { segment ->
        val normalized = normalize(segment)
        when {
            "CONGREGACAO" in normalized -> AccountType.CONGREGACAO
            "MANUTENCAO" in normalized -> AccountType.MANUTENCAO
            else -> null
        }
    }

    private fun yearFromAncestors(ancestors: List<String>, month: Int): Int? {
        for ((index, segment) in ancestors.withIndex().reversed()) {
            yearRange.find(segment)?.let { range ->
                val (first, second) = range.destructured
                val year = if (month >= SERVICE_YEAR_FIRST_MONTH) first.toInt() else second.toInt()
                return year + extraYearsFromQuarter(ancestors.drop(index + 1))
            }
            singleYear.find(segment)?.let { return it.groupValues[1].toInt() }
        }
        return null
    }

    /**
     * Um ano de serviço tem 4 trimestres. Um "5. Trimestre" dentro de "2025-2026"
     * já é o primeiro trimestre do ano seguinte (setembro de 2026, não de 2025).
     */
    private fun extraYearsFromQuarter(segmentsBelowServiceYear: List<String>): Int {
        val quarter = segmentsBelowServiceYear.firstNotNullOfOrNull { quarterNumber.find(normalize(it)) }
            ?.groupValues?.get(1)?.toIntOrNull() ?: return 0
        return (quarter - 1) / QUARTERS_PER_SERVICE_YEAR
    }

    private fun segmentsOf(folder: Path): List<String> = folder.map { it.toString() }.filter { it.isNotBlank() }

    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .uppercase()
            .trim()
            .replace(Regex("\\s+"), " ")
}
