package com.bragadev.fiscal.data

import com.bragadev.fiscal.data.database.Database
import com.bragadev.fiscal.data.database.ExpenseCheckDao
import com.bragadev.fiscal.data.repository.ExpenseCheckRepositoryImpl
import com.bragadev.fiscal.domain.model.*
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals

/** Conferências gravadas no SQLite: por pasta + nome, sem diferenciar maiúsculas. */
class ExpenseCheckDaoTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `grava e le de volta a conferencia do arquivo`() = runTest {
        Database(temp.root.toPath().resolve("fiscal.db")).use { database ->
            val repository = ExpenseCheckRepositoryImpl(ExpenseCheckDao(database))
            val folder = temp.newFolder("1. Setembro").toPath()
            val modified = Instant.parse("2026-09-19T13:55:34.123456Z")
            val missing = StoredExpenseCheck(
                10,
                modified,
                ExpenseCheck(setOf(ExpenseRequirement.SIGNATURE, ExpenseRequirement.FISCAL_DOCUMENT))
            )
            val unreadable = StoredExpenseCheck(20, modified, ExpenseCheck(unreadable = true))

            repository.save(folder.resolve("3. Despesa - Dreno.pdf"), missing)
            repository.save(folder.resolve("3.1 Despesa - Cabo.pdf"), unreadable)
            // Gravar de novo substitui.
            repository.save(folder.resolve("3.1 DESPESA - CABO.pdf"), unreadable.copy(sizeBytes = 21))

            val saved = repository.checksIn(folder)
            assertEquals(missing.check, saved.getValue("3. despesa - dreno.pdf").check)
            assertEquals(modified.toEpochMilli(), saved.getValue("3. despesa - dreno.pdf").lastModified.toEpochMilli())
            assertEquals(21, saved.getValue("3.1 despesa - cabo.pdf").sizeBytes)
            assertEquals(true, saved.getValue("3.1 despesa - cabo.pdf").check.unreadable)
            assertEquals(2, saved.size)
        }
    }

    @Test
    fun `grava a diferenca de valor e a versao das regras`() = runTest {
        Database(temp.root.toPath().resolve("fiscal.db")).use { database ->
            val repository = ExpenseCheckRepositoryImpl(ExpenseCheckDao(database))
            val folder = temp.newFolder("1. Agosto").toPath()
            val modified = Instant.parse("2026-08-11T17:44:00Z")
            val withTotal = ExpenseCheck(amountMismatch = AmountMismatch(BigDecimal("405.98"), BigDecimal("395.98")))
            val withoutTotal = ExpenseCheck(amountMismatch = AmountMismatch(BigDecimal("60.00"), null))

            repository.save(folder.resolve("a.pdf"), StoredExpenseCheck(1, modified, withTotal))
            repository.save(folder.resolve("b.pdf"), StoredExpenseCheck(1, modified, withoutTotal, rulesVersion = 1))

            val saved = repository.checksIn(folder)
            assertEquals(withTotal, saved.getValue("a.pdf").check)
            assertEquals(EXPENSE_RULES_VERSION, saved.getValue("a.pdf").rulesVersion)
            assertEquals(withoutTotal, saved.getValue("b.pdf").check)
            assertEquals(1, saved.getValue("b.pdf").rulesVersion)
        }
    }
}
