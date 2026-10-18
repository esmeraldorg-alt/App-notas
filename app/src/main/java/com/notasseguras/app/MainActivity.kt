package com.notasseguras.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val vm: NotesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Impede print de tela e esconde o conteúdo na lista de apps recentes
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent { NotasApp(vm) }
    }

    override fun onStop() {
        super.onStop()
        // Saiu do app (home, outra tela, desligou a tela) -> bloqueia.
        // Não bloqueia só por girar o aparelho.
        if (!isChangingConfigurations) vm.lock()
    }
}
