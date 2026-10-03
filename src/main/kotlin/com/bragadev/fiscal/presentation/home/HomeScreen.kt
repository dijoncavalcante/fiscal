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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.common.UserMessage
import com.bragadev.fiscal.presentation.components.pickFolder
import com.bragadev.fiscal.presentation.monthfiles.MonthFilesDialogHost
import com.bragadev.fiscal.presentation.monthfiles.MonthFilesViewModel
import com.bragadev.fiscal.presentation.organizer.MonthFileActions
import com.bragadev.fiscal.presentation.organizer.OrganizerDialogHost
import com.bragadev.fiscal.presentation.organizer.OrganizerScreen
import com.bragadev.fiscal.presentation.organizer.OrganizerUiState
import com.bragadev.fiscal.presentation.organizer.OrganizerViewModel
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
    onOpenSettings: () -> Unit,
) {
    val home by homeViewModel.uiState.collectAsState()
    val organizer by organizerViewModel.uiState.collectAsState()
    val monthFiles by monthFilesViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    MessageEffect(organizer.message, snackbarHostState, organizerViewModel::undo, organizerViewModel::onMessageShown)
    MessageEffect(monthFiles.message, snackbarHostState, organizerViewModel::undo, monthFilesViewModel::onMessageShown)
    ErrorEffect(home.error, snackbarHostState, homeViewModel::onErrorShown)

    Scaffold(
        topBar = {
            TopBar(
                canUndo = organizer.lastUndoableOperationId != null && !organizer.isWorking,
                onRefresh = homeViewModel::onRefresh,
                onUndo = { organizerViewModel.undo() },
                onOpenSettings = onOpenSettings,
            )
        },
        bottomBar = { StatusBar(home) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Workspace(home, organizer, previewViewModel, homeViewModel, organizerViewModel, monthFilesViewModel)
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
    val categories: @Composable (Modifier) -> Unit = { modifier ->
        OrganizerScreen(
            state = organizer,
            selectedDocument = selectedPath,
            onDrop = organizerViewModel::onFilesDropped,
            onCategoryClickedWithoutDocument = organizerViewModel::onCategoryClickedWithoutDocument,
            onEditMonthFolder = {
                val initial = organizerViewModel.currentMonthFolder ?: homeViewModel.currentSourceFolder
                pickFolder(Strings.MONTH_FOLDER_PICKER_TITLE, initial)?.let(organizerViewModel::onMonthFolderSelected)
            },
            fileActions = fileActions,
            modifier = modifier,
        )
    }
    val preview: @Composable (Modifier) -> Unit = { modifier -> PdfPreviewScreen(previewViewModel, selectedPath, modifier) }

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
) {
    Surface(tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                Strings.APP_TITLE,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onUndo, enabled = canUndo) { Text(Strings.UNDO_LAST) }
            TextButton(onClick = onRefresh) { Text(Strings.REFRESH) }
            TextButton(onClick = onOpenSettings) { Text("⚙ ${Strings.SETTINGS}") }
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
