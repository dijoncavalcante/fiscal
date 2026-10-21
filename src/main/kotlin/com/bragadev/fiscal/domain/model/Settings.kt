package com.bragadev.fiscal.domain.model

import java.nio.file.Path
import java.time.YearMonth

data class AppSettings(
    /** Pasta exibida à esquerda, de onde os PDFs são escolhidos. */
    val sourceFolder: Path? = null,
    /** Pasta raiz das contas (onde ficam "CONTAS CONGREGAÇÃO" e "CONTAS MANUTENÇÃO"), para escolher o mês com um clique. */
    val monthsRoot: Path? = null,
    /** Pasta do mês que está sendo editado, à direita. Destino dos documentos organizados. */
    val monthFolder: Path? = null,
    val duplicatePolicy: DuplicatePolicy = DuplicatePolicy.ASK,
    val confirmBeforeMove: Boolean = true,
    val confirmBeforeRename: Boolean = true,
    val documentSort: DocumentSort = DocumentSort.MODIFIED_NEWEST_FIRST,
    /** Apagar sozinho os backups de arquivos substituídos mais antigos que [backupRetentionDays]. Desligado por padrão. */
    val autoCleanBackups: Boolean = false,
    val backupRetentionDays: Int = DEFAULT_BACKUP_RETENTION_DAYS,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** O assistente da primeira vez já foi concluído ou pulado. */
    val onboardingDone: Boolean = false,
    /** Tamanho e posição da janela ao fechar, para reabrir igual. `null` = padrão (maximizada). */
    val windowBounds: WindowBounds? = null,
    /** Primeiro mês que pode ser alterado; os anteriores ficam somente leitura (prestação de contas já entregue). */
    val firstEditableMonth: YearMonth = DEFAULT_FIRST_EDITABLE_MONTH,
    /**
     * Quem assina as despesas de cada conta: nome completo, como aparece no certificado digital (gov.br).
     * Fica só no banco local: troca-se em Configurações quando o responsável muda. Ausente = não configurado.
     */
    val expenseSigners: Map<AccountType, String> = emptyMap(),
    /** Termos que, no nome do arquivo, dispensam o cupom nas despesas da Conta da Congregação. */
    val expenseExemptTerms: List<String> = DEFAULT_EXPENSE_EXEMPT_TERMS,
) {
    /** Usuário novo: falta a pasta de origem ou o mês e o assistente ainda não foi feito (a raiz das contas pode ser deduzida do mês). */
    val needsOnboarding: Boolean
        get() = !onboardingDone && (sourceFolder == null || monthFolder == null)

    companion object {
        const val DEFAULT_BACKUP_RETENTION_DAYS = 90
        val DEFAULT_FIRST_EDITABLE_MONTH: YearMonth = YearMonth.of(2026, 6)
        val BACKUP_RETENTION_OPTIONS = listOf(30, 90, 180, 365)

        /** Contas cujas despesas ("3. Despesas") são conferidas e têm um responsável pela assinatura. */
        val EXPENSE_ACCOUNTS = listOf(AccountType.CONGREGACAO, AccountType.MANUTENCAO)

        /**
         * Despesas da Congregação sem cupom (combinado com o usuário em 20/10/2026). Basta o nome do arquivo ter
         * as palavras principais de um termo, em qualquer ordem: "Pagamento ônibus Assembleia" vale por
         * "Ônibus para a assembleia".
         */
        val DEFAULT_EXPENSE_EXEMPT_TERMS = listOf(
            "Ônibus para o congresso",
            "Ônibus para a assembleia",
            "Resolução",
            "Objetivos específicos",
            "Donativo",
            "Reembolso",
            "Depósito de publicações",
        )
    }
}

/** Tema da interface. */
enum class ThemeMode {
    /** Segue o tema claro/escuro do Windows. */
    SYSTEM,
    LIGHT,
    DARK,
}

/** Posição e tamanho da janela "restaurada" (em dp), e se estava maximizada. */
data class WindowBounds(val x: Int, val y: Int, val width: Int, val height: Int, val maximized: Boolean) {
    fun encode(): String = "$x,$y,$width,$height,$maximized"

    companion object {
        fun decode(text: String): WindowBounds? {
            val parts = text.split(',')
            if (parts.size != 5) return null
            val numbers = parts.take(4).map { it.trim().toIntOrNull() ?: return null }
            val maximized = parts[4].trim().toBooleanStrictOrNull() ?: return null
            return WindowBounds(numbers[0], numbers[1], numbers[2], numbers[3], maximized)
        }
    }
}

/** Ordem da lista de documentos (lado esquerdo). */
enum class DocumentSort {
    /** Mais recentes primeiro, como "Data de modificação" no Explorer. */
    MODIFIED_NEWEST_FIRST,
    NAME,
}

/** Comportamento padrão quando o nome de destino já existe. */
enum class DuplicatePolicy {
    /** Pergunta ao usuário: substituir, criar cópia numerada ou cancelar. */
    ASK,

    /** Cria cópia numerada automaticamente, sem perguntar. Nunca substitui. */
    AUTO_NUMBERED_COPY,

    /** Bloqueia a operação. Nunca substitui nem cria cópia. */
    FORBID,
}

/** Escolha do usuário diante de um conflito de nome. */
enum class DuplicateResolution {
    REPLACE,
    NUMBERED_COPY,
    CANCEL,
}
