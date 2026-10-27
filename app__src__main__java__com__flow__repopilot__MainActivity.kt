package com.flow.repopilot

import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF11131B)
private val Panel = Color(0xFF1B1E29)
private val Violet = Color(0xFF9B8CFF)
private val Cyan = Color(0xFF78DCE8)
private val Muted = Color(0xFFA9ADBD)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(17, 19, 27)
        window.navigationBarColor = android.graphics.Color.rgb(17, 19, 27)
        setContent { RepoPilotApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepoPilotApp() {
    val context = LocalContext.current
    val secrets = remember { SecretStore(context) }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Secrets stay in memory only; never serialize API keys into saved-instance state.
    var githubToken by remember { mutableStateOf("") }
    var openRouterKey by remember { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("openai/gpt-4o-mini") }
    var owner by rememberSaveable { mutableStateOf("") }
    var repo by rememberSaveable { mutableStateOf("") }
    var branch by rememberSaveable { mutableStateOf("main") }
    var basePath by rememberSaveable { mutableStateOf("") }
    var commitMessage by rememberSaveable { mutableStateOf("Update project files") }
    var releaseTag by rememberSaveable { mutableStateOf("v1.0.1") }
    var releaseNotes by rememberSaveable { mutableStateOf("تحديث جديد للتطبيق.") }
    var aiTask by rememberSaveable { mutableStateOf("اكتب رسالة commit احترافية لهذا التحديث") }
    var aiContext by rememberSaveable { mutableStateOf("") }
    var aiResult by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("اختار ملف ZIP أو APK للبدء.") }
    var working by rememberSaveable { mutableStateOf(false) }
    var fileUri by remember { mutableStateOf<Uri?>(null) }
    var pickedFile by remember { mutableStateOf<PickedFile?>(null) }
    var uploadMode by rememberSaveable { mutableIntStateOf(0) }
    var showToken by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        githubToken = secrets.get("github_token")
        openRouterKey = secrets.get("openrouter_key")
        model = secrets.get("openrouter_model").ifBlank { "openai/gpt-4o-mini" }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            fileUri = uri
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            runCatching { pickedFile = RepoApi.fileInfo(context.contentResolver, uri); status = "تم اختيار ${pickedFile?.name}" }
                .onFailure { status = "تعذر قراءة الملف: ${it.message}" }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Violet, onPrimary = Ink, secondary = Cyan, background = Ink, surface = Panel,
            onBackground = Color(0xFFF4F3FA), onSurface = Color(0xFFF4F3FA), surfaceVariant = Color(0xFF292D3A),
            onSurfaceVariant = Muted, outline = Color(0xFF383C4A), error = Color(0xFFFF8585)
        ),
        typography = Typography(
            headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 28.sp),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            titleMedium = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        )
    ) {
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
            Scaffold(
                containerColor = Ink,
                bottomBar = {
                    NavigationBar(containerColor = Panel, contentColor = Color.White) {
                        NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Rounded.CloudUpload, null) }, label = { Text("رفع") })
                        NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Rounded.AutoAwesome, null) }, label = { Text("الذكاء") })
                        NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Rounded.Settings, null) }, label = { Text("الإعدادات") })
                    }
                }
            ) { padding ->
                AnimatedContent(tab, modifier = Modifier.padding(padding).fillMaxSize(), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { current ->
                    when (current) {
                        0 -> UploadScreen(
                            owner, { owner = it }, repo, { repo = it }, branch, { branch = it }, basePath, { basePath = it },
                            commitMessage, { commitMessage = it }, releaseTag, { releaseTag = it }, releaseNotes, { releaseNotes = it },
                            pickedFile, uploadMode, { uploadMode = it }, status, working,
                            onPick = { filePicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/vnd.android.package-archive", "application/octet-stream", "*/*")) },
                            onUpload = {
                                val uri = fileUri
                                if (uri == null) { status = "اختار الملف الأول." }
                                else if (uploadMode == 0 && pickedFile?.name?.endsWith(".zip", ignoreCase = true) != true) {
                                    status = "وضع تحديث المستودع يقبل ملفات ZIP فقط. لو هترفع APK، اختار وضع نشر Release."
                                } else {
                                    working = true; status = "بدأت العملية…"
                                    scope.launch {
                                        try {
                                            val result = withContext(Dispatchers.IO) {
                                                if (uploadMode == 0) RepoApi.uploadZipToRepository(githubToken, owner.trim(), repo.trim(), branch.trim(), basePath, commitMessage.trim(), context.contentResolver, uri) { msg -> scope.launch(Dispatchers.Main) { status = msg } }
                                                else RepoApi.uploadAsRelease(githubToken, owner.trim(), repo.trim(), releaseTag.trim(), releaseNotes, context.contentResolver, uri) { msg -> scope.launch(Dispatchers.Main) { status = msg } }
                                            }
                                            status = result
                                        } catch (e: Exception) { status = "فشلت العملية: ${e.message ?: "خطأ غير معروف"}" }
                                        finally { working = false }
                                    }
                                }
                            }
                        )
                        1 -> AiScreen(aiTask, { aiTask = it }, aiContext, { aiContext = it }, aiResult, working, {
                            if (openRouterKey.isBlank()) { aiResult = "أضف OpenRouter API Key من الإعدادات أولًا." }
                            else {
                                working = true; aiResult = "جاري التفكير…"
                                scope.launch {
                                    try { aiResult = withContext(Dispatchers.IO) { RepoApi.openRouter(openRouterKey, model, aiTask, aiContext) } }
                                    catch (e: Exception) { aiResult = "تعذر إكمال الطلب: ${e.message ?: "خطأ غير معروف"}" }
                                    finally { working = false }
                                }
                            }
                        }, onUseCommit = { commitMessage = it; tab = 0 }, onUseNotes = { releaseNotes = it; tab = 0 })
                        else -> SettingsScreen(
                            githubToken, { githubToken = it }, openRouterKey, { openRouterKey = it }, model, { model = it },
                            showToken, { showToken = it }, working, status,
                            onSave = {
                                secrets.put("github_token", githubToken.trim())
                                secrets.put("openrouter_key", openRouterKey.trim())
                                secrets.put("openrouter_model", model.trim())
                                status = "تم حفظ الإعدادات محليًا بتشفير Android Keystore."
                            },
                            onTest = {
                                if (githubToken.isBlank()) status = "أدخل GitHub Token أولًا."
                                else {
                                    working = true; status = "جاري اختبار GitHub…"
                                    scope.launch {
                                        try { val login = withContext(Dispatchers.IO) { RepoApi.checkGitHub(githubToken.trim()) }; status = "الاتصال ناجح — $login" }
                                        catch (e: Exception) { status = "فشل الاتصال: ${e.message}" }
                                        finally { working = false }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageFrame(title: String, subtitle: String, icon: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(Violet.copy(alpha = .35f), Cyan.copy(alpha = .12f)))), contentAlignment = Alignment.Center) { icon() }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
        }
        Spacer(Modifier.height(22.dp))
        content()
        Spacer(Modifier.height(24.dp))
        Text("RepoPilot · ${BuildConfig.VERSION_NAME}", color = Muted.copy(alpha = .7f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun UploadScreen(
    owner: String, onOwner: (String) -> Unit, repo: String, onRepo: (String) -> Unit,
    branch: String, onBranch: (String) -> Unit, basePath: String, onBasePath: (String) -> Unit,
    commitMessage: String, onCommit: (String) -> Unit, releaseTag: String, onTag: (String) -> Unit,
    releaseNotes: String, onNotes: (String) -> Unit, pickedFile: PickedFile?, uploadMode: Int, onMode: (Int) -> Unit,
    status: String, working: Boolean, onPick: () -> Unit, onUpload: () -> Unit
) {
    var confirmUpload by remember { mutableStateOf(false) }
    PageFrame("RepoPilot", "وكيل رفع وتحديث مستودعات GitHub", { Icon(Icons.Rounded.Code, null, tint = Violet, modifier = Modifier.size(28.dp)) }) {
        CardBlock {
            Text("مهمتك الأساسية", style = MaterialTheme.typography.titleLarge)
            Text("ارفع ZIP كتحديث متعدد الملفات في commit واحد، أو انشر APK/ZIP كملف Release قابل للتنزيل.", color = Muted)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ModeButton("تحديث ملفات المستودع", uploadMode == 0, Modifier.weight(1f)) { onMode(0) }
                ModeButton("نشر Release", uploadMode == 1, Modifier.weight(1f)) { onMode(1) }
            }
        }
        Spacer(Modifier.height(14.dp))
        CardBlock {
            Text("المستودع المستهدف", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("المالك / Username", owner, onOwner, Modifier.weight(1f), singleLine = true)
                Field("اسم المستودع", repo, onRepo, Modifier.weight(1f), singleLine = true)
            }
            if (uploadMode == 0) {
                Field("الفرع Branch", branch, onBranch, singleLine = true)
                Field("المسار داخل المستودع (اختياري)", basePath, onBasePath, singleLine = true, hint = "مثال: app/src/main")
                Field("رسالة Commit", commitMessage, onCommit, singleLine = true)
            } else {
                Field("Tag الإصدار", releaseTag, onTag, singleLine = true, hint = "مثال: v1.5.0")
                Field("ملاحظات الإصدار", releaseNotes, onNotes, minLines = 3)
            }
        }
        Spacer(Modifier.height(14.dp))
        CardBlock {
            Text("الملف", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), enabled = !working) {
                Icon(Icons.Rounded.FolderZip, null)
                Spacer(Modifier.width(8.dp))
                Text(if (pickedFile == null) "اختيار ZIP أو APK من الهاتف" else "تغيير الملف")
            }
            pickedFile?.let {
                Spacer(Modifier.height(10.dp))
                Text(it.name, fontWeight = FontWeight.SemiBold)
                Text(if (it.size >= 0) "${String.format("%.2f", it.size / 1024.0 / 1024.0)} MB · ${it.mime}" else it.mime, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { confirmUpload = true }, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = !working) {
                if (working) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Ink)
                else Icon(Icons.Rounded.CloudUpload, null)
                Spacer(Modifier.width(8.dp))
                Text(if (working) "جاري التنفيذ…" else if (uploadMode == 0) "فك ZIP ورفع التحديث" else "نشر الملف كـ Release")
            }
        }
        Spacer(Modifier.height(14.dp))
        StatusPanel(status, working)
        Spacer(Modifier.height(12.dp))
        Text("حدود أمان: تحديث ZIP يقبل حتى 80MB إجماليًا، 500 ملف، و24MB للملف الواحد. استخدم Release للـ APK الكبير.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
    if (confirmUpload) {
        AlertDialog(
            onDismissRequest = { confirmUpload = false },
            title = { Text("تأكيد عملية الرفع") },
            text = {
                Text(if (uploadMode == 0)
                    "سيتم إضافة أو استبدال الملفات الموجودة داخل $basePath في $owner/$repo على الفرع $branch، ثم إنشاء commit واحد. لن تُحذف الملفات الأخرى من المستودع. الملف: ${pickedFile?.name ?: "غير محدد"}."
                else "سيتم نشر ${pickedFile?.name ?: "الملف المحدد"} كأصل ضمن الإصدار $releaseTag في $owner/$repo. إذا كان هناك ملف بنفس الاسم في الإصدار، فسيتم استبداله.")
            },
            confirmButton = { TextButton(onClick = { confirmUpload = false; onUpload() }) { Text("تأكيد الرفع") } },
            dismissButton = { TextButton(onClick = { confirmUpload = false }) { Text("إلغاء") } },
            containerColor = Panel
        )
    }
}

@Composable
private fun AiScreen(task: String, onTask: (String) -> Unit, context: String, onContext: (String) -> Unit, result: String, working: Boolean, onRun: () -> Unit, onUseCommit: (String) -> Unit, onUseNotes: (String) -> Unit) {
    PageFrame("مساعد الذكاء", "OpenRouter · مساعدة عملية قبل النشر", { Icon(Icons.Rounded.AutoAwesome, null, tint = Cyan, modifier = Modifier.size(28.dp)) }) {
        CardBlock {
            Text("إيه اللي محتاجه؟", style = MaterialTheme.typography.titleLarge)
            Field("المهمة", task, onTask, minLines = 2)
            Field("وصف التحديث أو أسماء الملفات (اختياري)", context, onContext, minLines = 4, hint = "مثال: إصلاح أخطاء تسجيل الدخول وتحسين الأداء")
            Button(onClick = onRun, modifier = Modifier.fillMaxWidth().height(54.dp), enabled = !working) {
                if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.AutoAwesome, null)
                Spacer(Modifier.width(8.dp)); Text("تشغيل المساعد")
            }
        }
        Spacer(Modifier.height(14.dp))
        CardBlock {
            Text("النتيجة", style = MaterialTheme.typography.titleLarge)
            Text(result.ifBlank { "هنا هتظهر اقتراحات الذكاء الاصطناعي. لن يرفع المساعد أي شيء من تلقاء نفسه." }, color = if (result.isBlank()) Muted else Color.White)
            if (result.isNotBlank() && !working) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onUseCommit(result.lineSequence().firstOrNull().orEmpty().take(120)) }, modifier = Modifier.weight(1f)) { Text("استخدام أول سطر كـ Commit") }
                    OutlinedButton(onClick = { onUseNotes(result.take(1500)) }, modifier = Modifier.weight(1f)) { Text("استخدام كملاحظات") }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("الخصوصية: الذي يُرسل إلى OpenRouter هو نص المهمة والسياق المكتوب هنا فقط؛ لا تُرسل ملفات ZIP أو مفاتيح GitHub إليه.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SettingsScreen(githubToken: String, onGithub: (String) -> Unit, openRouterKey: String, onOpenRouter: (String) -> Unit, model: String, onModel: (String) -> Unit, showToken: Boolean, onShowToken: (Boolean) -> Unit, working: Boolean, status: String, onSave: () -> Unit, onTest: () -> Unit) {
    PageFrame("الإعدادات", "المفاتيح تُحفظ مشفرة على الجهاز", { Icon(Icons.Rounded.Settings, null, tint = Violet, modifier = Modifier.size(28.dp)) }) {
        CardBlock {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.VerifiedUser, null, tint = Cyan)
                Spacer(Modifier.width(8.dp)); Text("GitHub", style = MaterialTheme.typography.titleLarge)
            }
            Text("استخدم Fine-grained Personal Access Token بصلاحية Contents: Read and write للمستودعات المطلوبة.", color = Muted, style = MaterialTheme.typography.bodySmall)
            Field("GitHub Token", githubToken, onGithub, singleLine = true, visual = if (showToken) null else PasswordVisualTransformation(), hint = "ghp_… أو github_pat_…")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = showToken, onCheckedChange = onShowToken)
                Text("إظهار المفاتيح أثناء الكتابة", color = Muted)
            }
            OutlinedButton(onClick = onTest, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text("اختبار اتصال GitHub") }
        }
        Spacer(Modifier.height(14.dp))
        CardBlock {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Key, null, tint = Cyan)
                Spacer(Modifier.width(8.dp)); Text("OpenRouter", style = MaterialTheme.typography.titleLarge)
            }
            Field("OpenRouter API Key", openRouterKey, onOpenRouter, singleLine = true, visual = if (showToken) null else PasswordVisualTransformation(), hint = "sk-or-v1-…")
            Field("معرّف النموذج Model ID", model, onModel, singleLine = true, hint = "مثال: openai/gpt-4o-mini")
        }
        Spacer(Modifier.height(14.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("حفظ الإعدادات بأمان") }
        Spacer(Modifier.height(14.dp))
        StatusPanel(status, working)
        Spacer(Modifier.height(12.dp))
        Text("لا تضع مفاتيح API في GitHub أو داخل ملفات المشروع. حذف بيانات التطبيق أو إعادة ضبط مفاتيح الجهاز قد يجعل المفاتيح المحفوظة غير قابلة للاسترجاع.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CardBlock(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun Field(label: String, value: String, onValue: (String) -> Unit, modifier: Modifier = Modifier, singleLine: Boolean = false, minLines: Int = 1, hint: String = "", visual: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None) {
    OutlinedTextField(
        value = value, onValueChange = onValue, label = { Text(label) }, placeholder = { if (hint.isNotBlank()) Text(hint, color = Muted.copy(alpha = .75f)) },
        modifier = modifier.fillMaxWidth(), singleLine = singleLine, minLines = minLines, visualTransformation = visual,
        shape = RoundedCornerShape(17.dp), colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Violet, unfocusedBorderColor = Color(0xFF383C4A), focusedLabelColor = Violet,
            unfocusedLabelColor = Muted, focusedTextColor = Color.White, unfocusedTextColor = Color.White,
            cursorColor = Violet, focusedContainerColor = Ink.copy(alpha = .25f), unfocusedContainerColor = Ink.copy(alpha = .2f)
        )
    )
}

@Composable
private fun ModeButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = if (selected) ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Ink) else ButtonDefaults.outlinedButtonColors(contentColor = Muted)
    if (selected) Button(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), colors = colors, shape = CircleShape, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) { Text(text, fontSize = 12.sp) }
    else OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), shape = CircleShape, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) { Text(text, fontSize = 12.sp) }
}

@Composable
private fun StatusPanel(status: String, working: Boolean) {
    Surface(color = if (working) Color(0xFF24243A) else Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Cyan)
            else Icon(Icons.Rounded.VerifiedUser, null, tint = Cyan, modifier = Modifier.size(20.dp))
            Text(status, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
