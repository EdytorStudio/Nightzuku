package moe.shizuku.manager.module.catalog

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.shizuku.manager.R
import moe.shizuku.manager.module.ModuleSettings
import moe.shizuku.manager.module.discovery.DiscoveredModule
import moe.shizuku.manager.module.discovery.ModuleDiscoveryManager
import moe.shizuku.manager.module.update.ModuleInstaller
import moe.shizuku.manager.ui.compose.ShizukuExpressiveTheme
import moe.shizuku.manager.ui.compose.ShizukuIcon
import moe.shizuku.manager.ui.compose.ShizukuLazyScaffold
import java.util.Locale

private enum class SortMode {
    NEWEST,
    OLDEST,
    STARS,
    OFFICIAL
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CatalogScreen(
    onNavigateUp: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val discoveryManager = remember { ModuleDiscoveryManager.getInstance(context) }
    val installer = remember { ModuleInstaller.getInstance() }

    var modules by remember { mutableStateOf<List<DiscoveredModule>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var sortMode by remember { mutableStateOf(SortMode.STARS) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var showInstallDialog by remember { mutableStateOf<DiscoveredModule?>(null) }
    var installing by remember { mutableStateOf<String?>(null) }
    var installSuccess by remember { mutableStateOf(false) }

    var token by remember { mutableStateOf<String?>(TokenStore.getToken(context)) }

    LaunchedEffect(token) {
        if (token.isNullOrBlank()) {
            showTokenDialog = true
            isLoading = false
        } else {
            val cached = discoveryManager.getModules(forceRefresh = false)
            if (cached.isNotEmpty()) {
                modules = cached
                isLoading = false
            } else {
                isLoading = true
                error = null
                try {
                    modules = discoveryManager.getModules(forceRefresh = true)
                } catch (e: Exception) {
                    error = e.message
                } finally {
                    isLoading = false
                }
            }
        }
    }

    val sortedModules = remember(modules, sortMode) {
        when (sortMode) {
            SortMode.NEWEST -> modules.sortedByDescending { it.lastChecked }
            SortMode.OLDEST -> modules.sortedBy { it.lastChecked }
            SortMode.STARS -> modules.sortedByDescending { it.stars }
            SortMode.OFFICIAL -> modules.sortedByDescending { it.isOfficial }
        }
    }

    ShizukuExpressiveTheme {
        ShizukuLazyScaffold(
            title = stringResource(R.string.modules_catalog_title),
            onNavigateUp = onNavigateUp,
            actions = {
                FilledTonalButton(
                    modifier = Modifier.height(36.dp),
                    onClick = {
                        scope.launch {
                            isLoading = true
                            error = null
                            try {
                                modules = discoveryManager.getModules(forceRefresh = true)
                            } catch (e: Exception) {
                                error = e.message
                            } finally {
                                isLoading = false
                            }
                        }
                    }
                ) {
                    ShizukuIcon(
                        R.drawable.ic_server_restart,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(16.dp)
                    )
                    Text(stringResource(R.string.home_refresh))
                }
            }
        ) {
            item {
                SortChipRow(sortMode = sortMode, onSortChange = { sortMode = it })
            }

            item {
                AnimatedVisibility(
                    visible = isLoading,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LoadingIndicator(Modifier.size(32.dp))
                        Text(
                            text = stringResource(R.string.modules_running),
                            modifier = Modifier.padding(start = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!isLoading && error != null) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = error ?: "Error",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            if (!isLoading && modules.isEmpty() && error == null) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.modules_catalog_empty),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            items(sortedModules, key = { "${it.repoFullName}/${it.moduleId}" }) { module ->
                CatalogModuleCard(
                    module = module,
                    installing = installing == module.moduleId,
                    onInstall = { showInstallDialog = module },
                    onViewOnGitHub = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(module.repoUrl))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        if (showTokenDialog) {
            TokenInputDialog(
                onDismiss = {
                    showTokenDialog = false
                    if (TokenStore.getToken(context).isNullOrBlank()) {
                        onNavigateUp()
                    }
                },
                onTokenSet = { pat ->
                    TokenStore.setToken(context, pat)
                    token = pat
                    showTokenDialog = false
                }
            )
        }

        showInstallDialog?.let { module ->
            InstallModeDialog(
                module = module,
                onDismiss = { showInstallDialog = null },
                onInstall = { mode ->
                    showInstallDialog = null
                    ModuleSettings.setInstallMode(mode)
                    installing = module.moduleId
                    scope.launch {
                        val owner = module.repoFullName.substringBefore('/')
                        val repo = module.repoFullName.substringAfter('/')
                        val result = installer.installModule(
                            context = context,
                            moduleId = module.moduleId,
                            owner = owner,
                            repo = repo,
                            subPath = module.subPath
                        )
                        installing = null
                        result.fold(
                            onSuccess = {
                                installSuccess = true
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.modules_install_success, module.moduleName),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onFailure = {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.modules_catalog_install_failed, it.message ?: it.javaClass.simpleName),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    }
                }
            )
        }

        if (installSuccess) {
            AlertDialog(
                onDismissRequest = { installSuccess = false },
                title = { Text(stringResource(R.string.modules_install_success, "")) },
                confirmButton = {
                    TextButton(onClick = {
                        installSuccess = false
                        onNavigateUp()
                    }) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            )
        }
    }
}

@Composable
private fun SortChipRow(sortMode: SortMode, onSortChange: (SortMode) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        SortMode.entries.forEach { mode ->
            FilterChip(
                selected = sortMode == mode,
                onClick = { onSortChange(mode) },
                label = {
                    Text(
                        text = when (mode) {
                            SortMode.NEWEST -> stringResource(R.string.modules_catalog_sort_newest)
                            SortMode.OLDEST -> stringResource(R.string.modules_catalog_sort_oldest)
                            SortMode.STARS -> stringResource(R.string.modules_catalog_sort_stars)
                            SortMode.OFFICIAL -> stringResource(R.string.modules_catalog_sort_official)
                        }
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

@Composable
private fun CatalogModuleCard(
    module: DiscoveredModule,
    installing: Boolean,
    onInstall: () -> Unit,
    onViewOnGitHub: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CatalogAvatar(
                    ownerAvatar = module.ownerAvatar,
                    moduleName = module.moduleName,
                    modifier = Modifier.size(44.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = module.moduleName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = module.author ?: module.repoFullName.substringBefore('/'),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                module.version?.let {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = it,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = "\u2605 ${module.stars}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                if (module.isValid) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = stringResource(R.string.modules_catalog_sort_official),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            module.repoDescription?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            AnimatedVisibility(
                visible = installing,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    modifier = Modifier.height(36.dp),
                    enabled = !installing,
                    onClick = onInstall
                ) {
                    ShizukuIcon(
                        R.drawable.ic_outline_arrow_upward_24,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(16.dp)
                    )
                    Text(stringResource(R.string.modules_install_zip))
                }
                OutlinedButton(
                    modifier = Modifier.height(36.dp),
                    onClick = onViewOnGitHub
                ) {
                    ShizukuIcon(
                        R.drawable.ic_outline_open_in_new_24,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(16.dp)
                    )
                    Text("GitHub")
                }
            }
        }
    }
}

@Composable
private fun CatalogAvatar(
    ownerAvatar: String,
    moduleName: String,
    modifier: Modifier = Modifier
) {
    val initials = remember(moduleName) {
        moduleName.take(2).uppercase(Locale.getDefault())
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TokenInputDialog(
    onDismiss: () -> Unit,
    onTokenSet: (String) -> Unit
) {
    var tokenInput by remember { mutableStateOf("") }
    val showWarning = tokenInput.isNotBlank() && !TokenStore.isValidTokenFormat(tokenInput)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.modules_catalog_token_required)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.modules_catalog_token_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = { Text(stringResource(R.string.modules_catalog_token_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (showWarning) {
                    Text(
                        text = stringResource(R.string.modules_catalog_token_format_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onTokenSet(tokenInput) },
                enabled = tokenInput.isNotBlank()
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InstallModeDialog(
    module: DiscoveredModule,
    onDismiss: () -> Unit,
    onInstall: (ModuleSettings.InstallMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(module.moduleName) },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onInstall(ModuleSettings.InstallMode.SOURCES) }) {
                    ShizukuIcon(R.drawable.ic_code_24dp, modifier = Modifier.size(18.dp).padding(end = 4.dp))
                    Text(stringResource(R.string.modules_catalog_install_sources))
                }
                TextButton(onClick = { onInstall(ModuleSettings.InstallMode.RELEASE) }) {
                    ShizukuIcon(R.drawable.ic_outline_arrow_upward_24, modifier = Modifier.size(18.dp).padding(end = 4.dp))
                    Text(stringResource(R.string.modules_catalog_install_release))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}
