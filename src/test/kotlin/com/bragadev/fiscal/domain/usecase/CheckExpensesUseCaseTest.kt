package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.data.filesystem.FileRepositoryImpl
import com.bragadev.fiscal.domain.model.*
import com.bragadev.fiscal.domain.repository.DocumentContentReader
import com.bragadev.fiscal.domain.repository.ExpenseCheckRepository
import com.bragadev.fiscal.fakes.FakeCategoryRepository
import com.bragadev.fiscal.fakes.FakeSettingsRepository
import com.bragadev.fiscal.fakes.InMemoryFlagRepository
import com.bragadev.fiscal.fakes.createFakePdf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Conferência das despesas da Conta da Manutenção: pendências automáticas, check verde e cache. */
class CheckExpensesUseCaseTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val september: Path by lazy {
        temp.newFolder("pendriver").toPath().resolve("CONTAS MANUTENÇÃO").resolve("ANO DE SERVIÇO 2025-2026")
            .resolve("5. Trimestre Set-Out-Nov").resolve("1. Setembro").also(Files::createDirectories)
    }

    private val complete = DocumentContent(
        listOf(
            "COMPROVANTE DE VENDA CNPJ TOTAL R$ 125,00",
            "Comprovante de transação Pix Valor: 125.00 Id da transação: E60746948202609191355M3943eq0hGo"
        ),
        signers = listOf("CN=ELVES LINCOLN MENEZES DE LIMA"),
    )
    private val unsigned = complete.copy(signers = emptyList())

    private val fileRepository = FileRepositoryImpl()
    private val flags = InMemoryFlagRepository()
    private val checks = InMemoryExpenseChecks()
    private val reader = FakeReader()
    private val settings = FakeSettingsRepository(
        AppSettings(expenseSigners = mapOf(AccountType.MANUTENCAO to ELVES, AccountType.CONGREGACAO to ANDREY)),
    )
    private val checkExpenses = CheckExpensesUseCase(fileRepository, reader, checks, flags, settings)
    private val checklist = GetMonthChecklistUseCase(fileRepository, FakeCategoryRepository(), flags)

    private val evidences = mutableMapOf<String, String?>()

    private suspend fun run(
        folder: Path = september,
        account: AccountType = AccountType.MANUTENCAO
    ): Map<String, ExpenseStatus> {
        val results = mutableMapOf<String, ExpenseStatus>()
        checkExpenses(folder, account, checklist(folder, account)) { name, status, evidence ->
            results[name] = status
            evidences[name] = evidence
        }
        return results
    }

    @Test
    fun `despesa completa ganha o check e incompleta ganha pendencia dizendo o que falta`() = runTest {
        val ok = september.createFakePdf("3. Despesa - Dreno encapado.pdf")
        val missing = september.createFakePdf("3.1 Despesa - Espuma para microfone.pdf")
        september.createFakePdf("6. Outros - Planilha de controle.pdf")
        reader.contents[ok.fileName.toString()] = complete
        reader.contents[missing.fileName.toString()] = unsigned

        val results = run()

        assertEquals(ExpenseStatus.VERIFIED, results["3. Despesa - Dreno encapado.pdf"])
        assertEquals(ExpenseStatus.HAS_ISSUES, results["3.1 Despesa - Espuma para microfone.pdf"])
        assertNull(flags.flags[ok])
        assertEquals("Falta assinatura de $ELVES", flags.flags[missing])
        // Só as despesas são conferidas ("Outros" não tem regras).
        assertEquals(2, results.size)
    }

    private val congregation: Path by lazy {
        temp.newFolder("pendriver2").toPath().resolve("CONTAS CONGREGAÇÃO").resolve("ANO DE SERVIÇO 2025-2026")
            .resolve("5. Trimestre Set-Out-Nov").resolve("1. Setembro").also(Files::createDirectories)
    }

    /** Pix assinado pelo Andrey, sem cupom (como as despesas reais de "Resolução mensal"). */
    private val andreyPix = DocumentContent(
        listOf("Comprovante de transferência Pix Valor R$ 800,00 Id da transação: E60746948202608101330M3943abcdeF"),
        signers = listOf("CN=ANDREY ALVES DA COSTA"),
    )

    @Test
    fun `Congregacao - isenta pelo nome passa sem cupom e a outra exige cupom`() = runTest {
        val exempt = congregation.createFakePdf("3. Despesa - Resolução Mensal.pdf")
        val needsReceipt = congregation.createFakePdf("3.1 Despesa - Compra de cartazes para o display.pdf")
        reader.contents[exempt.fileName.toString()] = andreyPix
        reader.contents[needsReceipt.fileName.toString()] = andreyPix

        val results = run(congregation, AccountType.CONGREGACAO)

        assertEquals(ExpenseStatus.VERIFIED, results[exempt.fileName.toString()])
        assertEquals(
            "Sem cupom: despesa isenta (\"Resolução\" no nome do arquivo)",
            evidences[exempt.fileName.toString()]
        )
        assertNull(flags.flags[exempt])
        assertEquals(ExpenseStatus.HAS_ISSUES, results[needsReceipt.fileName.toString()])
        assertEquals("Falta cupom, nota fiscal ou recibo", flags.flags[needsReceipt])
    }

    @Test
    fun `assinatura do Elves nao vale na Congregacao`() = runTest {
        val file = congregation.createFakePdf("3. Despesa - Resolução Mensal.pdf")
        reader.contents[file.fileName.toString()] = complete

        run(congregation, AccountType.CONGREGACAO)

        assertEquals("Falta assinatura de $ANDREY", flags.flags[file])
    }

    @Test
    fun `trocar o responsavel confere de novo e troca a pendencia automatica`() = runTest {
        val file = september.createFakePdf("3. Despesa - Dreno encapado.pdf")
        reader.contents[file.fileName.toString()] = complete
        assertEquals(ExpenseStatus.VERIFIED, run()[file.fileName.toString()])

        settings.save(settings.settings.value.copy(expenseSigners = mapOf(AccountType.MANUTENCAO to "Fulano de Tal")))
        val results = run()

        assertEquals(2, reader.reads)
        assertEquals(ExpenseStatus.HAS_ISSUES, results[file.fileName.toString()])
        assertEquals("Falta assinatura de Fulano de Tal", flags.flags[file])
    }

    @Test
    fun `sem responsavel configurado a pendencia diz onde configurar`() = runTest {
        val file = september.createFakePdf("3. Despesa - Dreno encapado.pdf")
        reader.contents[file.fileName.toString()] = complete
        settings.save(settings.settings.value.copy(expenseSigners = emptyMap()))

        run()

        assertEquals(
            "Falta assinatura do responsável (configure quem assina em Configurações → Assinaturas das despesas)",
            flags.flags[file],
        )
    }

    @Test
    fun `tirar um termo de isencao confere de novo e passa a exigir cupom`() = runTest {
        val file = congregation.createFakePdf("3. Despesa - Resolução Mensal.pdf")
        reader.contents[file.fileName.toString()] = andreyPix
        assertEquals(ExpenseStatus.VERIFIED, run(congregation, AccountType.CONGREGACAO)[file.fileName.toString()])

        settings.save(settings.settings.value.copy(expenseExemptTerms = listOf("Donativo")))
        run(congregation, AccountType.CONGREGACAO)

        assertEquals(2, reader.reads)
        assertEquals("Falta cupom, nota fiscal ou recibo", flags.flags[file])
    }

    @Test
    fun `pasta sem conta identificada nao e conferida`() = runTest {
        val folder = temp.newFolder("Downloads").toPath()
        folder.createFakePdf("3. Despesa - Luz.pdf")

        assertTrue(run(folder, AccountType.OUTROS).isEmpty())
        assertEquals(0, reader.reads)
    }

    @Test
    fun `pendencia escrita pelo usuario nunca e trocada nem removida`() = runTest {
        val file = september.createFakePdf("3. Despesa - Dreno encapado.pdf")
        reader.contents[file.fileName.toString()] = unsigned
        flags.set(file, "Valor diferente do cupom")

        run()
        assertEquals("Valor diferente do cupom", flags.flags[file])

        // Mesmo depois de corrigido o arquivo.
        replace(file, complete)
        run()
        assertEquals("Valor diferente do cupom", flags.flags[file])
    }

    @Test
    fun `arquivo corrigido perde a pendencia automatica`() = runTest {
        val file = september.createFakePdf("3. Despesa - Dreno encapado.pdf")
        reader.contents[file.fileName.toString()] = unsigned
        run()
        assertEquals("Falta assinatura de $ELVES", flags.flags[file])

        replace(file, complete)
        val results = run()

        assertNull(flags.flags[file])
        assertEquals(ExpenseStatus.VERIFIED, results[file.fileName.toString()])
    }

    @Test
    fun `arquivo sem mudanca nao e lido de novo e pendencia removida pelo usuario nao volta`() = runTest {
        val file = september.createFakePdf("3. Despesa - Dreno encapado.pdf")
        reader.contents[file.fileName.toString()] = unsigned
        run()
        assertEquals(1, reader.reads)

        flags.clear(file)
        val results = run()

        assertEquals(1, reader.reads)
        assertNull(flags.flags[file])
        assertEquals(ExpenseStatus.HAS_ISSUES, results[file.fileName.toString()])
    }

    @Test
    fun `despesa verificada diz de onde veio o total, tambem quando vem do resultado guardado`() = runTest {
        val file = september.createFakePdf("3. Despesa - Dreno encapado.pdf")
        reader.contents[file.fileName.toString()] = complete
        val expected = "Valor conferido: R$ 125,00 em \"TOTAL\", página 1 (texto do PDF)"

        run()
        assertEquals(expected, evidences[file.fileName.toString()])

        evidences.clear()
        run()
        assertEquals(1, reader.reads)
        assertEquals(expected, evidences[file.fileName.toString()])
    }

    @Test
    fun `valor do Pix diferente do cupom vira pendencia que diz como corrigir`() = runTest {
        val file = september.createFakePdf("3. Despesa - Material de limpeza.pdf")
        reader.contents[file.fileName.toString()] = complete.copy(
            pageTexts = listOf("NOTA FISCAL Total R$ 395,98", "Comprovante de transação Pix Valor: 405.98"),
        )

        assertEquals(ExpenseStatus.HAS_ISSUES, run()[file.fileName.toString()])
        assertEquals(
            "Valor do comprovante (R$ 405,98) diferente do cupom/nota (R$ 395,98): confira e troque o documento errado",
            flags.flags[file],
        )
    }

    @Test
    fun `resultado guardado por regras antigas e conferido de novo`() = runTest {
        val file = september.createFakePdf("3. Despesa - Dreno encapado.pdf")
        reader.contents[file.fileName.toString()] = complete
        val old = StoredExpenseCheck(
            Files.size(file), Files.getLastModifiedTime(file).toInstant(), ExpenseCheck(), rulesVersion = 1,
        )
        checks.save(file, old)

        run()

        assertEquals(1, reader.reads)
    }

    @Test
    fun `pendencia automatica da versao anterior e trocada pela nova`() = runTest {
        // Até a versão 2 o recibo não valia: a pendência antiga dizia "cupom ou nota fiscal".
        val file = september.createFakePdf("3. Despesa - Conserto do portão.pdf")
        flags.set(file, "Falta cupom ou nota fiscal")
        checks.save(
            file,
            StoredExpenseCheck(
                Files.size(file), Files.getLastModifiedTime(file).toInstant(),
                ExpenseCheck(setOf(ExpenseRequirement.FISCAL_DOCUMENT)), rulesVersion = 2,
            ),
        )
        reader.contents[file.fileName.toString()] = complete.copy(
            pageTexts = listOf("RECIBO Recebi a importância de R$ 125,00 Total R$ 125,00", complete.pageTexts[1]),
        )

        assertEquals(ExpenseStatus.VERIFIED, run()[file.fileName.toString()])
        assertNull(flags.flags[file])
    }

    @Test
    fun `fora do Checar pendencias so le os arquivos pedidos e os outros ficam nao conferidos`() = runTest {
        val organized = september.createFakePdf("3. Despesa - Dreno encapado.pdf")
        val other = september.createFakePdf("3.1 Despesa - Espuma para microfone.pdf")
        reader.contents[organized.fileName.toString()] = complete
        reader.contents[other.fileName.toString()] = unsigned
        val results = mutableMapOf<String, ExpenseStatus>()

        // Abrir o mês sem nada pedido: nenhum PDF é lido e nenhuma pendência é criada.
        checkExpenses(
            september,
            AccountType.MANUTENCAO,
            checklist(september, AccountType.MANUTENCAO),
            only = emptySet()
        ) { name, status, _ ->
            results[name] = status
        }
        assertEquals(0, reader.reads)
        assertEquals(ExpenseStatus.UNCHECKED, results[organized.fileName.toString()])
        assertEquals(ExpenseStatus.UNCHECKED, results[other.fileName.toString()])
        assertNull(flags.flags[other])

        // Documento recém-organizado: só ele é lido.
        val onlyOrganized = setOf(organized.fileName.toString().lowercase())
        checkExpenses(
            september,
            AccountType.MANUTENCAO,
            checklist(september, AccountType.MANUTENCAO),
            only = onlyOrganized
        ) { name, status, _ ->
            results[name] = status
        }
        assertEquals(1, reader.reads)
        assertEquals(ExpenseStatus.VERIFIED, results[organized.fileName.toString()])
        assertEquals(ExpenseStatus.UNCHECKED, results[other.fileName.toString()])

        // "Checar pendências" (todos): lê só o que falta; o já conferido vem do resultado guardado.
        val all = run()
        assertEquals(2, reader.reads)
        assertEquals(ExpenseStatus.VERIFIED, all[organized.fileName.toString()])
        assertEquals(ExpenseStatus.HAS_ISSUES, all[other.fileName.toString()])
        assertEquals("Falta assinatura de $ELVES", flags.flags[other])
    }

    @Test
    fun `falha passageira nao marca pendencia e tenta de novo depois`() = runTest {
        val file = september.createFakePdf("3. Despesa - Dreno encapado.pdf")
        reader.failure = FileOperationError.FileLocked

        assertEquals(ExpenseStatus.NOT_CHECKED, run()[file.fileName.toString()])
        assertNull(flags.flags[file])

        reader.failure = null
        reader.contents[file.fileName.toString()] = complete
        assertEquals(ExpenseStatus.VERIFIED, run()[file.fileName.toString()])
    }

    @Test
    fun `PDF que nao abre vira pendencia`() = runTest {
        val file = september.createFakePdf("3. Despesa - Dreno encapado.pdf")
        reader.failure = FileOperationError.InvalidPdf

        assertEquals(ExpenseStatus.HAS_ISSUES, run()[file.fileName.toString()])
        assertEquals("Não foi possível ler o PDF", flags.flags[file])
    }

    /** "5. Extrato Bancário" do next, de setembro/2026, assinado pelo Filipe. */
    private val nextSeptember = DocumentContent(
        listOf(
            "Extrato de Conta Poupança Banco de origem: next (237 - Banco Bradesco S.A.) " +
                    "Extrato de: Agência:  | Conta:  | Tipo: 3943 286256-5 Conta Poupança " +
                    "Nome: DANILO CAVALCANTE BRAGA Movimentação entre: 01/09/2026 e 30/09/2026",
        ),
        signers = listOf("FILIPE DA SILVA COSTA"),
    )

    @Test
    fun `extrato bancario e conferido com o mes em edicao e quem assina os extratos`() = runTest {
        val statement = september.createFakePdf("5. Extrato Bancário.pdf")
        reader.contents[statement.fileName.toString()] = nextSeptember
        val month = java.time.YearMonth.of(2026, 9)
        suspend fun runStatement() = mutableMapOf<String, ExpenseStatus>().also { results ->
            checkExpenses(
                september,
                AccountType.MANUTENCAO,
                checklist(september, AccountType.MANUTENCAO),
                month
            ) { name, status, evidence ->
                results[name] = status
                evidences[name] = evidence
            }
        }

        // Ninguém configurado para os extratos: pendência dizendo onde configurar.
        assertEquals(ExpenseStatus.HAS_ISSUES, runStatement()[statement.fileName.toString()])
        assertTrue(flags.flags[statement]!!.startsWith("Falta assinatura do responsável pelos extratos"))

        // Configurado: confere de novo e a pendência automática sai.
        settings.save(settings.settings.value.copy(statementSigner = "Filipe da Silva Costa"))
        assertEquals(ExpenseStatus.VERIFIED, runStatement()[statement.fileName.toString()])
        assertNull(flags.flags[statement])
        assertTrue(evidences[statement.fileName.toString()]!!.contains("de 01/09/2026 a 30/09/2026"))

        // A assinatura das despesas (Elves) não vale para o extrato.
        settings.save(settings.settings.value.copy(statementSigner = ELVES))
        assertEquals(ExpenseStatus.HAS_ISSUES, runStatement()[statement.fileName.toString()])
        assertEquals("Falta assinatura de $ELVES", flags.flags[statement])
    }

    @Test
    fun `extrato de outro mes ganha pendencia e o da Betel nao e conferido`() = runTest {
        val statement = congregation.createFakePdf("8. Extrato Bancário.pdf")
        val betel = congregation.createFakePdf("9. Extrato Betel.pdf")
        reader.contents[statement.fileName.toString()] = DocumentContent(
            listOf(
                "Filipe da Silva Costa CPF Agência 0001 Conta 15077760-0 " +
                        "01 DE AGOSTO DE 2026 a 31 DE AGOSTO DE 2026 VALORES EM R$ Nu Pagamentos S.A.",
            ),
            signers = listOf("FILIPE DA SILVA COSTA"),
        )
        settings.save(settings.settings.value.copy(statementSigner = "Filipe da Silva Costa"))
        val results = mutableMapOf<String, ExpenseStatus>()
        checkExpenses(
            congregation, AccountType.CONGREGACAO, checklist(congregation, AccountType.CONGREGACAO),
            java.time.YearMonth.of(2026, 9),
        ) { name, status, _ -> results[name] = status }

        assertEquals(mapOf(statement.fileName.toString() to ExpenseStatus.HAS_ISSUES), results)
        assertEquals(
            "Extrato de 01/08/2026 a 31/08/2026, mas o mês em edição é setembro de 2026: troque pelo extrato do mês",
            flags.flags[statement],
        )
        assertNull(flags.flags[betel])
    }

    @Test
    fun `Folha de Contas da outra conta ganha pendencia e a certa ganha o check`() = runTest {
        val wrong = congregation.createFakePdf("1. Folha de Contas.pdf")
        reader.contents[wrong.fileName.toString()] = DocumentContent(
            listOf("FOLHA DE CONTAS (S-26)\nComissão de Funcionamento do Salão do Reino setembro de 2026"),
        )
        val month = java.time.YearMonth.of(2026, 9)
        suspend fun runSheet() = mutableMapOf<String, ExpenseStatus>().also { results ->
            checkExpenses(
                congregation,
                AccountType.CONGREGACAO,
                checklist(congregation, AccountType.CONGREGACAO),
                month
            ) { name, status, evidence ->
                results[name] = status
                evidences[name] = evidence
            }
        }

        assertEquals(ExpenseStatus.HAS_ISSUES, runSheet()[wrong.fileName.toString()])
        assertEquals(
            "Folha de Contas da conta da Manutenção (Comissão de Funcionamento do Salão do Reino), não da conta da " +
                    "Congregação: troque pela folha certa",
            flags.flags[wrong],
        )

        // Trocada pela folha certa: a pendência automática sai.
        replace(
            wrong,
            DocumentContent(listOf("FOLHA DE CONTAS (S-26)\nTrinta e Um de Março - Manaus AM (68361) setembro de 2026"))
        )
        assertEquals(ExpenseStatus.VERIFIED, runSheet()[wrong.fileName.toString()])
        assertNull(flags.flags[wrong])
        assertEquals(
            "FOLHA DE CONTAS (S-26) de Trinta e Um de Março - Manaus AM (68361), setembro de 2026",
            evidences[wrong.fileName.toString()],
        )
    }

    @Test
    fun `Relatorio Mensal de outro mes ganha pendencia e o do mes ganha o check`() = runTest {
        val report = september.createFakePdf("4. Relatório Mensal.pdf")
        fun s44(month: String) = DocumentContent(
            listOf("RELATÓRIO MENSAL DE CONTAS DA COMISSÃO DE FUNCIONAMENTO DO SALÃO DO\nREINO (S-44)\n$month\n"),
        )
        reader.contents[report.fileName.toString()] = s44("agosto de 2026")
        suspend fun runReport() = mutableMapOf<String, ExpenseStatus>().also { results ->
            checkExpenses(
                september,
                AccountType.MANUTENCAO,
                checklist(september, AccountType.MANUTENCAO),
                java.time.YearMonth.of(2026, 9),
            ) { name, status, evidence ->
                results[name] = status
                evidences[name] = evidence
            }
        }

        assertEquals(ExpenseStatus.HAS_ISSUES, runReport()[report.fileName.toString()])
        assertEquals(
            "Relatório Mensal de agosto de 2026, mas o mês em edição é setembro de 2026: troque pelo relatório do mês",
            flags.flags[report],
        )

        replace(report, s44("setembro de 2026"))
        assertEquals(ExpenseStatus.VERIFIED, runReport()[report.fileName.toString()])
        assertNull(flags.flags[report])
        assertEquals(
            "RELATÓRIO MENSAL DE CONTAS DA COMISSÃO DE FUNCIONAMENTO DO SALÃO DO REINO (S-44), setembro de 2026",
            evidences[report.fileName.toString()],
        )
    }

    @Test
    fun `donativos sao conferidos juntos e renomear um confere os dois de novo`() = runTest {
        fun pix(moment: String, origin: String, account: String) = DocumentContent(
            listOf(
                "Comprovante de transferência $moment Valor R$ 800,00 Destino Nome Danilo Cavalcante Braga " +
                        "CPF ***.638.182-** Instituição BCO BRADESCO S.A. Agência 3943 Conta 286256-5 " +
                        "Tipo de conta Conta poupança Origem Nome $origin Instituição NU PAGAMENTOS - IP " +
                        "Agência 0001 Conta $account",
            ),
        )

        val japiim = september.createFakePdf("2. Donativo - Japiim.pdf")
        val trinta = september.createFakePdf("2.1 Donativo - Trinta e um de março.pdf")
        // Japiim transferiu depois: os números estão trocados.
        reader.contents[japiim.fileName.toString()] =
            pix("10 SET 2026 - 11:00:00", "Walter Santana Mota do Rosário CPF ***.250.412-**", "18938180-7")
        reader.contents[trinta.fileName.toString()] =
            pix("10 SET 2026 - 10:36:07", "Filipe da Silva Costa CPF ***.369.732-**", "15077760-0")
        suspend fun runDonations() = mutableMapOf<String, ExpenseStatus>().also { results ->
            checkExpenses(
                september,
                AccountType.MANUTENCAO,
                checklist(september, AccountType.MANUTENCAO),
                java.time.YearMonth.of(2026, 9),
            ) { name, status, evidence ->
                results[name] = status
                evidences[name] = evidence
            }
        }

        val first = runDonations()
        assertEquals(ExpenseStatus.HAS_ISSUES, first[japiim.fileName.toString()])
        assertEquals(
            "Transferência de 10/09/2026 11:00 é a segunda do mês: o arquivo deve começar com \"2.1\"",
            flags.flags[japiim],
        )
        assertEquals(2, reader.reads)

        // Sem mudança: nada é lido de novo.
        runDonations()
        assertEquals(2, reader.reads)

        // O usuário troca os números: a categoria mudou, os dois são conferidos de novo.
        val japiimFixed = Files.move(japiim, september.resolve("2.1 Donativo - Japiim.pdf"))
        val trintaFixed = Files.move(trinta, september.resolve("2. Donativo - Trinta e um de março.pdf"))
        reader.contents[japiimFixed.fileName.toString()] = reader.contents.getValue(japiim.fileName.toString())
        reader.contents[trintaFixed.fileName.toString()] = reader.contents.getValue(trinta.fileName.toString())
        val fixed = runDonations()
        assertEquals(ExpenseStatus.VERIFIED, fixed[japiimFixed.fileName.toString()])
        assertEquals(ExpenseStatus.VERIFIED, fixed[trintaFixed.fileName.toString()])
        assertEquals(4, reader.reads)
        assertEquals(
            "Trinta e Um de Março: 10/09/2026 10:36, R$ 800,00, de Filipe da Silva Costa para Danilo Cavalcante Braga",
            evidences[trintaFixed.fileName.toString()],
        )
    }

    @Test
    fun `despesa paga em outro mes ganha pendencia com a data do comprovante`() = runTest {
        val file = september.createFakePdf("3. Despesa - Dreno encapado.pdf")
        fun paidOn(date: String) = complete.copy(
            pageTexts = listOf(
                complete.pageTexts[0],
                "Comprovante de transação Pix Data da operação: $date - 10:55 Valor: 125.00",
            ),
        )
        reader.contents[file.fileName.toString()] = paidOn("01/10/2026")
        suspend fun runSeptember() = mutableMapOf<String, ExpenseStatus>().also { results ->
            checkExpenses(
                september,
                AccountType.MANUTENCAO,
                checklist(september, AccountType.MANUTENCAO),
                java.time.YearMonth.of(2026, 9),
            ) { name, status, _ -> results[name] = status }
        }

        assertEquals(ExpenseStatus.HAS_ISSUES, runSeptember()[file.fileName.toString()])
        assertEquals(
            "Comprovante Pix/TED de 01/10/2026, mas o mês em edição é setembro de 2026: confira se a despesa é deste mês",
            flags.flags[file],
        )

        replace(file, paidOn("19/09/2026"))
        assertEquals(ExpenseStatus.VERIFIED, runSeptember()[file.fileName.toString()])
        assertNull(flags.flags[file])
    }

    @Test
    fun `TO-62 e conferido com a Remessa Betel do mes e organizar a remessa confere de novo`() = runTest {
        settings.save(settings.settings.value.copy(accountsServant = "Dijon Braga"))
        val to62 = congregation.createFakePdf("4. TO-62.pdf")
        reader.contents[to62.fileName.toString()] = DocumentContent(
            listOf(
                "REGISTRO DE TRANSFERENCIA DE FUNDOS (TO-62) Congregacao: Trinta e Um de Marco - Manaus AM (68361) " +
                        "Total de donativos e pagamentos: 975,00 TOTAL DE FUNDOS ENVIADOS: 975,00",
            ),
            signers = listOf("CN=DIJON BRAGA", "CN=ANDREY ALVES DA COSTA"),
            ocrPages = setOf(0),
        )
        suspend fun runCongregation(only: Set<String>? = null) = mutableMapOf<String, ExpenseStatus>().also { results ->
            checkExpenses(
                congregation,
                AccountType.CONGREGACAO,
                checklist(congregation, AccountType.CONGREGACAO),
                java.time.YearMonth.of(2026, 9),
                only,
            ) { name, status, evidence ->
                results[name] = status
                evidences[name] = evidence
            }
        }

        // Sem remessa no mês: pendência dizendo por quê.
        assertEquals(ExpenseStatus.HAS_ISSUES, runCongregation()[to62.fileName.toString()])
        assertEquals(
            "TOTAL DE FUNDOS ENVIADOS do TO-62 (R$ 975,00) não pôde ser conferido: \"5. Remessa Betel\" não tem " +
                    "nenhum documento neste mês",
            flags.flags[to62],
        )

        // A remessa é organizada: só ela foi pedida, mas o TO-62 depende dela e é conferido de novo.
        val remittance = congregation.createFakePdf("5. Remessa Betel.pdf")
        reader.contents[remittance.fileName.toString()] =
            DocumentContent(listOf("Total R$975,00 Remessas Associação Torre de Vigia"))
        assertEquals(
            ExpenseStatus.VERIFIED,
            runCongregation(only = setOf(remittance.fileName.toString().lowercase()))[to62.fileName.toString()],
        )
        assertNull(flags.flags[to62])
        assertEquals(
            "TOTAL DE FUNDOS ENVIADOS R$ 975,00, igual ao da Remessa Betel; assinado por Dijon Braga e $ANDREY",
            evidences[to62.fileName.toString()],
        )

        // Sem mudança: nada é lido de novo.
        val reads = reader.reads
        runCongregation()
        assertEquals(reads, reader.reads)

        // A remessa é trocada por outra com outro total: o TO-62 ganha a pendência com os dois valores.
        replace(remittance, DocumentContent(listOf("Total R$960,00 Remessas")))
        assertEquals(ExpenseStatus.HAS_ISSUES, runCongregation()[to62.fileName.toString()])
        assertEquals(
            "TOTAL DE FUNDOS ENVIADOS do TO-62 (R$ 975,00) é diferente do Total da Remessa Betel (R$ 960,00): " +
                    "confira à mão (foto)",
            flags.flags[to62],
        )
    }

    /** Troca o conteúdo do arquivo (outro tamanho e outra data), como quando o usuário substitui o PDF. */
    private fun replace(file: Path, content: DocumentContent) {
        Files.writeString(file, Files.readString(file) + "\n% corrigido")
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().plusSeconds(60)))
        reader.contents[file.fileName.toString()] = content
    }

    private companion object {
        const val ELVES = "Elves Lincoln Menezes de Lima"
        const val ANDREY = "Andrey Alves da Costa"
    }

    private class FakeReader : DocumentContentReader {
        val contents = mutableMapOf<String, DocumentContent>()
        var failure: FileOperationError? = null
        var reads = 0

        override suspend fun read(file: Path, isEnough: (DocumentContent) -> Boolean): Outcome<DocumentContent> {
            reads++
            failure?.let { return Outcome.Failure(it) }
            return Outcome.Success(contents[file.fileName.toString()] ?: DocumentContent(emptyList()))
        }

        override suspend fun signerNames(file: Path): Outcome<List<String>> = Outcome.Success(emptyList())
    }

    private class InMemoryExpenseChecks : ExpenseCheckRepository {
        private val saved = mutableMapOf<Path, StoredExpenseCheck>()

        override suspend fun checksIn(folder: Path): Map<String, StoredExpenseCheck> =
            saved.filterKeys { it.parent == folder }.mapKeys { it.key.fileName.toString().lowercase() }

        override suspend fun save(file: Path, check: StoredExpenseCheck) {
            saved[file] = check
        }
    }
}
