package com.janderson.borajuntos.data.repository

import com.google.firebase.firestore.*
import com.janderson.borajuntos.model.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class OrganizacaoSnapshot(
 val enquetes: List<Enquete> = emptyList(), val tarefas: List<Tarefa> = emptyList(),
 val roteiro: List<Atividade> = emptyList(), val votos: Map<String,Map<String,Voto>> = emptyMap(),
 val gastos: List<Gasto> = emptyList(), val carregando: Boolean=true, val pendente: Boolean=false
)
class OrganizacaoRepository(private val db: FirebaseFirestore) {
 fun observar(id: String)=callbackFlow {
  val ref=db.collection("eventos").document(id)
  var dados=OrganizacaoSnapshot()
  val prontos=mutableSetOf<String>()
  val cache=mutableMapOf<String,Boolean>()
  val votos=mutableMapOf<String,Map<String,Voto>>()
  val listenersVotos=mutableMapOf<String,ListenerRegistration>()
  fun emitir() {trySend(dados.copy(votos=votos.toMap(),
   carregando=prontos.size<4 || dados.enquetes.any {it.id !in votos},pendente=cache.values.any {it}))}
  fun metadados(chave: String,snapshot: QuerySnapshot) {
   prontos.add(chave);cache[chave]=snapshot.metadata.isFromCache || snapshot.metadata.hasPendingWrites()
  }
  val enquetes=ref.collection("enquetes").addSnapshotListener(MetadataChanges.INCLUDE) poll@{ snap,erro ->
   if(erro!=null) {close(erro);return@poll}
   if(snap==null) return@poll
   dados=dados.copy(enquetes=snap.documents.map {it.toObject(Enquete::class.java)!!.copy(id=it.id)})
   metadados("enquetes",snap)
   val ids=dados.enquetes.map {it.id}.toSet()
   listenersVotos.keys.toList().filter {it !in ids}.forEach {chave->
    listenersVotos.remove(chave)?.remove();votos.remove(chave);cache.remove("voto-"+chave)
   }
   ids.filter {it !in listenersVotos}.forEach {chave->
    listenersVotos[chave]=ref.collection("enquetes").document(chave).collection("votos")
     .addSnapshotListener(MetadataChanges.INCLUDE) vote@{ docs,falha ->
      if(falha!=null) {close(falha);return@vote}
      if(docs!=null) {
       votos[chave]=docs.documents.associate {it.id to it.toObject(Voto::class.java)!!}
       cache["voto-"+chave]=docs.metadata.isFromCache || docs.metadata.hasPendingWrites();emitir()
      }
     }
   }
   emitir()
  }
  val tarefas=ref.collection("tarefas").addSnapshotListener(MetadataChanges.INCLUDE) task@{ snap,erro ->
   if(erro!=null) {close(erro);return@task}
   if(snap!=null) {
    dados=dados.copy(tarefas=snap.documents.map {it.toObject(Tarefa::class.java)!!.copy(id=it.id)})
    metadados("tarefas",snap);emitir()
   }
  }
  val roteiro=ref.collection("roteiro").addSnapshotListener(MetadataChanges.INCLUDE) activity@{ snap,erro ->
   if(erro!=null) {close(erro);return@activity}
   if(snap!=null) {
    dados=dados.copy(roteiro=snap.documents.map {it.toObject(Atividade::class.java)!!.copy(id=it.id)}
     .sortedWith(compareBy<Atividade> {it.dia}.thenBy {it.hora}.thenBy {it.id}))
    metadados("roteiro",snap);emitir()
   }
  }
  val gastos=ref.collection("gastos").addSnapshotListener(MetadataChanges.INCLUDE) expense@{ snap,erro ->
   if(erro!=null) {close(erro);return@expense}
   if(snap!=null) {
    dados=dados.copy(gastos=snap.documents.map {it.toObject(Gasto::class.java)!!.copy(id=it.id)})
    metadados("gastos",snap);emitir()
   }
  }
  awaitClose {enquetes.remove();tarefas.remove();roteiro.remove();gastos.remove();listenersVotos.values.forEach {it.remove()}}
 }
 private suspend fun salvar(eventoId: String,colecao: String,id: String,dados: Map<String,Any>) {
  db.runTransaction {tx->
   val pai=db.collection("eventos").document(eventoId)
   val evento=if(colecao=="roteiro") tx.get(pai) else null
   val ref=pai.collection(colecao).document(id)
   val atual=tx.get(ref)
   if(atual.data!=dados) {
    tx.set(ref,dados)
    if(colecao=="roteiro" && !atual.exists())
     tx.update(pai,mapOf("roteiroQuantidade" to ((evento?.getLong("roteiroQuantidade") ?: 0L)+1L),"roteiroAlteracaoId" to id))
   }
  }.await()
 }
 suspend fun enquete(evento: String,e: Enquete) {
  require(e.pergunta.trim().length in 1..200) {"Informe uma pergunta de até 200 caracteres."}
  require(e.opcoes.size in 2..8 && e.opcoes.distinct().size==e.opcoes.size && e.opcoes.all {it.length in 1..100}) {"Informe 2 a 8 opções diferentes, de até 100 caracteres cada."}
  salvar(evento,"enquetes",e.id,mapOf("pergunta" to e.pergunta.trim(),"opcoes" to e.opcoes,"aberta" to true,"autorId" to e.autorId))
 }
 suspend fun votar(evento: String,enquete: String,uid: String,opcao: Long) {
  salvar(evento,"enquetes/"+enquete+"/votos",uid,mapOf("opcao" to opcao))
 }
 suspend fun fechar(evento: String,id: String) {
  db.runTransaction {tx->
   val ref=db.collection("eventos").document(evento).collection("enquetes").document(id)
   tx.get(ref);tx.update(ref,"aberta",false)
  }.await()
 }
 suspend fun tarefa(evento: String,t: Tarefa) {
  require(t.descricao.trim().length in 1..200) {"Informe uma tarefa de até 200 caracteres."}
  require(t.responsavelId.isNotBlank()) {"Escolha um responsável."}
  salvar(evento,"tarefas",t.id,mapOf("descricao" to t.descricao.trim(),"responsavelId" to t.responsavelId,"autorId" to t.autorId,"concluida" to t.concluida))
 }
 suspend fun concluir(evento: String,id: String,valor: Boolean) {
  db.runTransaction {tx->
   val ref=db.collection("eventos").document(evento).collection("tarefas").document(id)
   tx.get(ref);tx.update(ref,"concluida",valor)
  }.await()
 }
 suspend fun atividade(evento: Evento,a: Atividade) {
  require(a.descricao.trim().length in 1..200) {"Informe uma atividade de até 200 caracteres."}
  require(atividadeDentro(evento,a.dia,a.hora)) {"Data/hora fora dos limites do evento."}
  salvar(evento.id,"roteiro",a.id,mapOf("descricao" to a.descricao.trim(),"dia" to a.dia,"hora" to a.hora,"autorId" to a.autorId))
 }
 suspend fun gasto(evento: String,g: Gasto) {
  require(g.descricao.trim().length in 1..200) {"Informe uma descrição de até 200 caracteres."}
  require(g.pagadorId.isNotBlank()) {"Escolha o pagador."}
  require(g.participantes.isNotEmpty() && g.participantes.distinct().size==g.participantes.size) {"Selecione os participantes da divisão."}
  CalculoGastos.quotas(g.valorCentavos,g.participantes)
  // Cada inclusão é validada contra a participação real no servidor.
  // Esse índice apenas evita consultas por participante nas regras de cada gasto; não concede acesso.
  val cadastro=db.collection("eventos").document(evento).collection("controle").document("membrosGastos")
  (g.participantes+g.pagadorId).distinct().sorted().forEach {uid->
   db.runTransaction {tx->
    val atual=tx.get(cadastro)
    val uids=(atual.get("uids") as? List<*>)?.filterIsInstance<String>().orEmpty()
    if(uid !in uids) tx.set(cadastro,mapOf("uids" to (uids+uid),"ultimoUid" to uid))
   }.await()
  }
  salvar(evento,"gastos",g.id,mapOf("descricao" to g.descricao.trim(),"valorCentavos" to g.valorCentavos,
   "pagadorId" to g.pagadorId,"participantes" to g.participantes.sorted(),"autorId" to g.autorId))
 }
 suspend fun excluir(evento: String,colecao: String,id: String) {
  require(colecao in listOf("tarefas","roteiro","gastos"))
  db.runTransaction {tx->
   val pai=db.collection("eventos").document(evento)
   val dados=if(colecao=="roteiro") tx.get(pai) else null
   val ref=pai.collection(colecao).document(id)
   if(tx.get(ref).exists()) {
    tx.delete(ref)
    if(colecao=="roteiro")
     tx.update(pai,mapOf("roteiroQuantidade" to ((dados?.getLong("roteiroQuantidade") ?: 0L)-1L),"roteiroAlteracaoId" to id))
   }
  }.await()
 }
}
