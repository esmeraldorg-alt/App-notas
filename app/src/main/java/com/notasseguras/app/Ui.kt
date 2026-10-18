package com.notasseguras.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Verde = Color(0xFF2E5E4E)
private val Ambar = Color(0xFFE0A93B)

@Composable
fun NotasApp(vm: NotesViewModel) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme(primary = Color(0xFF8CC7B1), secondary = Ambar)
    else lightColorScheme(primary = Verde, secondary = Ambar, background = Color(0xFFFAF7F1), surface = Color(0xFFFAF7F1))

    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (val s = vm.screen) {
                Screen.Setup -> SetupScreen(vm)
                Screen.Unlock -> UnlockScreen(vm)
                Screen.List -> ListScreen(vm)
                is Screen.Edit -> EditScreen(vm, s)
                Screen.ChangePassword -> ChangePasswordScreen(vm)
            }
        }
    }
}

// ---------- Senha ----------

@Composable
private fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "Ocultar senha" else "Mostrar senha")
            }
        },
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun AuthLayout(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        content()
    }
}

@Composable
private fun ErrorText(vm: NotesViewModel) {
    vm.error?.let {
        Spacer(Modifier.height(12.dp))
        Text(it, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun SetupScreen(vm: NotesViewModel) {
    var pass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    AuthLayout("Crie sua senha", "Ela será pedida toda vez que abrir o app.") {
        PasswordField(pass, { pass = it; vm.clearError() }, "Senha")
        Spacer(Modifier.height(12.dp))
        PasswordField(confirm, { confirm = it; vm.clearError() }, "Confirmar senha",
            imeAction = ImeAction.Done, onDone = { vm.createPassword(pass, confirm) })
        ErrorText(vm)
        Spacer(Modifier.height(12.dp))
        Text(
            "Atenção: não existe recuperação. Se esquecer a senha, as notas não podem ser abertas.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = { vm.createPassword(pass, confirm) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            if (vm.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Criar senha")
        }
    }
}

@Composable
private fun UnlockScreen(vm: NotesViewModel) {
    var pass by remember { mutableStateOf("") }
    var askWipe by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    AuthLayout("Notas Seguras", "Digite sua senha para entrar.") {
        PasswordField(pass, { pass = it; vm.clearError() }, "Senha",
            imeAction = ImeAction.Done, onDone = { vm.unlock(pass) },
            modifier = Modifier.focusRequester(focus))
        ErrorText(vm)
        Spacer(Modifier.height(20.dp))
        Button(onClick = { vm.unlock(pass) }, enabled = !vm.busy && pass.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(50.dp)) {
            if (vm.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Entrar")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { askWipe = true }) { Text("Esqueci a senha") }
    }

    if (askWipe) {
        AlertDialog(
            onDismissRequest = { askWipe = false },
            title = { Text("Esqueceu a senha?") },
            text = { Text("As notas são criptografadas com a sua senha e não há como recuperá-las. A única opção é APAGAR TODAS as notas e criar uma senha nova.") },
            confirmButton = {
                TextButton(onClick = { askWipe = false; vm.wipeAll() }) {
                    Text("Apagar tudo", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { askWipe = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun ChangePasswordScreen(vm: NotesViewModel) {
    var current by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    BackHandler { vm.backToList() }
    AuthLayout("Trocar senha", "Suas notas serão recriptografadas.") {
        PasswordField(current, { current = it; vm.clearError() }, "Senha atual")
        Spacer(Modifier.height(12.dp))
        PasswordField(newPass, { newPass = it; vm.clearError() }, "Nova senha")
        Spacer(Modifier.height(12.dp))
        PasswordField(confirm, { confirm = it; vm.clearError() }, "Confirmar nova senha",
            imeAction = ImeAction.Done, onDone = { vm.changePassword(current, newPass, confirm) })
        ErrorText(vm)
        Spacer(Modifier.height(20.dp))
        Button(onClick = { vm.changePassword(current, newPass, confirm) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            if (vm.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Salvar nova senha")
        }
        TextButton(onClick = { vm.backToList() }) { Text("Cancelar") }
    }
}

// ---------- Lista ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListScreen(vm: NotesViewModel) {
    var query by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }
    val shown = remember(vm.notes, query) {
        if (query.isBlank()) vm.notes
        else vm.notes.filter { it.title.contains(query, true) || it.body.contains(query, true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Minhas notas") },
                actions = {
                    IconButton(onClick = { vm.lock() }) { Icon(Icons.Default.Lock, "Bloquear agora") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Mais opções") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Trocar senha") }, leadingIcon = { Icon(Icons.Default.Key, null) },
                            onClick = { menu = false; vm.openChangePassword() })
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.openNote(null) }) { Icon(Icons.Default.Add, "Nova nota") }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (vm.notes.isNotEmpty()) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Buscar") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (vm.notes.isEmpty()) "Nenhuma nota ainda.\nToque em + para criar." else "Nada encontrado.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp)) {
                    items(shown, key = { it.id }) { note -> NoteCard(note) { vm.openNote(note.id) } }
                }
            }
        }
    }
}

private val dateFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))

@Composable
private fun NoteCard(note: Note, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(note.title.ifBlank { "(sem título)" }, fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (note.body.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(note.body, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Text(dateFmt.format(Date(note.updatedAt)), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

// ---------- Editor ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditScreen(vm: NotesViewModel, s: Screen.Edit) {
    var askDelete by remember { mutableStateOf(false) }
    val bodyFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (s.noteId == null) runCatching { bodyFocus.requestFocus() } }
    BackHandler { vm.closeEditor() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { vm.closeEditor() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar e salvar") }
                },
                actions = {
                    IconButton(onClick = { askDelete = true }) { Icon(Icons.Default.Delete, "Excluir nota") }
                    IconButton(onClick = { vm.closeEditor() }) { Icon(Icons.Default.Check, "Salvar") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).imePadding().fillMaxSize().padding(horizontal = 8.dp)) {
            val fieldColors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
            )
            TextField(
                value = vm.draftTitle, onValueChange = { vm.draftTitle = it },
                placeholder = { Text("Título", fontSize = 22.sp) },
                textStyle = LocalTextStyle.current.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = vm.draftBody, onValueChange = { vm.draftBody = it },
                placeholder = { Text("Escreva aqui...") },
                textStyle = LocalTextStyle.current.copy(fontSize = 17.sp, lineHeight = 25.sp),
                colors = fieldColors,
                modifier = Modifier.fillMaxSize().focusRequester(bodyFocus)
            )
        }
    }

    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("Excluir nota?") },
            text = { Text("Essa ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(onClick = { askDelete = false; vm.deleteCurrent() }) {
                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Cancelar") } }
        )
    }
}
