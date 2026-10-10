package me.weishu.kernelsu.ui.screen.umount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.UmountPath
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun UmountManagerScreenMiuix(
    state: UmountManagerUiState,
    actions: UmountManagerActions,
    snackBarHost: SnackbarHostState,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val barColor = colorScheme.surface
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopBar(
                onBack = actions.onBack,
                scrollBehavior = scrollBehavior,
                barColor = barColor,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                containerColor = colorScheme.primary,
                shadowElevation = 0.dp,
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .padding(
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                WindowInsets.captionBar.asPaddingValues().calculateBottomPadding() + 20.dp,
                        end = 20.dp,
                    ),
                content = {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.umount_add_path),
                        modifier = Modifier.size(40.dp),
                        tint = colorScheme.onPrimary,
                    )
                },
            )
        },
        popupHost = { },
        snackbarHost = {
            SnackbarHost(
                state = snackBarHost,
                modifier = Modifier.padding(bottom = 20.dp),
            )
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        if (state.isLoading && state.umountPaths.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                InfiniteProgressIndicator()
            }
            return@Scaffold
        }

        val pullToRefreshState = rememberPullToRefreshState()
        val refreshTexts = listOf(
            stringResource(R.string.refresh_pulling),
            stringResource(R.string.refresh_release),
            stringResource(R.string.refresh_refresh),
            stringResource(R.string.refresh_complete),
        )
        val layoutDirection = LocalLayoutDirection.current
        PullToRefresh(
            isRefreshing = state.isRefreshing,
            pullToRefreshState = pullToRefreshState,
            onRefresh = actions.onRefresh,
            refreshTexts = refreshTexts,
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 12.dp,
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
            ),
        ) {
            Box {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxHeight()
                        .scrollEndHaptic()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .padding(horizontal = 12.dp),
                    contentPadding = innerPadding,
                    overscrollEffect = null,
                ) {
                    item { Spacer(Modifier.height(12.dp)) }
                    if (state.umountPaths.isEmpty()) {
                        item {
                            Card {
                                Text(
                                    text = stringResource(R.string.umount_no_paths),
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    color = colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                    items(state.umountPaths, key = { it.path }) { entry ->
                        UmountItem(entry = entry, onRemove = { actions.onRemove(entry) })
                    }
                    item {
                        Spacer(
                            Modifier.height(
                                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                                        WindowInsets.captionBar.asPaddingValues().calculateBottomPadding(),
                            ),
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        UmountAddDialogMiuix(
            onDismiss = { showAddDialog = false },
            onConfirm = { path, flags ->
                showAddDialog = false
                actions.onAdd(path, flags)
            },
        )
    }
}

@Composable
private fun UmountItem(entry: UmountPath, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.padding(bottom = 12.dp),
        showIndication = true,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = entry.path,
                fontWeight = FontWeight(550),
                color = colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = 0.5.dp,
                color = colorScheme.outline.copy(alpha = 0.5f),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = umountPersistentLabel(entry.persistent),
                    fontSize = 12.sp,
                    fontWeight = FontWeight(550),
                    color = colorScheme.onSurfaceSecondary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = umountFlagName(entry.flags),
                    fontSize = 12.sp,
                    fontWeight = FontWeight(550),
                    color = colorScheme.onSurfaceSecondary,
                )
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.umount_remove),
                onClick = onRemove,
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

@Composable
private fun UmountAddDialogMiuix(
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit,
) {
    OverlayDialog(
        show = true,
        title = stringResource(R.string.umount_add_path),
        onDismissRequest = onDismiss,
        content = {
            var path by remember { mutableStateOf("") }
            var flags by remember { mutableStateOf("0") }

            TextField(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                value = path,
                label = stringResource(R.string.umount_mount_path),
                maxLines = 1,
                onValueChange = { path = it },
            )
            TextField(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                value = flags,
                label = stringResource(R.string.umount_flags),
                maxLines = 1,
                onValueChange = { newValue ->
                    if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                        flags = newValue
                    }
                },
            )
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(20.dp))
                TextButton(
                    text = stringResource(android.R.string.ok),
                    onClick = { onConfirm(path, flags.toIntOrNull() ?: 0) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        },
    )
}

@Composable
private fun TopBar(
    onBack: () -> Unit,
    scrollBehavior: ScrollBehavior,
    barColor: Color,
) {
    TopAppBar(
        color = barColor,
        title = stringResource(R.string.settings_umount_path_manager),
        navigationIcon = {
            top.yukonga.miuix.kmp.basic.IconButton(onClick = onBack) {
                Icon(
                    imageVector = MiuixIcons.Back,
                    contentDescription = null,
                    tint = colorScheme.onBackground,
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}
