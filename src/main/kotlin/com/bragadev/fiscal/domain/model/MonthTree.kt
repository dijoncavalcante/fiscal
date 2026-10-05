package com.bragadev.fiscal.domain.model

import java.nio.file.Path

/**
 * Estrutura de pastas do pendrive a partir da pasta raiz das contas:
 * conta → ano de serviço → trimestre → mês. Só entram pastas que levam a um mês reconhecido.
 */
data class MonthTree(val root: Path, val accounts: List<AccountFolder>)

data class AccountFolder(val account: AccountType, val path: Path, val serviceYears: List<ServiceYearFolder>)

data class ServiceYearFolder(val name: String, val path: Path, val quarters: List<QuarterFolder>)

data class QuarterFolder(val name: String, val path: Path, val months: List<MonthFolderInfo>)
