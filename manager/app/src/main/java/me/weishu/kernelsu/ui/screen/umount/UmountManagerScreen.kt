package me.weishu.kernelsu.ui.screen.umount

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.LocalUiMode
import me.weishu.kernelsu.ui.UiMode
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import me.weishu.kernelsu.ui.viewmodel.UmountManagerEvent
import me.weishu.kernelsu.ui.viewmodel.UmountManagerViewModel
import top.yukonga.miuix.kmp.basic.SnackbarHostState as MiuixSnackbarHostState

@Composable
fun UmountManagerScreen() {
    val uiMode = LocalUiMode.current
    val navigator = LocalNavigator.current
    val viewModel = viewModel<UmountManagerViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackBarHost = remember { SnackbarHostState() }
    val miuixSnackbarHost = remember { MiuixSnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UmountManagerEvent.Message -> {
                    if (uiMode == UiMode.Material) {
                        snackBarHost.showSnackbar(event.message)
                    } else {
                        miuixSnackbarHost.showSnackbar(event.message)
                    }
                }
            }
        }
    }

    val actions = UmountManagerActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onRefresh = viewModel::refresh,
        onRemove = viewModel::remove,
        onAdd = { path, flags -> viewModel.add(path, flags) },
    )

    when (uiMode) {
        UiMode.Material -> UmountManagerScreenMaterial(
            state = uiState,
            actions = actions,
            snackBarHost = snackBarHost,
        )

        UiMode.Miuix -> UmountManagerScreenMiuix(
            state = uiState,
            actions = actions,
            snackBarHost = miuixSnackbarHost,
        )
    }
}

@Composable
fun umountFlagName(flags: Int): String = when (flags) {
    0 -> "UMOUNT_UNUSED"
    1 -> "MNT_FORCE"
    2 -> "MNT_DETACH"
    4 -> "MNT_EXPIRE"
    8 -> "UMOUNT_NOFOLLOW"
    else -> flags.toString()
}

@Composable
fun umountPersistentLabel(persistent: Boolean): String =
    if (persistent) stringResource(R.string.umount_persistent) else stringResource(R.string.umount_temporary)
