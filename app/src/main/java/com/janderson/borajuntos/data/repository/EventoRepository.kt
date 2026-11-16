package com.janderson.borajuntos.data.repository

import com.google.firebase.firestore.*
import com.janderson.borajuntos.model.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class EventosSnapshot(val itens: List<EventoItem> = emptyList(), val carregando: Boolean = true, val pendente: Boolean = false)
class EventoRepository(private val db: FirebaseFirestore) {
 fun observar(uid: String) = callbackFlow {
  val filhos = mutableListOf<ListenerRegistration>()
  val eventos = mutableMapOf<String,Evento>()
  val pessoas = mutableMapOf<String,List<Participante>>()
  val cache = mutableMapOf<String,Boolean>()
  var ids = emptySet<String>()
  var indiceCache = false
  fun emitir() { trySend(EventosSnapshot(ids.mapNotNull { id -> eventos[id]?.let { EventoItem(it,pessoas[id].orEmpty()) } },
   ids.any { it !in eventos || it !in pessoas }, indiceCache || cache.values.any { it })) }
  val indice = db.collection("usuarios").document(uid).collection("eventos").addSnapshotListener(MetadataChanges.INCLUDE) { snap,erro ->
   if(erro != null) { close(erro); return@addSnapshotListener }
   if(snap == null) return@addSnapshotListener
   indiceCache = snap.metadata.isFromCache || snap.metadata.hasPendingWrites()
   val novos = snap.documents.map { it.id }.toSet()
   if(novos != ids) {
    filhos.forEach { it.remove() }; filhos.clear()
    ids=novos; eventos.clear(); pessoas.clear(); cache.clear()
    ids.forEach { id ->
     val ref=db.collection("eventos").document(id)
     filhos += ref.addSnapshotListener(MetadataChanges.INCLUDE) event@{ doc,falha ->
      if(falha != null) { close(falha); return@event }
      if(doc != null && doc.exists()) {
       eventos[id]=doc.toObject(Evento::class.java)!!.copy(id=id)
       cache[id]=doc.metadata.isFromCache || doc.metadata.hasPendingWrites(); emitir()
      }
     }
     filhos += ref.collection("participantes").addSnapshotListener(MetadataChanges.INCLUDE) people@{ docs,falha ->
      if(falha != null) { close(falha); return@people }
      if(docs != null) {
       pessoas[id]=docs.toObjects(Participante::class.java)
       cache["p"+id]=docs.metadata.isFromCache || docs.metadata.hasPendingWrites(); emitir()
      }
     }
    }
   }
   emitir()
  }
  awaitClose { indice.remove(); filhos.forEach { it.remove() } }
 }
 private suspend fun limparColecao(ref: CollectionReference) {
  while(true) {
   val docs=ref.limit(300).get(Source.SERVER).await()
   if(docs.isEmpty) return
   val batch=db.batch(); docs.documents.forEach {batch.delete(it.reference)}; batch.commit().await()
  }
 }
 suspend fun salvar(e: Evento,uid: String,nome: String,novo: Boolean) {
  require(validarEvento(e)==null) { validarEvento(e).orEmpty() }
  val ref=db.collection("eventos").document(e.id)
  if(!novo) {
   val atividades=ref.collection("roteiro").get(Source.SERVER).await().toObjects(Atividade::class.java)
   require(atividades.all {atividadeDentro(e,it.dia,it.hora)}) {
    "Os novos limites deixam atividades fora do evento. Ajuste o roteiro antes de alterar as datas/horários."
   }
  }
  val dados=mapOf("organizadorId" to uid,"titulo" to e.titulo.trim(),"tipo" to e.tipo,
   "local" to e.local.trim(),"inicio" to e.inicio,"fim" to e.fim,"descricao" to e.descricao,"capaUrl" to e.capaUrl,"status" to e.status,"detalhesLocal" to e.detalhesLocal,"horaInicio" to e.horaInicio,"horaFim" to e.horaFim)
  db.runTransaction { tx ->
   val atual=tx.get(ref)
   if(novo && atual.exists()) {
    check(atual.data==dados) { "Evento já existe. Atualize a lista." }
   } else if(novo) {
    tx.set(ref,dados)
    tx.set(ref.collection("participantes").document(uid),mapOf("uid" to uid,"nome" to nome,"papel" to "organizador","presenca" to "vou"))
    tx.set(db.collection("usuarios").document(uid).collection("eventos").document(e.id),mapOf("eventoId" to e.id))
   } else {
    check(atual.getString("organizadorId")==uid && atual.getString("status")!="excluindo")
    val anterior=atual.toObject(Evento::class.java)!!
    require((atual.getLong("roteiroQuantidade") ?: 0L)==0L ||
      (e.inicio*1440+(e.horaInicio ?: 0L) <= anterior.inicio*1440+(anterior.horaInicio ?: 0L) &&
       e.fim*1440+(e.horaFim ?: 1439L) >= anterior.fim*1440+(anterior.horaFim ?: 1439L))) {
     "Remova as atividades do roteiro antes de reduzir datas ou horários do evento."
    }
    tx.update(ref,dados)
   }
  }.await()
 }
 suspend fun gerarConvite(id: String,uid: String): String {
  val bytes=ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
  val token=bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
  val ref=db.collection("convites").document(token)
  db.runTransaction { tx ->
   val evento=tx.get(db.collection("eventos").document(id))
   check(evento.getString("organizadorId")==uid && evento.getString("status")=="ativo") { "Evento não permite novos convites." }
   tx.set(ref,mapOf("eventoId" to id,"organizadorId" to uid,"ativo" to true))
   tx.set(db.collection("eventos").document(id).collection("convites").document(token),mapOf("token" to token))
  }.await()
  return token
 }
 suspend fun listarConvites(id: String): List<Convite> {
  val marcadores=db.collection("eventos").document(id).collection("convites").get(Source.SERVER).await()
  return marcadores.documents.map { doc ->
   val convite=db.collection("convites").document(doc.id).get(Source.SERVER).await()
   Convite(doc.id,convite.getBoolean("ativo")==true)
  }
 }
 suspend fun revogarConvite(token: String) {
  db.runTransaction { tx ->
   val ref=db.collection("convites").document(token)
   if(tx.get(ref).getBoolean("ativo")==true) tx.update(ref,"ativo",false)
  }.await()
 }
 suspend fun entrarPorCodigo(codigo: String,uid: String,nome: String): String {
  require(codigo.matches(Regex("[0-9a-f]{64}"))) { "Código inválido. Cole somente o código do convite." }
  return db.runTransaction { tx ->
   val convite=tx.get(db.collection("convites").document(codigo))
   check(convite.exists() && convite.getBoolean("ativo")==true) { "Convite inexistente ou revogado." }
   val id=requireNotNull(convite.getString("eventoId"))
   val indice=db.collection("usuarios").document(uid).collection("eventos").document(id)
   // Leitura própria permitida mesmo antes de ingressar; não ler o evento privado.
   if(!tx.get(indice).exists()) {
    tx.set(db.collection("eventos").document(id).collection("participantes").document(uid),
     mapOf("uid" to uid,"nome" to nome,"papel" to "membro","presenca" to "vou","conviteToken" to codigo))
    tx.set(indice,mapOf("eventoId" to id))
   }
   id
  }.await()
 }
 suspend fun presenca(id: String,uid: String,valor: String) {
  require(valor in listOf("vou","talvez","nao_vou"))
  db.runTransaction { tx ->
   val ref=db.collection("eventos").document(id).collection("participantes").document(uid)
   check(tx.get(ref).exists()) { "Participação não encontrada." }
   tx.update(ref,"presenca",valor)
  }.await()
 }
 suspend fun status(id: String,status: String) {
  db.runTransaction { tx ->
   val ref=db.collection("eventos").document(id)
   check(tx.get(ref).getString("status")=="ativo") { "Evento já encerrado." }
   tx.update(ref,"status",status)
  }.await()
 }
 suspend fun excluir(id: String,uid: String) {
  val ref=db.collection("eventos").document(id)
  db.runTransaction { tx ->
   check(tx.get(ref).getString("organizadorId")==uid)
   tx.update(ref,"status","excluindo")
  }.await()
  // Marcadores permitem limpar códigos sem abrir listagem global de convites.
  val convites=ref.collection("convites").get(Source.SERVER).await()
  convites.documents.chunked(200).forEach { grupo ->
   val batch=db.batch()
   grupo.forEach { batch.delete(it.reference); batch.delete(db.collection("convites").document(it.id)) }
   batch.commit().await()
  }
  // Votos são documentos filhos: apagar a enquete não os remove.
  while(true) {
   val enquetes=ref.collection("enquetes").limit(100).get(Source.SERVER).await()
   if(enquetes.isEmpty) break
   enquetes.documents.forEach { enquete ->
    limparColecao(enquete.reference.collection("votos"))
    enquete.reference.delete().await()
   }
  }
  limparColecao(ref.collection("gastos"))
  ref.collection("controle").document("membrosGastos").delete().await()
  limparColecao(ref.collection("tarefas"))
  limparColecao(ref.collection("roteiro"))
  // Preservar organizador até o lote final.
  val pessoas=ref.collection("participantes").get(Source.SERVER).await()
  pessoas.documents.filter { it.id!=uid }.chunked(200).forEach { grupo ->
   val batch=db.batch()
   grupo.forEach { batch.delete(it.reference); batch.delete(db.collection("usuarios").document(it.id).collection("eventos").document(id)) }
   batch.commit().await()
  }
  db.batch().delete(ref.collection("participantes").document(uid))
   .delete(db.collection("usuarios").document(uid).collection("eventos").document(id)).delete(ref).commit().await()
 }
}
