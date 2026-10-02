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
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.common.UserMessage
import com.bragadev.fiscal.presentation.components.pickFolder
import com.bragadev.fiscal.presentation.organizer.OrganizerDialogHost
import com.bragadev.fiscal.presentation.organizer.OrganizerScreen
import com.bragadev.fiscal.presentation.organizer.OrganizerUiState
import com.bragadev.fiscal.presentation.organizer.OrganizerViewModel
import com.bragadev.fiscal.presentation.preview.PdfPreviewScreen
import com.bragadev.fiscal.presentation.preview.PdfPreviewViewModel
import java.nio.file.Path

private val WIDE_LAYOUT_MIN_WIDTH = 1100.dp

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    previewViewModel: PdfPreviewViewModel,
    organizerViewModel: OrganizerViewModel,
    onOpenSettings: () -> Unit,
) {
    val home by homeViewModel.uiState.collectAsState()
    val organizer by organizerViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val chooseRoot: () -> Unit = {
        pickFolder(home.rootPath.takeIf { it.isNotBlank() }?.let(Path::of))?.let(homeViewModel::onRootSelected)
    }

    MessageEffect(organizer.message, snackbarHostState, organizerViewModel)
    ErrorEffect(home.error, snackbarHostState, homeViewModel::onErrorShown)

    Scaffold(
        topBar = {
            TopBar(
                rootPath = home.rootPath,
                canUndo = organizer.lastUndoableOperationId != null && !organizer.isWorking,
                onChangeRoot = chooseRoot,
                onRefresh = homeViewModel::onRefresh,
                onUndo = { organizerViewModel.undo() },
                onOpenSettings = onOpenSettings,
            )
        },
        bottomBar = { StatusBar(home) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (home.rootStatus) {
                RootStatus.READY -> Workspace(home, organizer, previewViewModel, homeViewModel, organizerViewModel)
                RootStatus.NOT_CONFIGURED -> RootRequired(Strings.ROOT_NOT_CONFIGURED_BODY, chooseRoot)
                RootStatus.MISSING -> RootRequired(Strings.rootMissing(home.rootPath), chooseRoot)
                RootStatus.LOADING -> Unit
            }
            if (organizer.isWorking) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
    OrganizerDialogHost(organizer, organizerViewModel)
}

@Composable
private fun Workspace(
    home: HomeUiState,
    organizer: OrganizerUiState,
    previewViewModel: PdfPreviewViewModel,
    homeViewModel: HomeViewModel,
    organizerViewModel: OrganizerViewModel,
) {
    val selectedPath = home.selectedDocument?.path
    val documents: @Composable (Modifier) -> Unit = { modifier ->
        DocumentList(home.documents, home.selectedDocument, home.isLoading, homeViewModel::onDocumentSelected, modifier)
    }
    val categories: @Composable (Modifier) -> Unit = { modifier ->
        OrganizerScreen(
            groups = organizer.groups,
            selectedDocument = selectedPath,
            onDrop = organizerViewModel::onFilesDropped,
            onCategoryClickedWithoutDocument = organizerViewModel::onCategoryClickedWithoutDocument,
            modifier = modifier,
        )
    }
    val preview: @Composable (Modifier) -> Unit = { modifier -> PdfPreviewScreen(previewViewModel, selectedPath, modifier) }

    BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp)) {
        if (maxWidth >= WIDE_LAYOUT_MIN_WIDTH) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                documents(Modifier.weight(0.25f).fillMaxHeight())
                preview(Modifier.weight(0.45f).fillMaxHeight())
                categories(Modifier.weight(0.30f).fillMaxHeight())
            }
        } else {
            CompactWorkspace(documents, categories, preview)
        }
    }
}

/** Em janelas estreitas, documentos e categorias dividem a mesma coluna em abas. */
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
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(Strings.CATEGORIES) })
            }
            val panelModifier = Modifier.weight(1f).fillMaxWidth()
            if (tab == 0) documents(panelModifier) else categories(panelModifier)
        }
        preview(Modifier.weight(0.6f).fillMaxHeight())
    }
}

@Composable
private fun TopBar(
    rootPath: String,
    canUndo: Boolean,
    onChangeRoot: () -> Unit,
    onRefresh: () -> Unit,
    onUndo: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Strings.ROOT_LABEL, fontWeight = FontWeight.SemiBold)
                Text(
                    rootPath.ifBlank { Strings.SETTINGS_NO_FOLDER },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedButton(onClick = onChangeRoot) { Text(Strings.ROOT_CHANGE) }
            }
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
private fun RootRequired(message: String, onChooseRoot: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(Strings.ROOT_NOT_CONFIGURED_TITLE, style = MaterialTheme.typography.headlineSmall)
        Text(message, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp))
        Button(onClick = onChooseRoot) { Text(Strings.ROOT_SELECT) }
    }
}

@Composable
private fun MessageEffect(message: UserMessage?, snackbarHostState: SnackbarHostState, viewModel: OrganizerViewModel) {
    LaunchedEffect(message?.id) {
        message ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message.text,
            actionLabel = message.undoOperationId?.let { Strings.UNDO },
            withDismissAction = true,
            duration = if (message.undoOperationId != null) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undo(message.undoOperationId)
        viewModel.onMessageShown()
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
