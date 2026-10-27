package com.flowstudio.gitagent

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AppRoot(vm: ChatViewModel = viewModel()) {
    var page by rememberSaveable {
        mutableIntStateOf(if (vm.store.orKey.isBlank() || vm.store.ghToken.isBlank()) 1 else 0)
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Crossfade(targetState = page, label = "page") { p ->
            if (p == 0) ChatScreen(vm, onSettings = { page = 1 }) else SettingsScreen(vm, onBack = { page = 0 })
        }
    }
    vm.confirmReq?.let { req ->
        AlertDialog(
            onDismissRequest = { vm.answerConfirm(false) },
            title = { Text(stringResource(R.string.confirm_title), fontWeight = FontWeight.Bold) },
            text = { Text(req.text) },
            confirmButton = { Button(onClick = { vm.answerConfirm(true) }) { Text(stringResource(R.string.confirm_ok)) } },
            dismissButton = { TextButton(onClick = { vm.answerConfirm(false) }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun toolLabel(n: String) = when (n) {
    "github_upload_attachment" -> "رفع على GitHub"
    "github_list_repos" -> "عرض المستودعات"
    "github_create_repo" -> "إنشاء مستودع"
    "github_list_files" -> "عرض الملفات"
    "github_read_file" -> "قراءة ملف"
    "github_delete_path" -> "مسح من المستودع"
    "github_whoami" -> "فحص الحساب"
    else -> n
}

@Composable
fun ChatScreen(vm: ChatViewModel, onSettings: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.addFiles(uris)
    }
    LaunchedEffect(vm.messages.size, vm.busy) {
        val count = vm.messages.size + (if (vm.busy) 1 else 0)
        if (count > 0) listState.animateScrollToItem(count - 1)
    }
    val canSend = input.isNotBlank() || vm.pending.isNotEmpty()

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxWidth().height(280.dp).background(
                Brush.radialGradient(
                    listOf(cs.primary.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(0f, 0f), radius = 900f,
                )
            )
        )
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                    Text(vm.store.model, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledTonalIconButton(onClick = onSettings) {
                    Icon(Icons.Rounded.Settings, stringResource(R.string.cd_settings))
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (vm.messages.isEmpty() && !vm.busy) {
                    EmptyState(onPick = { input = it })
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(vm.messages, key = { it.id }) { m ->
                            when (m) {
                                is UiMsg.User -> UserBubble(m)
                                is UiMsg.Bot -> BotBubble(m.text)
                                is UiMsg.Step -> StepRow(m)
                                is UiMsg.Err -> ErrBubble(m.text)
                            }
                        }
                        if (vm.busy) {
                            item(key = "busy") {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.size(10.dp))
                                    Text(
                                        vm.statusText.ifBlank { stringResource(R.string.thinking) },
                                        style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (vm.pending.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        items(vm.pending.toList(), key = { it.id }) { a ->
                            Surface(shape = CircleShape, color = cs.secondaryContainer) {
                                Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Description, null, Modifier.size(18.dp), tint = cs.onSecondaryContainer)
                                    Text(
                                        "  ${a.name} · ${a.size / 1024} KB",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = cs.onSecondaryContainer, maxLines = 1,
                                    )
                                    IconButton(onClick = { vm.removePending(a) }, Modifier.size(36.dp)) {
                                        Icon(Icons.Rounded.Close, stringResource(R.string.cd_remove), Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                Surface(shape = RoundedCornerShape(28.dp), color = cs.surfaceContainerHigh) {
                    Row(Modifier.padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
                        IconButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Rounded.AttachFile, stringResource(R.string.cd_attach))
                        }
                        TextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.input_hint)) },
                            maxLines = 5,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                        )
                        FilledIconButton(
                            onClick = { if (vm.busy) vm.stop() else if (vm.send(input)) input = "" },
                            enabled = vm.busy || canSend,
                            modifier = Modifier.padding(bottom = 4.dp),
                        ) {
                            if (vm.busy) Icon(Icons.Rounded.Stop, "إيقاف")
                            else Icon(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.cd_send))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onPick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = CircleShape, color = cs.primaryContainer, modifier = Modifier.size(84.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(40.dp), tint = cs.onPrimaryContainer)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.empty_sub), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
        )
        val s1 = stringResource(R.string.sug1)
        val s2 = stringResource(R.string.sug2)
        SuggestionChip(onClick = { onPick(s1) }, label = { Text(s1) }, shape = CircleShape)
        SuggestionChip(onClick = { onPick(s2) }, label = { Text(s2) }, shape = CircleShape)
    }
}

@Composable
private fun UserBubble(m: UiMsg.User) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Surface(color = cs.primary, shape = RoundedCornerShape(22.dp), modifier = Modifier.widthIn(max = 340.dp)) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (m.text.isNotBlank()) Text(m.text, color = cs.onPrimary)
                m.files.forEach {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Description, null, Modifier.size(16.dp), tint = cs.onPrimary)
                        Text("  $it", style = MaterialTheme.typography.labelMedium, color = cs.onPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun BotBubble(text: String) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Surface(color = cs.surfaceContainerHigh, shape = RoundedCornerShape(22.dp), modifier = Modifier.widthIn(max = 340.dp)) {
            SelectionContainer {
                Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = cs.onSurface)
            }
        }
    }
}

@Composable
private fun ErrBubble(text: String) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.errorContainer, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Text(text, Modifier.padding(14.dp), color = cs.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StepRow(m: UiMsg.Step) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = if (m.ok) cs.secondaryContainer else cs.errorContainer,
        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(
                if (m.ok) Icons.Rounded.CheckCircle else Icons.Rounded.Error, null, Modifier.size(20.dp),
                tint = if (m.ok) cs.onSecondaryContainer else cs.onErrorContainer,
            )
            Column(Modifier.padding(start = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Build, null, Modifier.size(14.dp), tint = cs.onSurfaceVariant)
                    Text(
                        "  ${toolLabel(m.tool)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        color = if (m.ok) cs.onSecondaryContainer else cs.onErrorContainer,
                    )
                }
                Text(
                    m.summary, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    color = if (m.ok) cs.onSecondaryContainer else cs.onErrorContainer,
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var orKey by rememberSaveable { mutableStateOf(vm.store.orKey) }
    var gh by rememberSaveable { mutableStateOf(vm.store.ghToken) }
    var model by rememberSaveable { mutableStateOf(vm.store.model) }
    var picking by rememberSaveable { mutableStateOf(false) }
    if (picking) {
        ModelPicker(vm, model, onPick = { model = it; picking = false }, onBack = { picking = false })
        return
    }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back)) }
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        }
        OutlinedTextField(
            value = orKey, onValueChange = { orKey = it }, singleLine = true,
            label = { Text(stringResource(R.string.or_key)) },
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = gh, onValueChange = { gh = it }, singleLine = true,
            label = { Text(stringResource(R.string.gh_token)) },
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = model, onValueChange = { model = it }, singleLine = true,
            label = { Text(stringResource(R.string.model)) },
            supportingText = { Text(stringResource(R.string.model_hint)) },
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
        )
        FilledTonalButton(
            onClick = { picking = true },
            shape = CircleShape, modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("اختار من كل الموديلات", fontWeight = FontWeight.Bold) }
        Button(
            onClick = {
                vm.store.orKey = orKey
                vm.store.ghToken = gh
                vm.store.model = model
                onBack()
            },
            shape = CircleShape, modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(stringResource(R.string.save), fontWeight = FontWeight.Bold) }
        FilledTonalButton(
            onClick = { vm.testGithub(gh) },
            shape = CircleShape, modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(stringResource(R.string.test_gh), fontWeight = FontWeight.Bold) }
        if (vm.testResult.isNotBlank()) {
            Text(vm.testResult, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        Text(
            stringResource(R.string.about, BuildConfig.VERSION_NAME, BuildConfig.GIT_COMMIT),
            style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
        )
    }
}
