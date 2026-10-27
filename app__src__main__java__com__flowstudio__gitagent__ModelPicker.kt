package com.flowstudio.gitagent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ModelPicker(vm: ChatViewModel, current: String, onPick: (String) -> Unit, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var q by rememberSaveable { mutableStateOf("") }
    var freeOnly by rememberSaveable { mutableStateOf(true) }
    var toolsOnly by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) { if (vm.models.isEmpty()) vm.loadModels() }

    val list = remember(vm.models, q, freeOnly, toolsOnly) {
        val s = q.trim().lowercase()
        vm.models.filter {
            (!freeOnly || it.free) && (!toolsOnly || it.tools) &&
                (s.isEmpty() || it.id.lowercase().contains(s) || it.name.lowercase().contains(s))
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back)) }
            Text("اختار الموديل", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        }
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            placeholder = { Text("ابحث بالاسم أو الـ id…") },
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = freeOnly, onClick = { freeOnly = !freeOnly }, label = { Text("مجاني") }, shape = CircleShape)
            FilterChip(selected = toolsOnly, onClick = { toolsOnly = !toolsOnly }, label = { Text("يدعم Tools") }, shape = CircleShape)
        }
        Row(Modifier.padding(horizontal = 20.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (vm.modelsLoading) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("  بحمّل القايمة…", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            } else {
                Text("${list.size} موديل", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
        }
        if (vm.modelsError.isNotBlank()) {
            Text(
                vm.modelsError, style = MaterialTheme.typography.bodySmall, color = cs.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(list, key = { it.id }) { m ->
                val selected = m.id == current
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) cs.primaryContainer else cs.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().clickable { onPick(m.id) },
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(m.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(m.id, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val tags = buildList {
                            if (m.free) add("مجاني")
                            if (m.tools) add("Tools")
                            if (m.ctx > 0) add("${m.ctx / 1000}K")
                        }.joinToString(" · ")
                        if (tags.isNotEmpty()) Text(tags, style = MaterialTheme.typography.labelSmall, color = cs.primary)
                    }
                }
            }
        }
    }
}
