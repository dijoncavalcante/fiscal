package com.bragadev.fiscal.domain.usecase

import com.bragadev.fiscal.domain.model.Outcome
import com.bragadev.fiscal.domain.model.SignatureRole
import com.bragadev.fiscal.domain.repository.DocumentContentReader
import com.bragadev.fiscal.domain.repository.SignatureImageStorage
import java.nio.file.Path

/** Nomes nos certificados de um PDF assinado, para escolher o responsável sem digitar (Configurações). */
class ReadPdfSignersUseCase(private val reader: DocumentContentReader) {
    suspend operator fun invoke(file: Path): Outcome<List<String>> = reader.signerNames(file)
}

/** Imagem de referência do selo de assinatura de cada responsável, guardada só na pasta de dados do usuário. */
class SignatureImageUseCase(private val storage: SignatureImageStorage) {
    suspend fun load(role: SignatureRole): ByteArray? = storage.load(role)

    suspend fun save(role: SignatureRole, source: Path): Outcome<Unit> = storage.save(role, source)

    suspend fun remove(role: SignatureRole): Outcome<Unit> = storage.remove(role)
}
