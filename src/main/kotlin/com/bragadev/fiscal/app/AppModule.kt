package com.bragadev.fiscal.app

import com.bragadev.fiscal.data.AppDirectories
import com.bragadev.fiscal.data.database.CategoryDao
import com.bragadev.fiscal.data.database.Database
import com.bragadev.fiscal.data.database.DocumentDao
import com.bragadev.fiscal.data.database.FileOperationDao
import com.bragadev.fiscal.data.database.SettingsDao
import com.bragadev.fiscal.data.filesystem.BackupStorageImpl
import com.bragadev.fiscal.data.filesystem.FileRepositoryImpl
import com.bragadev.fiscal.data.pdf.PdfRepositoryImpl
import com.bragadev.fiscal.data.repository.CategoryRepositoryImpl
import com.bragadev.fiscal.data.repository.DocumentRepositoryImpl
import com.bragadev.fiscal.data.repository.OperationHistoryRepositoryImpl
import com.bragadev.fiscal.data.settings.SettingsRepositoryImpl
import com.bragadev.fiscal.domain.repository.BackupStorage
import com.bragadev.fiscal.domain.repository.CategoryRepository
import com.bragadev.fiscal.domain.repository.DocumentRepository
import com.bragadev.fiscal.domain.repository.FileRepository
import com.bragadev.fiscal.domain.repository.OperationHistoryRepository
import com.bragadev.fiscal.domain.repository.PdfRepository
import com.bragadev.fiscal.domain.repository.SettingsRepository
import com.bragadev.fiscal.domain.usecase.ChangeRootFolderUseCase
import com.bragadev.fiscal.domain.usecase.GetCategoryTreeUseCase
import com.bragadev.fiscal.domain.usecase.ObserveSettingsUseCase
import com.bragadev.fiscal.domain.usecase.OrganizeDocumentUseCase
import com.bragadev.fiscal.domain.usecase.PlanOrganizationUseCase
import com.bragadev.fiscal.domain.usecase.PreviewPdfUseCase
import com.bragadev.fiscal.domain.usecase.ResolveRootFolderUseCase
import com.bragadev.fiscal.domain.usecase.ScanDocumentsUseCase
import com.bragadev.fiscal.domain.usecase.UndoOperationUseCase
import com.bragadev.fiscal.domain.usecase.UpdateSettingsUseCase
import com.bragadev.fiscal.presentation.common.DocumentChangeNotifier
import com.bragadev.fiscal.presentation.home.HomeViewModel
import com.bragadev.fiscal.presentation.organizer.OrganizerViewModel
import com.bragadev.fiscal.presentation.preview.PdfPreviewViewModel
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
    single<FileRepository> { FileRepositoryImpl() }
    single<PdfRepository> { PdfRepositoryImpl() }
    single<BackupStorage> { BackupStorageImpl(get()) }
    single<CategoryRepository> { CategoryRepositoryImpl(get()) }
    single<DocumentRepository> { DocumentRepositoryImpl(get()) }
    single<OperationHistoryRepository> { OperationHistoryRepositoryImpl(get()) }
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }

    // Domain
    factory { ScanDocumentsUseCase(get()) }
    factory { ResolveRootFolderUseCase(get(), get()) }
    factory { ChangeRootFolderUseCase(get(), get()) }
    factory { ObserveSettingsUseCase(get()) }
    factory { UpdateSettingsUseCase(get()) }
    factory { GetCategoryTreeUseCase(get()) }
    factory { PlanOrganizationUseCase(get(), get(), get()) }
    factory { OrganizeDocumentUseCase(get(), get(), get(), get(), get()) }
    factory { UndoOperationUseCase(get(), get(), get()) }
    factory { PreviewPdfUseCase(get()) }

    // Presentation
    single { DocumentChangeNotifier() }
    single { HomeViewModel(get(), get(), get(), get(), get()) }
    single { PdfPreviewViewModel(get()) }
    single { OrganizerViewModel(get(), get(), get(), get(), get(), get()) }
    single { SettingsViewModel(get(), get(), get()) }
}
