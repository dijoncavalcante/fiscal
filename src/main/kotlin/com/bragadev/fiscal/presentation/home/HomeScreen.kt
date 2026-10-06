package com.bragadev.fiscal.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.domain.model.ImagePage
import com.bragadev.fiscal.presentation.common.RegisterShortcuts
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.common.UserMessage
import com.bragadev.fiscal.presentation.components.AppIcons
import com.bragadev.fiscal.presentation.components.ImageThumbnail
import com.bragadev.fiscal.presentation.components.Panel
import com.bragadev.fiscal.presentation.components.pickFolder
import com.bragadev.fiscal.presentation.monthfiles.MonthFilesDialogHost
import com.bragadev.fiscal.presentation.monthfiles.MonthFilesViewModel
import com.bragadev.fiscal.presentation.navigator.MonthNavigator
import com.bragadev.fiscal.presentation.navigator.MonthNavigatorViewModel
import com.bragadev.fiscal.presentation.organizer.MonthFileActions
import com.bragadev.fiscal.presentation.organizer.OrganizerDialogHost
import com.bragadev.fiscal.presentation.organizer.OrganizerScreen
import com.bragadev.fiscal.presentation.organizer.OrganizerUiState
import com.bragadev.fiscal.presentation.organizer.OrganizerViewModel
import com.bragadev.fiscal.presentation.pdftools.PdfToolsPanel
import com.bragadev.fiscal.presentation.pdftools.PdfToolsViewModel
import com.bragadev.fiscal.presentation.preview.PdfPreviewScreen
import com.bragadev.fiscal.presentation.preview.PdfPreviewViewModel
import java.util.UUID

private val WIDE_LAYOUT_MIN_WIDTH = 1100.dp

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    previewViewModel: PdfPreviewViewModel,
    organizerViewModel: OrganizerViewModel,
    monthFilesViewModel: MonthFilesViewModel,
    navigatorViewModel: MonthNavigatorViewModel,
    pdfToolsViewModel: PdfToolsViewModel,
    onOpenSettings: () -> Unit,
) {
    val pdfTools by pdfToolsViewModel.uiState.collectAsState()
    val navigator by navigatorViewModel.uiState.collectAsState()
    val home by homeViewModel.uiState.collectAsState()
    val organizer by organizerViewModel.uiState.collectAsState()
    val monthFiles by monthFilesViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    MessageEffect(organizer.message, snackbarHostState, organizerViewModel::undo, organizerViewModel::onMessageShown)
    MessageEffect(monthFiles.message, snackbarHostState, organizerViewModel::undo, monthFilesViewModel::onMessageShown)
    MessageEffect(pdfTools.message, snackbarHostState, organizerViewModel::undo, pdfToolsViewModel::onMessageShown)
    ErrorEffect(home.error, snackbarHostState, homeViewModel::onErrorShown)
    ErrorEffect(navigator.error, snackbarHostState, navigatorViewModel::onErrorShown)

    val canUndo = organizer.lastUndoableOperationId != null && !organizer.isWorking
    // Com um diálogo aberto os atalhos ficam parados: Ctrl+Z ali não pode desfazer outra coisa por trás.
    val dialogOpen = organizer.dialog != null || monthFiles.dialog != null
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
                onImagesToPdf = pdfToolsViewModel::onOpenImagesToPdf,
                onMergePdfs = { pdfToolsViewModel.onOpenMerge(home.selectedDocument?.path) },
            )
        },
        bottomBar = { StatusBar(home) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val toolPanel = pdfTools.dialog?.let { dialog ->
                @Composable { modifier: Modifier ->
                    PdfToolsPanel(dialog, pdfTools.isWorking, pdfToolsViewModel, homeViewModel::onPreviewFile, modifier)
                }
            }
            Workspace(home, organizer, previewViewModel, homeViewModel, organizerViewModel, monthFilesViewModel, toolPanel) {
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
                )
            }
            if (organizer.isWorking || monthFiles.isWorking) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
    OrganizerDialogHost(organizer, organizerViewModel)
    MonthFilesDialogHost(monthFiles, monthFilesViewModel)
}

@Composable
private fun Workspace(
    home: HomeUiState,
    organizer: OrganizerUiState,
    previewViewModel: PdfPreviewViewModel,
    homeViewModel: HomeViewModel,
    organizerViewModel: OrganizerViewModel,
    monthFilesViewModel: MonthFilesViewModel,
    /** Painel que ocupa o lugar do mês em edição enquanto estiver aberto (ex.: ferramentas de PDF). */
    toolPanel: (@Composable (Modifier) -> Unit)?,
    navigator: @Composable () -> Unit,
) {
    val selectedPath = home.selectedDocument?.path
    val fileActions = MonthFileActions(
        onPreview = homeViewModel::onPreviewFile,
        onRename = monthFilesViewModel::onRenameRequested,
        onRemove = monthFilesViewModel::onRemoveRequested,
        onFlag = monthFilesViewModel::onFlagRequested,
        onClearFlag = monthFilesViewModel::onFlagCleared,
    )
    val documents: @Composable (Modifier) -> Unit = { modifier ->
        DocumentList(
            state = home,
            onSelect = homeViewModel::onDocumentSelected,
            onRefresh = homeViewModel::onRefresh,
            onSortChanged = homeViewModel::onSortChanged,
            onQueryChanged = homeViewModel::onQueryChanged,
            onChangeFolder = {
                pickFolder(Strings.SOURCE_FOLDER_PICKER_TITLE, homeViewModel.currentSourceFolder)
                    ?.let(homeViewModel::onSourceFolderSelected)
            },
            modifier = modifier,
        )
    }
    val categories: @Composable (Modifier) -> Unit = toolPanel ?: { modifier ->
        OrganizerScreen(
            state = organizer,
            selectedDocument = selectedPath,
            onDrop = organizerViewModel::onFilesDropped,
            onCategoryClickedWithoutDocument = organizerViewModel::onCategoryClickedWithoutDocument,
            fileActions = fileActions,
            navigator = navigator,
            modifier = modifier,
        )
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
                documents(Modifier.weight(0.25f).fillMaxHeight())
                preview(Modifier.weight(0.43f).fillMaxHeight())
                categories(Modifier.weight(0.32f).fillMaxHeight())
            }
        } else {
            CompactWorkspace(documents, categories, preview)
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
private fun StatusBar(home: HomeUiState) {
    Surface(tonalElevation = 3.dp) {
        val text = if (home.isLoading) Strings.LOADING else Strings.statusDocuments(home.documents.size)
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
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
