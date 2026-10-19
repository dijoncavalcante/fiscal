package com.bragadev.fiscal.app

import com.bragadev.fiscal.data.AppDirectories
import com.bragadev.fiscal.data.database.*
import com.bragadev.fiscal.data.filesystem.BackupStorageImpl
import com.bragadev.fiscal.data.filesystem.FileRepositoryImpl
import com.bragadev.fiscal.data.logging.DiagnosticsRepositoryImpl
import com.bragadev.fiscal.data.maintenance.DataMaintenanceRepositoryImpl
import com.bragadev.fiscal.data.pdf.PdfContentReaderImpl
import com.bragadev.fiscal.data.pdf.PdfRepositoryImpl
import com.bragadev.fiscal.data.pdf.PdfToolsRepositoryImpl
import com.bragadev.fiscal.data.repository.*
import com.bragadev.fiscal.data.settings.SettingsRepositoryImpl
import com.bragadev.fiscal.domain.repository.*
import com.bragadev.fiscal.domain.rules.EditablePeriodPolicy
import com.bragadev.fiscal.domain.usecase.*
import com.bragadev.fiscal.presentation.about.AboutInfo
import com.bragadev.fiscal.presentation.closing.MonthClosingViewModel
import com.bragadev.fiscal.presentation.common.DocumentChangeNotifier
import com.bragadev.fiscal.presentation.documentactions.DocumentActionsViewModel
import com.bragadev.fiscal.presentation.history.HistoryViewModel
import com.bragadev.fiscal.presentation.home.HomeViewModel
import com.bragadev.fiscal.presentation.monthfiles.MonthFilesViewModel
import com.bragadev.fiscal.presentation.navigator.MonthNavigatorViewModel
import com.bragadev.fiscal.presentation.onboarding.OnboardingViewModel
import com.bragadev.fiscal.presentation.organizer.OrganizerViewModel
import com.bragadev.fiscal.presentation.pdftools.PdfToolsViewModel
import com.bragadev.fiscal.presentation.preview.PdfPreviewViewModel
import com.bragadev.fiscal.presentation.settings.DataSafetyViewModel
import com.bragadev.fiscal.presentation.settings.SettingsViewModel
import org.koin.dsl.module

fun appModule(directories: AppDirectories) = module {
    single { directories }
    single { Database(directories.databaseFile) }

    // Data
    single { CategoryDao(get()) }
    single { DocumentDao(get()) }
    single { FileOperationDao(get()) }
    single { SettingsDao(get()) }
    single { FileFlagDao(get()) }
    single { ExpenseCheckDao(get()) }
    single<FileRepository> { FileRepositoryImpl() }
    single<PdfRepository> { PdfRepositoryImpl() }
    single<BackupStorage> { BackupStorageImpl(get()) }
    single<CategoryRepository> { CategoryRepositoryImpl(get()) }
    single<DocumentRepository> { DocumentRepositoryImpl(get()) }
    single<OperationHistoryRepository> { OperationHistoryRepositoryImpl(get()) }
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }
    single<FileFlagRepository> { FileFlagRepositoryImpl(get()) }
    single<ExpenseCheckRepository> { ExpenseCheckRepositoryImpl(get()) }
    // Leitura de texto e assinaturas (com OCR) para conferir despesas; um PDF por vez.
    single<DocumentContentReader> { PdfContentReaderImpl() }
    single<PdfToolsRepository> { PdfToolsRepositoryImpl() }
    single<DataMaintenanceRepository> { DataMaintenanceRepositoryImpl(get(), get()) }
    single<DiagnosticsRepository> { DiagnosticsRepositoryImpl(AppInfo.NAME, AppInfo.FULL_VERSION) }
    single {
        AboutInfo(
            name = AppInfo.NAME,
            version = AppInfo.VERSION,
            buildDate = AppInfo.BUILD_DATE,
            commit = AppInfo.COMMIT,
            javaVersion = "${System.getProperty("java.version")} (${System.getProperty("java.vendor")})",
            windowsVersion = "${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})",
            dataFolder = directories.dataDirectory,
            logFolder = directories.logDirectory,
        )
    }

    // Domain
    factory { ScanDocumentsUseCase(get()) }
    // Mês de corte das Configurações, lido a cada verificação.
    single { EditablePeriodPolicy { get<SettingsRepository>().settings.value.firstEditableMonth } }
    factory { LoadInitialFoldersUseCase(get(), get()) }
    factory { ChangeSourceFolderUseCase(get(), get()) }
    factory { ChangeMonthFolderUseCase(get(), get(), get()) }
    factory { DescribeMonthFolderUseCase(get()) }
    factory { ObserveSettingsUseCase(get()) }
    factory { UpdateSettingsUseCase(get()) }
    factory { GetCategoryTreeUseCase(get()) }
    factory { GetMonthChecklistUseCase(get(), get(), get()) }
    factory { CheckExpensesUseCase(get(), get(), get(), get()) }
    factory { PlanOrganizationUseCase(get(), get(), get(), get()) }
    factory { RecordedFileMover(get(), get(), get(), get()) }
    factory { OrganizeDocumentUseCase(get(), get(), get(), get(), get()) }
    factory { UndoOperationUseCase(get(), get(), get(), get(), get()) }
    factory { ListHistoryUseCase(get(), get()) }
    factory { ReviewMonthUseCase(get(), get(), get(), get()) }
    factory { GenerateMonthReportUseCase(get(), get()) }
    factory { PlanRenameUseCase(get(), get(), get()) }
    factory { RemoveFromMonthUseCase(get(), get(), get(), get()) }
    factory { RenameDocumentUseCase(get(), get(), get()) }
    factory { DeleteDocumentUseCase(get(), get(), get(), get()) }
    factory { FileFlagUseCase(get()) }
    factory { WatchFolderUseCase(get()) }
    factory { ResolveMonthsRootUseCase(get(), get()) }
    factory { ChangeMonthsRootUseCase(get(), get()) }
    factory { BrowseMonthFoldersUseCase(get(), get()) }
    factory { PdfOutputResolver(get(), get()) }
    factory { CreatePdfFromImagesUseCase(get(), get(), get()) }
    factory { MergePdfsUseCase(get(), get(), get()) }
    factory { ReadImageUseCase(get()) }
    factory { GetBackupStatsUseCase(get()) }
    factory { CleanOldBackupsUseCase(get(), get()) }
    factory { ExportDataUseCase(get()) }
    factory { ImportDataUseCase(get()) }
    factory { PreviewPdfUseCase(get()) }

    // Presentation
    single { DocumentChangeNotifier() }
    single { HomeViewModel(get(), get(), get(), get(), get(), get(), get()) }
    single { PdfPreviewViewModel(get()) }
    single { OrganizerViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { MonthFilesViewModel(get(), get(), get(), get(), get(), get(), get()) }
    single { DocumentActionsViewModel(get(), get(), get()) }
    single { MonthNavigatorViewModel(get(), get(), get(), get(), get(), get()) }
    single { PdfToolsViewModel(get(), get(), get(), get()) }
    single { SettingsViewModel(get(), get(), get(), get(), get()) }
    single { HistoryViewModel(get(), get(), get()) }
    single { MonthClosingViewModel(get(), get(), get(), get()) }
    single { OnboardingViewModel(get()) }
    single { DataSafetyViewModel(get(), get(), get(), get(), get(), get(), get()) }
}
