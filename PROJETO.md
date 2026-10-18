# Notas Seguras — App Android de bloco de notas com senha

> Arquivo de referência do projeto. Revisar sempre antes de mexer no código.

## O que é
Bloco de notas para Android que pede senha para entrar. As notas ficam
**criptografadas** no próprio celular — sem internet, sem conta, sem nuvem.

## Funcionalidades
- Criar senha no primeiro uso (mín. 4 caracteres, com confirmação)
- Tela de senha toda vez que abre o app
- **Bloqueio automático** ao sair do app (botão home, trocar de app, apagar a tela)
- Botão de cadeado para bloquear na hora
- Criar, editar, buscar e excluir notas (título + texto)
- Salvamento automático ao voltar/sair do editor
- Trocar senha (recriptografa todas as notas)
- "Esqueci a senha": apaga tudo e recomeça (não existe recuperação, por segurança)
- Proteção contra força bruta: após 5 erros, espera crescente (30s → até 15 min)
- Bloqueia print de tela e esconde o conteúdo na lista de apps recentes
- Backup automático do Android desativado (notas não vão para a nuvem)
- Tema claro e escuro automático

## Segurança (como funciona)
| Item | Como é feito |
|---|---|
| Senha | Nunca é gravada. Vira uma chave via **PBKDF2-HMAC-SHA256**, 210.000 iterações, salt aleatório de 16 bytes |
| Notas | Arquivo único `notes.bin` cifrado com **AES-256-GCM** (IV novo a cada gravação) |
| Verificação da senha | Um texto fixo cifrado ("verificador"); se decifrar, a senha está certa |
| Memória | Ao bloquear, a chave e as notas são descartadas da memória |

⚠️ **Se esquecer a senha, as notas são irrecuperáveis.**

## Tecnologia
- Kotlin 2.0.21 + Jetpack Compose (Material 3)
- Android mínimo: **8.0 (API 26)** · alvo: API 35
- Sem banco de dados: lista de notas em JSON (kotlinx.serialization) criptografado
- Gradle 8.14.3 · AGP 8.7.3 · Java 17

## Estrutura
```
notas-seguras/
├── PROJETO.md                 ← este arquivo
├── .github/workflows/build-apk.yml  ← gera o APK no GitHub a cada push
├── app/build.gradle.kts       ← dependências e versão do app
└── app/src/main/
    ├── AndroidManifest.xml
    ├── res/drawable/ic_launcher.xml   ← ícone (bloco + cadeado)
    ├── res/xml/data_extraction_rules.xml ← bloqueia backup
    └── java/com/notasseguras/app/
        ├── MainActivity.kt    ← entrada; FLAG_SECURE; bloqueia no onStop
        ├── Crypto.kt          ← PBKDF2 + AES-GCM
        ├── NoteStore.kt       ← grava/lê notas, senha, tentativas erradas
        ├── NotesViewModel.kt  ← estado e regras das telas
        └── Ui.kt              ← telas (senha, lista, editor, trocar senha)
```

## Repositório
https://github.com/esmeraldorg-alt/App-notas

## Como gerar o APK
**Opção 1 — GitHub (sem instalar nada):**
1. Envie o projeto para o GitHub (`git push`)
2. Aba **Actions** → "Gerar APK" → abra a execução mais recente
3. Baixe o artefato **NotasSeguras-apk** (zip com o `.apk`)
4. Passe para o celular e instale (permitir "fontes desconhecidas")

**Opção 2 — Android Studio:**
1. File → Open → pasta `notas-seguras`
2. Esperar o Gradle sincronizar
3. Build → Build APK(s) — ou conectar o celular e clicar em ▶ Run

> O APK de release está assinado com a chave de debug (bom para uso próprio).
> Para publicar na Play Store, criar uma keystore própria e trocar em `app/build.gradle.kts`.

## Status
- [x] Código completo (v1.0)
- [x] Primeiro build no GitHub Actions (sucesso em 2026-10-18)
- [ ] Testar no celular

## Ideias futuras
- Desbloqueio por digital (BiometricPrompt)
- Exportar/importar backup criptografado
- Categorias / fixar notas no topo
- Tempo de bloqueio configurável

## Histórico
- **2026-10-18** — Criação do projeto (v1.0)
