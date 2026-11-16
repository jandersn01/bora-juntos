package com.janderson.borajuntos.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestoreException
import com.janderson.borajuntos.data.repository.*
import com.janderson.borajuntos.data.remote.ViaCep
import com.janderson.borajuntos.model.Evento
import com.janderson.borajuntos.model.Convite
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.HttpException
import java.io.IOException

data class EventosState(val dados: EventosSnapshot=EventosSnapshot(),val erro: String?=null,
 val gravando: Boolean=false,val salvo: String?=null,val buscandoCep: Boolean=false,
 val endereco: String?=null,val erroCep: String?=null,val cepConsultado: String?=null,
 val convites: List<Convite> = emptyList(),val eventoIngresso: String?=null)
class EventosViewModel(private val repo: EventoRepository,private val cep: ViaCep,
 private val uid: String,private val nome: String): ViewModel() {
 private val _state=MutableStateFlow(EventosState())
 val state=_state.asStateFlow()
 private var listener: Job?=null
 private var cepJob: Job?=null
 private var cepGeracao=0L
 fun iniciar() {
  listener?.cancel(); _state.update { it.copy(dados=EventosSnapshot(),erro=null) }
  listener=viewModelScope.launch {
   try { repo.observar(uid).collect { dados -> _state.update { it.copy(dados=dados) } } }
   catch(e: CancellationException) { throw e }
   catch(e: Exception) { _state.update { it.copy(dados=EventosSnapshot(carregando=false),erro=mensagemEvento(e)) } }
  }
 }
 fun parar() { listener?.cancel(); cepJob?.cancel(); _state.value=EventosState() }
 fun limparResultado() { alterarCep(); _state.update { it.copy(salvo=null,erro=null,convites=emptyList(),eventoIngresso=null) } }
 private fun executar(id: String,acao: suspend ()->Unit) {
  if(_state.value.gravando) return
  _state.update { it.copy(gravando=true,erro=null,salvo=null) }
  viewModelScope.launch {
   try { acao(); _state.update { it.copy(salvo=id) } }
   catch(e: CancellationException) { throw e }
   catch(e: Exception) { _state.update { it.copy(erro=mensagemEvento(e)) } }
   finally { _state.update { it.copy(gravando=false) } }
  }
 }
 fun salvar(e: Evento,novo: Boolean)=executar(e.id) { repo.salvar(e,uid,nome,novo) }
 fun mudarStatus(id: String,status: String)=executar(id) { repo.status(id,status) }
 fun excluir(id: String)=executar("excluido") { repo.excluir(id,uid); iniciar() }
 fun alterarCep() {
  cepGeracao++; cepJob?.cancel()
  _state.update { it.copy(buscandoCep=false,endereco=null,cepConsultado=null,erroCep=null) }
 }
 fun consumirEndereco() { _state.update { it.copy(endereco=null,cepConsultado=null) } }
 fun buscarCep(valor: String) {
  alterarCep()
  val geracao=cepGeracao
  _state.update { it.copy(buscandoCep=true) }
  cepJob=viewModelScope.launch {
   try {
    val texto=cep.buscar(valor)
    if(geracao==cepGeracao) _state.update { it.copy(endereco=texto,cepConsultado=valor) }
   }
   catch(e: CancellationException) { throw e }
   catch(e: Exception) {
    val mensagem=when(e) {
     is HttpException -> "Erro HTTP ${e.code()} no ViaCEP."
     is IOException -> "Falha de rede. O endereço existente foi mantido."
     else -> e.message ?: "Falha na consulta."
    }
    if(geracao==cepGeracao) _state.update { it.copy(erroCep=mensagem) }
   }
   finally { if(geracao==cepGeracao) _state.update { it.copy(buscandoCep=false) } }
  }
 }
 fun carregarConvites(id: String)=executar("convites") {
  val convites=repo.listarConvites(id); _state.update { it.copy(convites=convites) }
 }
 fun gerarConvite(id: String)=executar("convites") {
  repo.gerarConvite(id,uid)
  val convites=repo.listarConvites(id); _state.update { it.copy(convites=convites) }
 }
 fun revogarConvite(id: String,token: String)=executar("convites") {
  repo.revogarConvite(token)
  val convites=repo.listarConvites(id); _state.update { it.copy(convites=convites) }
 }
 fun entrar(codigo: String)=executar("ingresso") {
  val id=repo.entrarPorCodigo(codigo.trim(),uid,nome)
  _state.update { it.copy(eventoIngresso=id) }
 }
 fun presenca(id: String,valor: String)=executar("presenca") { repo.presenca(id,uid,valor) }
}
private fun mensagemEvento(e: Exception): String = when(e) {
 is FirebaseFirestoreException -> when(e.code) {
  FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Acesso negado. Confira participação e regras publicadas."
  FirebaseFirestoreException.Code.UNAVAILABLE -> "Sem conexão com o servidor. Tente novamente online."
  else -> "Falha no Firestore. Tente novamente."
 }
 else -> e.message ?: "Não foi possível concluir. Tente novamente."
}
