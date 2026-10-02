package com.bragadev.fiscal.domain.repository

import com.bragadev.fiscal.domain.model.Document
import com.bragadev.fiscal.domain.model.Outcome
import java.nio.file.Path

/** Acesso ao sistema de arquivos. Única porta de entrada para ler, mover e renomear arquivos. */
interface FileRepository {
    suspend fun exists(path: Path): Boolean

    suspend fun isDirectory(path: Path): Boolean

    /** Verifica extensão e assinatura "%PDF-" do arquivo. */
    suspend fun isPdf(path: Path): Boolean

    /** Nomes dos arquivos existentes na pasta. Retorna vazio se a pasta não existir. */
    suspend fun listFileNames(directory: Path): Set<String>

    /** Lista os PDFs da pasta, sem entrar nas subpastas. Outros arquivos são ignorados. */
    suspend fun listPdfFiles(folder: Path): Outcome<List<Document>>

    /**
     * Move (ou renomeia) um arquivo criando as pastas de destino quando necessário.
     * Nunca sobrescreve: falha com DestinationAlreadyExists se o destino existir.
     */
    suspend fun move(source: Path, target: Path): Outcome<Unit>
}
