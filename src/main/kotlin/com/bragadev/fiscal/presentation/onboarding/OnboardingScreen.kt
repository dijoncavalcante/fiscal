package com.bragadev.fiscal.presentation.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bragadev.fiscal.domain.model.AppSettings
import com.bragadev.fiscal.presentation.common.Strings
import com.bragadev.fiscal.presentation.components.AppIcons
import com.bragadev.fiscal.presentation.components.FolderPathField
import com.bragadev.fiscal.presentation.components.IconText
import com.bragadev.fiscal.presentation.components.StatusColors
import com.bragadev.fiscal.presentation.components.pickFolder
import com.bragadev.fiscal.presentation.home.HomeViewModel
import com.bragadev.fiscal.presentation.navigator.MonthNavigatorViewModel
import com.bragadev.fiscal.presentation.navigator.MonthTreePicker
import com.bragadev.fiscal.presentation.organizer.OrganizerViewModel

/**
 * Assistente da primeira vez, em três passos. Cada passo pode ficar em branco ("Próximo" sempre avança)
 * e "Pular assistente" fecha tudo; o usuário sempre termina na tela principal.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    settings: AppSettings,
    homeViewModel: HomeViewModel,
    navigatorViewModel: MonthNavigatorViewModel,
    organizerViewModel: OrganizerViewModel,
    onDone: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val navigator by navigatorViewModel.uiState.collectAsState()
    val organizer by organizerViewModel.uiState.collectAsState()

    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 2.dp,
            modifier = Modifier.padding(32.dp).widthIn(max = 780.dp).fillMaxWidth(),
        ) {
            Column(Modifier.padding(28.dp)) {
                Text(Strings.ONBOARDING_WELCOME, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    Strings.ONBOARDING_INTRO,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
                )
                StepIndicator(state.step)
                HorizontalDivider(Modifier.padding(vertical = 20.dp))

                when (state.step) {
                    OnboardingStep.MONTHS_ROOT -> StepContent(AppIcons.Folder, Strings.ONBOARDING_ROOT_TITLE, Strings.ONBOARDING_ROOT_TEXT) {
                        FolderPathField(
                            label = Strings.MONTHS_ROOT_LABEL,
                            path = navigator.root?.toString().orEmpty(),
                            emptyText = Strings.MONTHS_ROOT_EMPTY,
                            editDescription = Strings.MONTHS_ROOT_EDIT,
                            onEdit = {
                                pickFolder(Strings.MONTHS_ROOT_PICKER_TITLE, navigatorViewModel.currentRoot)
                                    ?.let(navigatorViewModel::onRootSelected)
                            },
                        )
                        val accounts = navigator.tree?.accounts.orEmpty()
                        when {
                            accounts.isNotEmpty() -> IconText(
                                AppIcons.CheckCircle,
                                Strings.accountsFound(accounts.map { it.account.displayName }),
                                color = StatusColors.Positive,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            navigator.root != null && !navigator.isLoading -> IconText(
                                AppIcons.Warning,
                                Strings.NO_MONTHS_FOUND,
                                color = StatusColors.Warning,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                    OnboardingStep.SOURCE_FOLDER -> StepContent(AppIcons.FolderOpen, Strings.ONBOARDING_SOURCE_TITLE, Strings.ONBOARDING_SOURCE_TEXT) {
                        FolderPathField(
                            label = Strings.SOURCE_FOLDER_LABEL,
                            path = settings.sourceFolder?.toString().orEmpty(),
                            emptyText = Strings.SOURCE_FOLDER_EMPTY,
                            editDescription = Strings.SOURCE_FOLDER_EDIT,
                            onEdit = {
                                pickFolder(Strings.SOURCE_FOLDER_PICKER_TITLE, homeViewModel.currentSourceFolder)
                                    ?.let(homeViewModel::onSourceFolderSelected)
                            },
                        )
                    }
                    OnboardingStep.MONTH -> StepContent(AppIcons.Calendar, Strings.ONBOARDING_MONTH_TITLE, Strings.ONBOARDING_MONTH_TEXT) {
                        MonthTreePicker(navigator, navigatorViewModel)
                        FolderPathField(
                            label = Strings.MONTH_FOLDER_LABEL,
                            path = organizer.monthFolder?.path?.toString().orEmpty(),
                            emptyText = Strings.MONTH_FOLDER_EMPTY,
                            editDescription = Strings.MONTH_FOLDER_EDIT,
                            highlightedSegment = organizer.monthFolder?.detectedMonth?.folderName,
                            onEdit = {
                                val initial = organizerViewModel.currentMonthFolder ?: navigatorViewModel.currentRoot
                                pickFolder(Strings.MONTH_FOLDER_PICKER_TITLE, initial)?.let(organizerViewModel::onMonthFolderSelected)
                            },
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
                navigator.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }

                Row(Modifier.fillMaxWidth().padding(top = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { viewModel.onFinish(onDone) }) { Text(Strings.ONBOARDING_SKIP) }
                    Spacer(Modifier.weight(1f))
                    if (!state.isFirst) {
                        OutlinedButton(onClick = viewModel::onBack, modifier = Modifier.padding(end = 8.dp)) { Text(Strings.ONBOARDING_BACK) }
                    }
                    if (state.isLast) {
                        Button(onClick = { viewModel.onFinish(onDone) }) { Text(Strings.ONBOARDING_FINISH) }
                    } else {
                        Button(onClick = viewModel::onNext) { Text(Strings.ONBOARDING_NEXT) }
                    }
                }
            }
        }
    }
}

/** "1 Pasta raiz — 2 Pasta de origem — 3 Mês": passos feitos com ✓, o atual em destaque. */
@Composable
private fun StepIndicator(current: OnboardingStep) {
    val titles = mapOf(
        OnboardingStep.MONTHS_ROOT to Strings.ONBOARDING_ROOT_TITLE,
        OnboardingStep.SOURCE_FOLDER to Strings.ONBOARDING_SOURCE_TITLE,
        OnboardingStep.MONTH to Strings.ONBOARDING_MONTH_TITLE,
    )
    Column {
        Text(
            Strings.onboardingStep(current.ordinal + 1, OnboardingStep.entries.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(top = 8.dp)) {
            OnboardingStep.entries.forEach { step ->
                val done = step.ordinal < current.ordinal
                val active = step == current
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val color = if (active || done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    if (done) {
                        Icon(AppIcons.CheckCircle, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                    } else {
                        Box(Modifier.size(24.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                            Text(
                                "${step.ordinal + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.surface,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Text(
                        titles.getValue(step),
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StepContent(icon: ImageVector, title: String, text: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IconText(
            icon,
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            iconSize = 26.dp,
            iconTint = MaterialTheme.colorScheme.primary,
        )
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Column(Modifier.padding(top = 8.dp)) { content() }
    }
}
