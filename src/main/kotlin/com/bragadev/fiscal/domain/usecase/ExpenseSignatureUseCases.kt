package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.domain.model.AccountType
import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.repository.DocumentContentReader
import com.bragadev.fiscal.domain.repository.SignatureImageStorage
import java.nio.file.Path

/** Nomes nos certificados de um PDF assinado, para escolher o responsável sem digitar (Configurações). */
class ReadPdfSignersUseCase(private val reader: DocumentContentReader) {
    suspend operator fun invoke(file: Path): Outcome<List<String>> = reader.signerNames(file)
}

/** Imagem de referência do selo de assinatura de cada conta, guardada só na pasta de dados do usuário. */
class SignatureImageUseCase(private val storage: SignatureImageStorage) {
    suspend fun load(account: AccountType): ByteArray? = storage.load(account)

    suspend fun save(account: AccountType, source: Path): Outcome<Unit> = storage.save(account, source)

    suspend fun remove(account: AccountType): Outcome<Unit> = storage.remove(account)
}
