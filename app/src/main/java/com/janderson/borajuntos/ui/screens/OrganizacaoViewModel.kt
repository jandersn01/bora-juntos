package com.janderson.borajuntos.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestoreException
import com.janderson.borajuntos.data.repository.*
import com.janderson.borajuntos.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class OrganizacaoState(val dados: OrganizacaoSnapshot=OrganizacaoSnapshot(),val gravando: Boolean=false,val erro: String?=null,val salvo: Long=0)
class OrganizacaoViewModel(private val repo: OrganizacaoRepository,private val eventoId: String,val uid: String): ViewModel() {
 private val _state=MutableStateFlow(OrganizacaoState())
 val state=_state.asStateFlow()
 private var listener: Job?=null
 fun iniciar() {
  listener?.cancel();_state.update {it.copy(dados=OrganizacaoSnapshot(),erro=null)}
  listener=viewModelScope.launch {
   try {repo.observar(eventoId).collect {dados->_state.update {it.copy(dados=dados)}}}
   catch(e: CancellationException) {throw e}
   catch(e: Exception) {_state.update {it.copy(dados=OrganizacaoSnapshot(carregando=false),erro=mensagem(e))}}
  }
 }
 fun parar() {listener?.cancel();_state.value=OrganizacaoState()}
 private fun executar(acao: suspend ()->Unit) {
  if(_state.value.gravando) return
  _state.update {it.copy(gravando=true,erro=null)}
  viewModelScope.launch {
   try {acao();_state.update {it.copy(salvo=it.salvo+1)}}
   catch(e: CancellationException) {throw e}
   catch(e: Exception) {_state.update {it.copy(erro=mensagem(e))}}
   finally {_state.update {it.copy(gravando=false)}}
  }
 }
 fun enquete(e: Enquete)=executar {repo.enquete(eventoId,e)}
 fun votar(id: String,opcao: Long)=executar {repo.votar(eventoId,id,uid,opcao)}
 fun fechar(id: String)=executar {repo.fechar(eventoId,id)}
 fun tarefa(t: Tarefa)=executar {repo.tarefa(eventoId,t)}
 fun concluir(t: Tarefa)=executar {repo.concluir(eventoId,t.id,!t.concluida)}
 fun atividade(e: Evento,a: Atividade)=executar {repo.atividade(e,a)}
 fun gasto(id: String,descricao: String,valor: String,pagador: String,participantes: List<String>,autor: String)=executar {
  repo.gasto(eventoId,Gasto(id,descricao,CalculoGastos.converterBR(valor),pagador,participantes,autor))
 }
 fun excluir(colecao: String,id: String)=executar {repo.excluir(eventoId,colecao,id)}
}
private fun mensagem(e: Exception)=when(e) {
 is FirebaseFirestoreException -> when(e.code) {
  FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Ação não permitida: confira sua permissão, o estado do evento e os limites atuais."
  FirebaseFirestoreException.Code.UNAVAILABLE -> "Sem conexão. Tente novamente online."
  else -> "Falha no Firestore. Tente novamente."
 }
 else -> e.message ?: "Não foi possível concluir."
}
