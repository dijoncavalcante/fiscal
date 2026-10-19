package com.bragadev.fiscal.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.domain.model.ImagePage
import com.bragadev.fiscal.presentation.closing.MonthClosingDialogHost
import com.bragadev.fiscal.presentation.closing.MonthClosingViewModel
import com.bragadev.fiscal.presentation.common.RegisterShortcuts
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.common.UserMessage
import com.bragadev.fiscal.presentation.components.*
import com.bragadev.fiscal.presentation.documentactions.DocumentActionsDialogHost
import com.bragadev.fiscal.presentation.documentactions.DocumentActionsViewModel
import com.bragadev.fiscal.presentation.monthfiles.MonthFilesDialogHost
import com.bragadev.fiscal.presentation.monthfiles.MonthFilesViewModel
import com.bragadev.fiscal.presentation.navigator.MonthNavigator
import com.bragadev.fiscal.presentation.navigator.MonthNavigatorViewModel
import com.bragadev.fiscal.presentation.organizer.*
import com.bragadev.fiscal.presentation.pdftools.PdfToolsPanel
import com.bragadev.fiscal.presentation.pdftools.PdfToolsViewModel
import com.bragadev.fiscal.presentation.preview.PdfPreviewScreen
import com.bragadev.fiscal.presentation.preview.PdfPreviewViewModel
import java.nio.file.Path
import java.util.*

private val WIDE_LAYOUT_MIN_WIDTH = 1100.dp

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    previewViewModel: PdfPreviewViewModel,
    organizerViewModel: OrganizerViewModel,
    monthFilesViewModel: MonthFilesViewModel,
    navigatorViewModel: MonthNavigatorViewModel,
    pdfToolsViewModel: PdfToolsViewModel,
    monthClosingViewModel: MonthClosingViewModel,
    documentActionsViewModel: DocumentActionsViewModel,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    appVersion: String = "",
    onOpenAbout: () -> Unit = {},
) {
    val pdfTools by pdfToolsViewModel.uiState.collectAsState()
    val navigator by navigatorViewModel.uiState.collectAsState()
    val home by homeViewModel.uiState.collectAsState()
    val organizer by organizerViewModel.uiState.collectAsState()
    val monthFiles by monthFilesViewModel.uiState.collectAsState()
    val closing by monthClosingViewModel.uiState.collectAsState()
    val documentActions by documentActionsViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    MessageEffect(organizer.message, snackbarHostState, organizerViewModel::undo, organizerViewModel::onMessageShown)
    MessageEffect(monthFiles.message, snackbarHostState, organizerViewModel::undo, monthFilesViewModel::onMessageShown)
    MessageEffect(pdfTools.message, snackbarHostState, organizerViewModel::undo, pdfToolsViewModel::onMessageShown)
    MessageEffect(documentActions.message, snackbarHostState, organizerViewModel::undo, documentActionsViewModel::onMessageShown)
    ErrorEffect(home.error, snackbarHostState, homeViewModel::onErrorShown)
    ErrorEffect(navigator.error, snackbarHostState, navigatorViewModel::onErrorShown)
    ErrorEffect(closing.error, snackbarHostState, monthClosingViewModel::onErrorShown)

    val canUndo = organizer.lastUndoableOperationId != null && !organizer.isWorking
    // Com um diálogo aberto os atalhos ficam parados: Ctrl+Z ali não pode desfazer outra coisa por trás.
    val dialogOpen = organizer.dialog != null || monthFiles.dialog != null || closing.dialog != null || documentActions.dialog != null
    RegisterShortcuts(
        onUndo = { if (canUndo && !dialogOpen) organizerViewModel.undo() },
        onRefresh = { if (!dialogOpen) homeViewModel.onRefresh() },
    )

    Scaffold(
        topBar = {
            TopBar(
                canUndo = canUndo,
                onRefresh = homeViewModel::onRefresh,
                onUndo = { organizerViewModel.undo() },
                onOpenSettings = onOpenSettings,
                onOpenHistory = onOpenHistory,
                onImagesToPdf = pdfToolsViewModel::onOpenImagesToPdf,
                onMergePdfs = { pdfToolsViewModel.onOpenMerge(home.selectedDocument?.path) },
            )
        },
        bottomBar = { StatusBar(home, appVersion, onOpenAbout) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val toolPanel = pdfTools.dialog?.let { dialog ->
                @Composable { modifier: Modifier ->
                    PdfToolsPanel(dialog, pdfTools.isWorking, pdfToolsViewModel, homeViewModel::onPreviewFile, modifier)
                }
            }
            Workspace(
                home, organizer, previewViewModel, homeViewModel, organizerViewModel, monthFilesViewModel,
                documentActionsViewModel, toolPanel,
                onConcludeMonth = monthClosingViewModel::onOpen,
            ) {
                MonthNavigator(
                    state = navigator,
                    viewModel = navigatorViewModel,
                    monthFolder = organizer.monthFolder,
                    onEditMonthFolder = {
                        val initial = organizerViewModel.currentMonthFolder ?: homeViewModel.currentSourceFolder
                        pickFolder(Strings.MONTH_FOLDER_PICKER_TITLE, initial)?.let(organizerViewModel::onMonthFolderSelected)
                    },
                    onEditRoot = {
                        pickFolder(Strings.MONTHS_ROOT_PICKER_TITLE, navigatorViewModel.currentRoot)
                            ?.let(navigatorViewModel::onRootSelected)
                    },
                    onOpenFolder = DesktopActions::openFolder,
                )
            }
            if (organizer.isWorking || monthFiles.isWorking || documentActions.isWorking) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
    OrganizerDialogHost(organizer, organizerViewModel)
    MonthFilesDialogHost(monthFiles, monthFilesViewModel)
    DocumentActionsDialogHost(documentActions, documentActionsViewModel)
    MonthClosingDialogHost(closing, monthClosingViewModel)
}

@Composable
private fun Workspace(
    home: HomeUiState,
    organizer: OrganizerUiState,
    previewViewModel: PdfPreviewViewModel,
    homeViewModel: HomeViewModel,
    organizerViewModel: OrganizerViewModel,
    monthFilesViewModel: MonthFilesViewModel,
    documentActionsViewModel: DocumentActionsViewModel,
    /** Painel que ocupa o lugar do mês em edição enquanto estiver aberto (ex.: ferramentas de PDF). */
    toolPanel: (@Composable (Modifier) -> Unit)?,
    onConcludeMonth: (Path) -> Unit,
    navigator: @Composable () -> Unit,
) {
    val selectedPath = home.selectedDocument?.path
    val fileActions = MonthFileActions(
        onPreview = homeViewModel::onPreviewFile,
        // Arquivo com categoria segue a regra de nome dela; sem número, renomeia o nome livre (como na lista).
        onRename = { file, category ->
            if (category != null) monthFilesViewModel.onRenameRequested(file, category)
            else documentActionsViewModel.onRenameRequested(file)
        },
        onCopy = documentActionsViewModel::onCopyRequested,
        onDelete = { file -> documentActionsViewModel.onDeleteRequested(file) },
        onRemove = monthFilesViewModel::onRemoveRequested,
        onFlag = monthFilesViewModel::onFlagRequested,
        onClearFlag = monthFilesViewModel::onFlagCleared,
        onSendWhatsApp = documentActionsViewModel::onSendWhatsAppRequested,
    )
    val documentActions = DocumentActions(
        onRename = documentActionsViewModel::onRenameRequested,
        onCopy = documentActionsViewModel::onCopyRequested,
        onDelete = { file -> documentActionsViewModel.onDeleteRequested(file, homeViewModel.selectionAfterRemoving(file)) },
        onSendWhatsApp = documentActionsViewModel::onSendWhatsAppRequested,
    )
    // Painéis recolhidos para a borda (só no layout largo); o preview ganha o espaço.
    var documentsCollapsed by rememberSaveable { mutableStateOf(false) }
    var monthCollapsed by rememberSaveable { mutableStateOf(false) }
    val documents: @Composable (Modifier, PanelCollapse?) -> Unit = { modifier, collapse ->
        DocumentList(
            state = home,
            onSelect = homeViewModel::onDocumentSelected,
            actions = documentActions,
            onRefresh = homeViewModel::onRefresh,
            onSortChanged = homeViewModel::onSortChanged,
            onQueryChanged = homeViewModel::onQueryChanged,
            onChangeFolder = {
                pickFolder(Strings.SOURCE_FOLDER_PICKER_TITLE, homeViewModel.currentSourceFolder)
                    ?.let(homeViewModel::onSourceFolderSelected)
            },
            modifier = modifier,
            onOpenInExplorer = { homeViewModel.currentSourceFolder?.let(DesktopActions::openFolder) },
            collapse = collapse,
        )
    }
    val categories: @Composable (Modifier, PanelCollapse?) -> Unit = { modifier, collapse ->
        if (toolPanel != null) {
            toolPanel(modifier)
        } else {
            OrganizerScreen(
                state = organizer,
                selectedDocument = selectedPath,
                onDrop = organizerViewModel::onFilesDropped,
                onCategoryClickedWithoutDocument = organizerViewModel::onCategoryClickedWithoutDocument,
                fileActions = fileActions,
                navigator = navigator,
                onConcludeMonth = onConcludeMonth,
                modifier = modifier,
                collapse = collapse,
            )
        }
    }
    val preview: @Composable (Modifier) -> Unit = { modifier ->
        // Imagens (JPEG/PNG) aparecem no preview para o usuário conferir antes de converter.
        if (home.selectedDocument?.isImage == true) {
            Panel(title = Strings.PREVIEW, modifier = modifier) {
                ImageThumbnail(ImagePage(home.selectedDocument.path), Modifier.weight(1f).fillMaxWidth())
            }
        } else {
            PdfPreviewScreen(previewViewModel, selectedPath, modifier)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp)) {
        if (maxWidth >= WIDE_LAYOUT_MIN_WIDTH) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (documentsCollapsed) {
                    CollapsedPanel(Strings.DOCUMENTS, PanelSide.LEFT, onExpand = { documentsCollapsed = false })
                } else {
                    documents(
                        Modifier.weight(0.25f).fillMaxHeight(),
                        PanelCollapse(PanelSide.LEFT) { documentsCollapsed = true })
                }
                preview(Modifier.weight(0.43f).fillMaxHeight())
                // As ferramentas de PDF ocupam o lugar do mês e ficam sempre abertas (não têm botão de recolher).
                if (monthCollapsed && toolPanel == null) {
                    CollapsedPanel(Strings.MONTH_PANEL, PanelSide.RIGHT, onExpand = { monthCollapsed = false })
                } else {
                    categories(
                        Modifier.weight(0.32f).fillMaxHeight(),
                        if (toolPanel == null) PanelCollapse(PanelSide.RIGHT) { monthCollapsed = true } else null,
                    )
                }
            }
        } else {
            // Nas abas da janela estreita não há o que recolher.
            CompactWorkspace({ documents(it, null) }, { categories(it, null) }, preview)
        }
    }
}

/** Em janelas estreitas, documentos e mês em edição dividem a mesma coluna em abas. */
@Composable
private fun CompactWorkspace(
    documents: @Composable (Modifier) -> Unit,
    categories: @Composable (Modifier) -> Unit,
    preview: @Composable (Modifier) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(0.4f).fillMaxHeight()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(Strings.DOCUMENTS) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(Strings.MONTH_PANEL) })
            }
            val panelModifier = Modifier.weight(1f).fillMaxWidth()
            if (tab == 0) documents(panelModifier) else categories(panelModifier)
        }
        preview(Modifier.weight(0.6f).fillMaxHeight())
    }
}

@Composable
private fun TopBar(
    canUndo: Boolean,
    onRefresh: () -> Unit,
    onUndo: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onImagesToPdf: () -> Unit,
    onMergePdfs: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                Strings.APP_TITLE,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            PdfMenu(onImagesToPdf, onMergePdfs)
            TopBarButton(AppIcons.Undo, Strings.UNDO_LAST, Strings.UNDO_SHORTCUT, onUndo, enabled = canUndo)
            TopBarButton(AppIcons.Refresh, Strings.REFRESH, Strings.REFRESH_SHORTCUT, onRefresh)
            TopBarButton(AppIcons.History, Strings.HISTORY, Strings.HISTORY_SHORTCUT, onOpenHistory)
            TopBarButton(AppIcons.Settings, Strings.SETTINGS, null, onOpenSettings)
        }
    }
}

/** Botão da barra superior com ícone; a dica mostra o atalho de teclado, quando houver. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBarButton(icon: ImageVector, label: String, shortcut: String?, onClick: () -> Unit, enabled: Boolean = true) {
    val button = @Composable {
        TextButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, Modifier.padding(start = 6.dp))
        }
    }
    if (shortcut == null) return button()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(shortcut) } },
        state = rememberTooltipState(),
    ) { button() }
}

/** Menu "PDF": converter imagens em PDF e juntar PDFs. */
@Composable
private fun PdfMenu(onImagesToPdf: () -> Unit, onMergePdfs: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(AppIcons.Document, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(Strings.PDF_MENU, Modifier.padding(start = 6.dp))
            Icon(AppIcons.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(Strings.IMAGES_TO_PDF) }, onClick = { open = false; onImagesToPdf() })
            DropdownMenuItem(text = { Text(Strings.MERGE_PDFS) }, onClick = { open = false; onMergePdfs() })
        }
    }
}

@Composable
private fun StatusBar(home: HomeUiState, appVersion: String, onOpenAbout: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val text = if (home.isLoading) Strings.LOADING else Strings.statusDocuments(home.documents.size)
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            // Versão sempre à vista: clicar abre "Sobre".
            if (appVersion.isNotBlank()) {
                Text(
                    Strings.statusVersion(appVersion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onOpenAbout),
                )
            }
        }
    }
}

@Composable
private fun MessageEffect(
    message: UserMessage?,
    snackbarHostState: SnackbarHostState,
    onUndo: (UUID?) -> Unit,
    onShown: () -> Unit,
) {
    LaunchedEffect(message?.id) {
        message ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message.text,
            actionLabel = message.undoOperationId?.let { Strings.UNDO },
            withDismissAction = true,
            duration = if (message.undoOperationId != null) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) onUndo(message.undoOperationId)
        onShown()
    }
}

@Composable
private fun ErrorEffect(error: String?, snackbarHostState: SnackbarHostState, onShown: () -> Unit) {
    LaunchedEffect(error) {
        error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = error, withDismissAction = true)
        onShown()
    }
}
