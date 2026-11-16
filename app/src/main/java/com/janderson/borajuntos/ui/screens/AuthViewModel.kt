package com.janderson.borajuntos.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestoreException
import com.janderson.borajuntos.data.repository.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AuthState(
    val carregando: Boolean = true, val uid: String? = null,
    val email: String = "", val nomeAuth: String = "", val perfil: Perfil? = null,
    val erro: String? = null
)

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(AuthState())
    val state = _state.asStateFlow()
    private var operando = false
    private val listener = FirebaseAuth.AuthStateListener {
        if (!operando) atualizar()
    }
    init { repository.observar(listener) }

    private suspend fun carregar() {
        val user = repository.usuario
        _state.value = AuthState(carregando = user != null, uid = user?.uid,
            email = user?.email.orEmpty(), nomeAuth = user?.displayName.orEmpty())
        if (user != null) {
            val perfil = repository.lerPerfil()
            if (repository.usuario?.uid == user.uid)
                _state.value = _state.value.copy(carregando = false, perfil = perfil)
        }
    }

    private fun executar(acao: suspend () -> Unit) {
        if (operando) return
        operando = true
        _state.value = _state.value.copy(carregando = true, erro = null)
        viewModelScope.launch {
            try {
                acao()
                carregar()
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                val user = repository.usuario
                _state.value = AuthState(carregando = false, uid = user?.uid,
                    email = user?.email.orEmpty(), nomeAuth = user?.displayName.orEmpty(),
                    erro = "Tempo de conexão esgotado. Tente novamente com a mesma conta.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val user = repository.usuario
                _state.value = AuthState(carregando = false, uid = user?.uid,
                    email = user?.email.orEmpty(), nomeAuth = user?.displayName.orEmpty(),
                    erro = mensagem(e))
            } finally { operando = false }
        }
    }
    fun atualizar() = executar {}
    fun entrar(email: String, senha: String) = executar { repository.entrar(email.trim(), senha) }
    fun cadastrar(nome: String, email: String, senha: String) = executar {
        repository.cadastrar(nome.trim(), email.trim(), senha)
        repository.salvarPerfil(nome.trim())
    }
    fun completarPerfil(nome: String) = executar { repository.salvarPerfil(nome.trim()) }
    fun sair() {
        if (operando) return
        repository.sair()
        _state.value = AuthState(carregando = false)
    }
    override fun onCleared() { repository.remover(listener) }
}

private fun mensagem(e: Exception): String = when (e) {
    is FirebaseAuthUserCollisionException -> "Este e-mail já possui uma conta. Entre com sua senha."
    is FirebaseAuthWeakPasswordException -> "A senha não atende à política do projeto Firebase."
    is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException ->
        "E-mail ou senha inválidos."
    is FirebaseNetworkException -> "Sem conexão. Verifique a rede e tente novamente."
    is FirebaseTooManyRequestsException -> "Muitas tentativas. Aguarde e tente novamente."
    is FirebaseFirestoreException -> when (e.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Sem permissão para o perfil. Confira as regras do Firestore."
        FirebaseFirestoreException.Code.UNAVAILABLE -> "Firestore indisponível. Verifique a conexão e tente novamente."
        else -> "Não foi possível acessar o perfil. Tente novamente."
    }
    else -> "Não foi possível concluir. Confira a conexão e a configuração Firebase e tente novamente."
}
