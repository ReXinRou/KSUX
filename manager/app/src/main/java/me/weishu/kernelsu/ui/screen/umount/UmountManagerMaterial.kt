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
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.UmountPath
import me.weishu.kernelsu.ui.component.material.ExpressiveScaffold
import me.weishu.kernelsu.ui.component.material.SegmentedItem
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SnackBarHost
import me.weishu.kernelsu.ui.component.material.TopBarBackButton
import me.weishu.kernelsu.ui.component.material.expressiveTopAppBarColors
import me.weishu.kernelsu.ui.component.statustag.StatusTag

@Composable
fun UmountManagerScreenMaterial(
    state: UmountManagerUiState,
    actions: UmountManagerActions,
    snackBarHost: SnackbarHostState,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val pullToRefreshState = rememberPullToRefreshState()
    var showAddDialog by remember { mutableStateOf(false) }

    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_umount_path_manager)) },
                navigationIcon = { TopBarBackButton(onClick = actions.onBack) },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackBarHost(hostState = snackBarHost) },
        floatingActionButton = {
            SmallExtendedFloatingActionButton(
                expanded = true,
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.umount_add_path)) },
                modifier = Modifier.padding(
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                            WindowInsets.captionBar.asPaddingValues().calculateBottomPadding(),
                ),
            )
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            } else {
                PullToRefreshBox(
                    state = pullToRefreshState,
                    isRefreshing = state.isRefreshing,
                    onRefresh = actions.onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val navBars = WindowInsets.navigationBars.asPaddingValues()
                    val captionBar = WindowInsets.captionBar.asPaddingValues()
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 16.dp + 56.dp + 16.dp + navBars.calculateBottomPadding() +
                                    captionBar.calculateBottomPadding(),
                        ),
                    ) {
                        if (state.umountPaths.isEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.umount_no_paths),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 24.dp),
                                )
                            }
                        }
                        itemsIndexed(state.umountPaths, key = { _, it -> it.path }) { index, entry ->
                            SegmentedItem(index = index, count = state.umountPaths.size) {
                                UmountItem(entry = entry, onRemove = { actions.onRemove(entry) })
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        UmountAddDialog(
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
    SegmentedListItem(
        onClick = onRemove,
        headlineContent = { Text(entry.path) },
        supportingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusTag(
                    label = umountPersistentLabel(entry.persistent),
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    backgroundColor = MaterialTheme.colorScheme.primary,
                )
                StatusTag(
                    label = umountFlagName(entry.flags),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    backgroundColor = MaterialTheme.colorScheme.secondaryContainer,
                )
            }
        },
        leadingContent = { Icon(Icons.Filled.Folder, null) },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.umount_remove),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

@Composable
private fun UmountAddDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit,
) {
    var path by remember { mutableStateOf("") }
    var flags by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.umount_add_path)) },
        text = {
            Column {
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text(stringResource(R.string.umount_mount_path)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = flags,
                    onValueChange = { flags = it },
                    label = { Text(stringResource(R.string.umount_flags)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text(stringResource(R.string.umount_flags_hint)) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(path, flags.toIntOrNull() ?: 0) },
                enabled = path.isNotBlank(),
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
