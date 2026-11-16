package com.janderson.borajuntos.ui.screens

import android.util.Patterns
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp

@Composable
fun Page(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
fun AuthScreen(cadastro: Boolean, state: AuthState, enviar: (String, String, String) -> Unit,
               alternar: () -> Unit) {
    var nome by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    // Senhas ficam somente em memória, nunca em SavedState ou preferências.
    var senha by remember { mutableStateOf("") }
    var confirmacao by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf<String?>(null) }
    Page {
        Icon(androidx.compose.ui.res.painterResource(com.janderson.borajuntos.R.drawable.ic_group),
            contentDescription = null, tint = androidx.compose.ui.graphics.Color.Unspecified,
            modifier = Modifier.size(80.dp).align(Alignment.CenterHorizontally))
        Text("Bora Juntos", style = MaterialTheme.typography.headlineLarge)
        Text(if (cadastro) "Crie sua conta" else "Organize viagens e eventos com seus amigos")
        if (cadastro) OutlinedTextField(nome, { nome = it }, label = { Text("Nome") },
            singleLine = true, enabled = !state.carregando, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            enabled = !state.carregando, modifier = Modifier.fillMaxWidth())
        PasswordField("Senha", senha, !state.carregando) { senha = it }
        if (cadastro) PasswordField("Confirme a senha", confirmacao, !state.carregando) { confirmacao = it }
        (erro ?: state.erro)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.carregando) CircularProgressIndicator()
        Button(enabled = !state.carregando, modifier = Modifier.fillMaxWidth(), onClick = {
            erro = when {
                cadastro && nome.trim().length !in 2..80 -> "Informe um nome entre 2 e 80 caracteres."
                email.trim().length > 254 || !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Informe um e-mail válido."
                senha.isEmpty() -> "Informe sua senha."
                cadastro && senha.length < 6 -> "Use pelo menos 6 caracteres na senha."
                cadastro && senha != confirmacao -> "As senhas não coincidem."
                else -> null
            }
            if (erro == null) enviar(nome, email, senha)
        }) { Text(if (cadastro) "Criar conta" else "Entrar") }
        TextButton(enabled = !state.carregando, onClick = alternar) {
            Text(if (cadastro) "Já tenho conta • Entrar" else "Não tem conta? Criar conta")
        }
    }
}

@Composable
private fun PasswordField(label: String, value: String, enabled: Boolean, change: (String) -> Unit) {
    var visivel by remember { mutableStateOf(false) }
    OutlinedTextField(value, change, label = { Text(label) }, enabled = enabled, singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (visivel) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { TextButton(onClick = { visivel = !visivel }) {
            Text(if (visivel) "Ocultar" else "Mostrar")
        } })
}

@Composable
fun PerfilPendente(state: AuthState, salvar: (String) -> Unit, retry: () -> Unit, sair: () -> Unit) {
    var nome by rememberSaveable(state.uid) { mutableStateOf(state.nomeAuth) }
    Page {
        if (state.carregando) CircularProgressIndicator()
        Text("Complete seu perfil", style = MaterialTheme.typography.headlineMedium)
        Text("Sua conta está conectada: ${state.email}. Se o cadastro foi interrompido, use esta conta para concluir o perfil. Não é necessário criar outra conta.")
        state.erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(nome, { nome = it }, label = { Text("Nome") },
            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !state.carregando)
        Button(onClick = { salvar(nome) }, enabled = !state.carregando && nome.trim().length in 2..80) {
            Text("Salvar perfil")
        }
        TextButton(onClick = retry, enabled = !state.carregando) { Text("Consultar perfil novamente") }
        TextButton(onClick = sair, enabled = !state.carregando) { Text("Sair") }
    }
}
