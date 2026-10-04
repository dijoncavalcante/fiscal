package com.bragadev.fiscal.domain.rules

import com.bragadev.fiscal.domain.model.AccountType
import com.bragadev.fiscal.domain.model.AccountType.CONGREGACAO
import com.bragadev.fiscal.domain.model.AccountType.MANUTENCAO
import com.bragadev.fiscal.domain.model.AccountType.OUTROS
import com.bragadev.fiscal.domain.model.DocumentCategory
import com.bragadev.fiscal.domain.model.NamingRule

/** Catálogo inicial de categorias. É gravado no banco na primeira execução. */
object DefaultCategories {
    const val OUTROS_ID = "outros"

    /**
     * Outros de cada conta, com descrição e numeração: Congregação "10. Outros - xxx", "10.1 Outros - yyy"...;
     * Manutenção "6. Outros - xxx", "6.1 Outros - yyy"... Substituem o "Outros" geral ("1. Outros.pdf"),
     * que só vale para pastas sem conta identificada.
     */
    private fun outros(prefix: String, account: AccountType, number: String) = DocumentCategory(
        "$prefix.outros", "Outros", number, account,
        namingRule = NamingRule.DESCRIBED_SEQUENCE, optional = true,
    )

    /** Despesas aceitam vários arquivos: "3. Despesa - xxx", "3.1 Despesa - yyy"... */
    private fun despesas(prefix: String, account: AccountType) = DocumentCategory(
        "$prefix.despesas", "Despesas", "3", account,
        namingRule = NamingRule.DESCRIBED_SEQUENCE, fileBaseName = "Despesa",
    )

    val all: List<DocumentCategory> = listOf(
        DocumentCategory("congregacao.folha_de_contas", "Folha de Contas", "1", CONGREGACAO),
        DocumentCategory("congregacao.recibos_reunioes", "Recibos das reuniões", "2", CONGREGACAO),
        despesas("congregacao", CONGREGACAO),
        DocumentCategory("congregacao.to62", "TO-62", "4", CONGREGACAO),
        DocumentCategory("congregacao.remessa_betel", "Remessa Betel", "5", CONGREGACAO),
        DocumentCategory(
            "congregacao.comprovante_remessa", "Comprovante Remessa", "5.1", CONGREGACAO,
            parentId = "congregacao.remessa_betel",
        ),
        DocumentCategory("congregacao.carta_agradecimento", "Carta de Agradecimento", "6", CONGREGACAO),
        DocumentCategory("congregacao.relatorio_mensal", "Relatório Mensal", "7", CONGREGACAO),
        DocumentCategory("congregacao.extrato_bancario", "Extrato Bancário", "8", CONGREGACAO),
        DocumentCategory("congregacao.extrato_betel", "Extrato Betel", "9", CONGREGACAO),
        outros("congregacao", CONGREGACAO, "10"),

        DocumentCategory("manutencao.folha_de_contas", "Folha de Contas", "1", MANUTENCAO),
        DocumentCategory("manutencao.donativos_congregacoes", "Donativos das Congregações", "2", MANUTENCAO),
        despesas("manutencao", MANUTENCAO),
        DocumentCategory("manutencao.relatorio_mensal", "Relatório Mensal", "4", MANUTENCAO),
        DocumentCategory("manutencao.extrato_bancario", "Extrato Bancário", "5", MANUTENCAO),
        outros("manutencao", MANUTENCAO, "6"),

        DocumentCategory(OUTROS_ID, "Outros", "", OUTROS, namingRule = NamingRule.SEQUENTIAL, optional = true),
    )
}
