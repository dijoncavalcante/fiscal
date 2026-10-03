package com.bragadev.fiscal.domain.repository

import java.nio.file.Path

/**
 * Pendências marcadas pelo usuário em arquivos (ex.: "arquivo errado, trocar").
 * Guardadas só no banco local; o arquivo em disco não é alterado.
 */
interface FileFlagRepository {
    /** Pendências dos arquivos da pasta, por nome de arquivo em minúsculas. */
    suspend fun flagsIn(folder: Path): Map<String, String>

    suspend fun set(file: Path, note: String)

    suspend fun clear(file: Path)

    /** Acompanha o arquivo quando ele é renomeado ou movido pelo app. */
    suspend fun move(from: Path, to: Path)
}
