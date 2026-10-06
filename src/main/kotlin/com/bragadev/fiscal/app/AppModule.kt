package com.bragadev.fiscal.app

import com.bragadev.fiscal.data.AppDirectories
import com.bragadev.fiscal.data.database.CategoryDao
import com.bragadev.fiscal.data.database.Database
import com.bragadev.fiscal.data.database.FileFlagDao
import com.bragadev.fiscal.data.database.DocumentDao
import com.bragadev.fiscal.data.database.FileOperationDao
import com.bragadev.fiscal.data.database.SettingsDao
import com.bragadev.fiscal.data.filesystem.BackupStorageImpl
import com.bragadev.fiscal.data.filesystem.FileRepositoryImpl
import com.bragadev.fiscal.data.logging.DiagnosticsRepositoryImpl
import com.bragadev.fiscal.data.maintenance.DataMaintenanceRepositoryImpl
import com.bragadev.fiscal.data.pdf.PdfRepositoryImpl
import com.bragadev.fiscal.data.pdf.PdfToolsRepositoryImpl
import com.bragadev.fiscal.data.repository.CategoryRepositoryImpl
import com.bragadev.fiscal.data.repository.DocumentRepositoryImpl
import com.bragadev.fiscal.data.repository.FileFlagRepositoryImpl
import com.bragadev.fiscal.data.repository.OperationHistoryRepositoryImpl
import com.bragadev.fiscal.data.settings.SettingsRepositoryImpl
import com.bragadev.fiscal.domain.repository.BackupStorage
import com.bragadev.fiscal.domain.repository.CategoryRepository
import com.bragadev.fiscal.domain.repository.DataMaintenanceRepository
import com.bragadev.fiscal.domain.repository.DiagnosticsRepository
import com.bragadev.fiscal.domain.repository.DocumentRepository
import com.bragadev.fiscal.domain.repository.FileFlagRepository
import com.bragadev.fiscal.domain.repository.FileRepository
import com.bragadev.fiscal.domain.repository.OperationHistoryRepository
import com.bragadev.fiscal.domain.repository.PdfRepository
import com.bragadev.fiscal.domain.repository.PdfToolsRepository
import com.bragadev.fiscal.domain.repository.SettingsRepository
import com.bragadev.fiscal.domain.rules.EditablePeriodPolicy
import com.bragadev.fiscal.domain.usecase.BrowseMonthFoldersUseCase
import com.bragadev.fiscal.domain.usecase.ChangeMonthFolderUseCase
import com.bragadev.fiscal.domain.usecase.ChangeMonthsRootUseCase
import com.bragadev.fiscal.domain.usecase.ChangeSourceFolderUseCase
import com.bragadev.fiscal.domain.usecase.CleanOldBackupsUseCase
import com.bragadev.fiscal.domain.usecase.CreatePdfFromImagesUseCase
import com.bragadev.fiscal.domain.usecase.DescribeMonthFolderUseCase
import com.bragadev.fiscal.domain.usecase.ExportDataUseCase
import com.bragadev.fiscal.domain.usecase.FileFlagUseCase
import com.bragadev.fiscal.domain.usecase.GetBackupStatsUseCase
import com.bragadev.fiscal.domain.usecase.GetCategoryTreeUseCase
import com.bragadev.fiscal.domain.usecase.GetMonthChecklistUseCase
import com.bragadev.fiscal.domain.usecase.ObserveSettingsUseCase
import com.bragadev.fiscal.domain.usecase.OrganizeDocumentUseCase
import com.bragadev.fiscal.domain.usecase.PdfOutputResolver
import com.bragadev.fiscal.domain.usecase.PlanOrganizationUseCase
import com.bragadev.fiscal.domain.usecase.PlanRenameUseCase
import com.bragadev.fiscal.domain.usecase.PreviewPdfUseCase
import com.bragadev.fiscal.domain.usecase.ReadImageUseCase
import com.bragadev.fiscal.domain.usecase.RecordedFileMover
import com.bragadev.fiscal.domain.usecase.RemoveFromMonthUseCase
import com.bragadev.fiscal.domain.usecase.ResolveMonthsRootUseCase
import com.bragadev.fiscal.domain.usecase.ImportDataUseCase
import com.bragadev.fiscal.domain.usecase.LoadInitialFoldersUseCase
import com.bragadev.fiscal.domain.usecase.MergePdfsUseCase
import com.bragadev.fiscal.domain.usecase.ScanDocumentsUseCase
import com.bragadev.fiscal.domain.usecase.UndoOperationUseCase
import com.bragadev.fiscal.domain.usecase.UpdateSettingsUseCase
import com.bragadev.fiscal.domain.usecase.WatchFolderUseCase
import com.bragadev.fiscal.presentation.common.DocumentChangeNotifier
import com.bragadev.fiscal.presentation.home.HomeViewModel
import com.bragadev.fiscal.presentation.monthfiles.MonthFilesViewModel
import com.bragadev.fiscal.presentation.navigator.MonthNavigatorViewModel
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
    single<FileRepository> { FileRepositoryImpl() }
    single<PdfRepository> { PdfRepositoryImpl() }
    single<BackupStorage> { BackupStorageImpl(get()) }
    single<CategoryRepository> { CategoryRepositoryImpl(get()) }
    single<DocumentRepository> { DocumentRepositoryImpl(get()) }
    single<OperationHistoryRepository> { OperationHistoryRepositoryImpl(get()) }
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }
    single<FileFlagRepository> { FileFlagRepositoryImpl(get()) }
    single<PdfToolsRepository> { PdfToolsRepositoryImpl() }
    single<DataMaintenanceRepository> { DataMaintenanceRepositoryImpl(get(), get()) }
    single<DiagnosticsRepository> { DiagnosticsRepositoryImpl(AppInfo.NAME, AppInfo.VERSION) }

    // Domain
    factory { ScanDocumentsUseCase(get()) }
    single { EditablePeriodPolicy() }
    factory { LoadInitialFoldersUseCase(get(), get()) }
    factory { ChangeSourceFolderUseCase(get(), get()) }
    factory { ChangeMonthFolderUseCase(get(), get(), get()) }
    factory { DescribeMonthFolderUseCase(get()) }
    factory { ObserveSettingsUseCase(get()) }
    factory { UpdateSettingsUseCase(get()) }
    factory { GetCategoryTreeUseCase(get()) }
    factory { GetMonthChecklistUseCase(get(), get(), get()) }
    factory { PlanOrganizationUseCase(get(), get(), get(), get()) }
    factory { RecordedFileMover(get(), get(), get(), get()) }
    factory { OrganizeDocumentUseCase(get(), get(), get(), get(), get()) }
    factory { UndoOperationUseCase(get(), get(), get(), get(), get()) }
    factory { PlanRenameUseCase(get(), get(), get()) }
    factory { RemoveFromMonthUseCase(get(), get(), get(), get()) }
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
    single { OrganizerViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { MonthFilesViewModel(get(), get(), get(), get(), get(), get(), get()) }
    single { MonthNavigatorViewModel(get(), get(), get(), get(), get(), get()) }
    single { PdfToolsViewModel(get(), get(), get(), get()) }
    single { SettingsViewModel(get(), get(), get(), get(), get()) }
    single { DataSafetyViewModel(get(), get(), get(), get(), get(), get(), get()) }
}
