package com.notasseguras.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.SecretKey

sealed interface Screen {
    data object Setup : Screen
    data object Unlock : Screen
    data object List : Screen
    data class Edit(val noteId: String?) : Screen
    data object ChangePassword : Screen
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel(app: Application) : AndroidViewModel(app) {
    private val store = NoteStore(app)
    private var key: SecretKey? = null

    var screen by mutableStateOf<Screen>(if (store.hasPassword) Screen.Unlock else Screen.Setup)
        private set
    var notes by mutableStateOf<List<Note>>(emptyList())
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)

    // Rascunho da nota aberta no editor (fica no ViewModel para sobreviver à rotação de tela)
    var draftTitle by mutableStateOf("")
    var draftBody by mutableStateOf("")

    // Gravação em fila única, para nunca gravar fora de ordem
    private val ioQueue = Dispatchers.IO.limitedParallelism(1)

    fun clearError() { error = null }

    fun createPassword(pass: String, confirm: String) {
        when {
            pass.length < 4 -> { error = "A senha precisa ter pelo menos 4 caracteres."; return }
            pass != confirm -> { error = "As senhas não conferem."; return }
        }
        runBusy {
            key = withContext(Dispatchers.Default) { store.createPassword(pass.toCharArray()) }
            notes = emptyList()
            screen = Screen.List
        }
    }

    fun unlock(pass: String) {
        val wait = store.lockoutRemainingMs()
        if (wait > 0) {
            error = "Muitas tentativas erradas. Aguarde ${(wait / 1000) + 1} segundos."
            return
        }
        runBusy {
            val k = withContext(Dispatchers.Default) { store.unlock(pass.toCharArray()) }
            if (k == null) {
                error = "Senha incorreta."
            } else {
                key = k
                notes = withContext(Dispatchers.IO) { store.load(k) }.sortedByDescending { it.updatedAt }
                screen = Screen.List
            }
        }
    }

    /** Chamado quando o app sai da tela: esquece a chave e as notas da memória. */
    fun lock() {
        if (key == null) return
        if (screen is Screen.Edit) saveDraft()
        key = null
        notes = emptyList()
        draftTitle = ""; draftBody = ""
        error = null
        screen = Screen.Unlock
    }

    fun openNote(id: String?) {
        val n = notes.find { it.id == id }
        draftTitle = n?.title ?: ""
        draftBody = n?.body ?: ""
        screen = Screen.Edit(n?.id)
    }

    /** Salva o rascunho e volta para a lista. */
    fun closeEditor() {
        saveDraft()
        screen = Screen.List
    }

    fun deleteCurrent() {
        (screen as? Screen.Edit)?.noteId?.let { deleteNote(it) }
        draftTitle = ""; draftBody = ""
        screen = Screen.List
    }

    private fun saveDraft() {
        val edit = screen as? Screen.Edit ?: return
        val id = saveNote(edit.noteId, draftTitle, draftBody)
        // Nota nova que acabou de ganhar id: evita duplicar se salvar de novo
        if (id != edit.noteId) screen = Screen.Edit(id)
    }

    fun backToList() { screen = Screen.List }
    fun openChangePassword() { error = null; screen = Screen.ChangePassword }

    /** Salva e retorna o id da nota (ou null se ficou vazia e foi descartada). */
    private fun saveNote(id: String?, title: String, body: String): String? {
        val k = key ?: return id
        if (title.isBlank() && body.isBlank()) {
            // Nota vazia: se já existia, remove; se era nova, descarta
            if (id != null) deleteNote(id)
            return null
        }
        val existing = notes.find { it.id == id }
        if (existing != null && existing.title == title && existing.body == body) return id
        val note = (existing ?: Note()).copy(title = title, body = body, updatedAt = System.currentTimeMillis())
        notes = (listOf(note) + notes.filterNot { it.id == note.id })
        persist(k)
        return note.id
    }

    fun deleteNote(id: String) {
        val k = key ?: return
        notes = notes.filterNot { it.id == id }
        persist(k)
    }

    fun changePassword(current: String, newPass: String, confirm: String) {
        val k = key ?: return
        when {
            newPass.length < 4 -> { error = "A nova senha precisa ter pelo menos 4 caracteres."; return }
            newPass != confirm -> { error = "As novas senhas não conferem."; return }
        }
        runBusy {
            val ok = withContext(Dispatchers.Default) { store.unlock(current.toCharArray()) }
            if (ok == null) {
                error = "Senha atual incorreta."
            } else {
                key = withContext(Dispatchers.Default) { store.changePassword(k, newPass.toCharArray()) }
                message = "Senha alterada."
                screen = Screen.List
            }
        }
    }

    /** "Esqueci a senha": a única saída é apagar tudo e começar de novo. */
    fun wipeAll() {
        store.wipe()
        key = null
        notes = emptyList()
        error = null
        screen = Screen.Setup
    }

    private fun persist(k: SecretKey) {
        val snapshot = notes
        viewModelScope.launch(ioQueue) { store.save(k, snapshot) }
    }

    private fun runBusy(block: suspend () -> Unit) {
        error = null
        busy = true
        viewModelScope.launch {
            try { block() } finally { busy = false }
        }
    }
}
