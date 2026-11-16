package com.janderson.borajuntos.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.janderson.borajuntos.model.*
import java.util.UUID

@Composable
fun OrganizacaoTela(item: EventoItem?,secao: String,vm: OrganizacaoViewModel,voltar: ()->Unit) {
 val state by vm.state.collectAsStateWithLifecycle()
 DisposableEffect(vm) {vm.iniciar();onDispose {vm.parar()}}
 BackHandler(enabled=state.gravando) {}
 if(secao=="gastos") {GastosTela(item,state,vm,voltar);return}
 var editor by rememberSaveable {mutableStateOf<String?>(null)}
 var excluir by rememberSaveable {mutableStateOf<String?>(null)}
 LaunchedEffect(state.salvo) {if(state.salvo>0) {editor=null;excluir=null}}
 val e=item?.evento
 val organizador=e?.organizadorId==vm.uid
 val ativo=e?.status=="ativo"
 val titulo=when(secao) {"enquetes"->"Votações";"tarefas"->"Tarefas";else->"Roteiro"}
 LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  item {
   TextButton(onClick=voltar,enabled=!state.gravando) {Text("Voltar")}
   Text(titulo,style=MaterialTheme.typography.headlineMedium)
   if(!ativo) Text("Evento encerrado ou indisponível. Somente leitura.")
   if(ativo && (secao!="enquetes" || organizador)) Button(onClick={editor="novo"},enabled=!state.gravando) {Text("Adicionar")}
   if(state.dados.carregando || state.gravando) CircularProgressIndicator()
   if(state.dados.pendente) Text("Dados em cache ou pendentes de confirmação.")
   state.erro?.let {Text(it,color=MaterialTheme.colorScheme.error);TextButton(onClick=vm::iniciar,enabled=!state.gravando) {Text("Tentar novamente")}}
  }
  if(secao=="enquetes") {
   if(state.dados.enquetes.isEmpty() && !state.dados.carregando) item {Text("Nenhuma votação criada.")}
   items(state.dados.enquetes,key={it.id}) {poll->
    Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
     Text(poll.pergunta,style=MaterialTheme.typography.titleLarge)
     Text(if(poll.aberta) "Aberta" else "Encerrada")
     val votos=state.dados.votos[poll.id]
     if(votos==null) Text("Carregando votos…")
     else {
      poll.opcoes.forEachIndexed {index,opcao->
       val quantidade=votos.values.count {it.opcao==index.toLong()}
       OutlinedButton(onClick={vm.votar(poll.id,index.toLong())},enabled=ativo && poll.aberta && !state.gravando) {
        Text("${if(votos[vm.uid]?.opcao==index.toLong()) "✓ " else ""}${opcao} • ${quantidade} voto(s)")
       }
      }
      Text("Total: ${votos.size} voto(s)")
     }
     if(organizador && poll.aberta) TextButton(onClick={vm.fechar(poll.id)},enabled=ativo && !state.gravando) {Text("Encerrar votação")}
    }}
   }
  } else if(secao=="tarefas") {
   if(state.dados.tarefas.isEmpty() && !state.dados.carregando) item {Text("Nenhuma tarefa criada.")}
   items(state.dados.tarefas,key={it.id}) {t->
    Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp)) {
     Text(t.descricao,style=MaterialTheme.typography.titleLarge)
     Text("Responsável: "+(item?.participantes?.find {it.uid==t.responsavelId}?.nome ?: "Membro indisponível"))
     Text(if(t.concluida) "Concluída" else "Pendente")
     if(t.responsavelId==vm.uid || organizador) TextButton(onClick={vm.concluir(t)},enabled=ativo && !state.gravando) {Text(if(t.concluida) "Reabrir tarefa" else "Concluir tarefa")}
     if(t.autorId==vm.uid || organizador) {
      TextButton(onClick={editor=t.id},enabled=ativo && !state.gravando) {Text("Editar")}
      TextButton(onClick={excluir=t.id},enabled=ativo && !state.gravando) {Text("Excluir")}
     }
    }}
   }
  } else {
   if(state.dados.roteiro.isEmpty() && !state.dados.carregando) item {Text("Nenhuma atividade criada.")}
   items(state.dados.roteiro,key={it.id}) {a->
    Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp)) {
     Text("${dataTexto(a.dia)} • ${horaTexto(a.hora)}")
     Text(a.descricao,style=MaterialTheme.typography.titleLarge)
     if(a.autorId==vm.uid || organizador) {
      TextButton(onClick={editor=a.id},enabled=ativo && !state.gravando) {Text("Editar")}
      TextButton(onClick={excluir=a.id},enabled=ativo && !state.gravando) {Text("Excluir")}
     }
    }}
   }
  }
 }
 if(editor!=null && e!=null) key(editor) {
  when(secao) {
   "enquetes" -> EditorEnquete(state,vm,{editor=null})
   "tarefas" -> EditorTarefa(state.dados.tarefas.find {it.id==editor},item.participantes,state,vm,{editor=null})
   else -> EditorAtividade(state.dados.roteiro.find {it.id==editor},e,state,vm,{editor=null})
  }
 }
 if(excluir!=null) AlertDialog(onDismissRequest={if(!state.gravando) excluir=null},title={Text("Excluir item?")},
  text={Column {Text("Esta ação é definitiva.");state.erro?.let {Text(it)}}},
  confirmButton={TextButton(onClick={vm.excluir(secao,excluir!!)},enabled=!state.gravando) {Text("Excluir")}},
  dismissButton={TextButton(onClick={excluir=null},enabled=!state.gravando) {Text("Manter")}})
}

@Composable
fun EditorBase(titulo: String,state: OrganizacaoState,cancelar: ()->Unit,salvar: ()->Unit,content: @Composable ColumnScope.()->Unit) {
 AlertDialog(onDismissRequest={if(!state.gravando) cancelar()},title={Text(titulo)},
  text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
   content();state.erro?.let {Text(it,color=MaterialTheme.colorScheme.error)}
   if(state.gravando) CircularProgressIndicator()
  }},
  confirmButton={TextButton(onClick=salvar,enabled=!state.gravando) {Text("Salvar")}},
  dismissButton={TextButton(onClick=cancelar,enabled=!state.gravando) {Text("Cancelar")}})
}
@Composable
private fun EditorEnquete(state: OrganizacaoState,vm: OrganizacaoViewModel,cancelar: ()->Unit) {
 val id=rememberSaveable {UUID.randomUUID().toString()}
 var pergunta by rememberSaveable {mutableStateOf("")}
 var opcoes by rememberSaveable {mutableStateOf("")}
 EditorBase("Nova votação",state,cancelar,{
  vm.enquete(Enquete(id,pergunta,opcoes.lines().map {it.trim()},true,vm.uid))
 }) {
  OutlinedTextField(pergunta,{pergunta=it},label={Text("Pergunta")},enabled=!state.gravando)
  OutlinedTextField(opcoes,{opcoes=it},label={Text("Opções • uma por linha (2 a 8)")},minLines=3,enabled=!state.gravando)
 }
}
@Composable
private fun EditorTarefa(original: Tarefa?,pessoas: List<Participante>,state: OrganizacaoState,vm: OrganizacaoViewModel,cancelar: ()->Unit) {
 val id=rememberSaveable {original?.id ?: UUID.randomUUID().toString()}
 var descricao by rememberSaveable {mutableStateOf(original?.descricao.orEmpty())}
 var responsavel by rememberSaveable {mutableStateOf(original?.responsavelId.orEmpty())}
 EditorBase("Tarefa",state,cancelar,{
  vm.tarefa(Tarefa(id,descricao,responsavel,original?.concluida ?: false,original?.autorId ?: vm.uid))
 }) {
  OutlinedTextField(descricao,{descricao=it},label={Text("Descrição")},enabled=!state.gravando)
  Text("Responsável")
  pessoas.forEach {p->Row {
   RadioButton(selected=responsavel==p.uid,onClick={responsavel=p.uid},enabled=!state.gravando);Text(p.nome,Modifier.padding(top=12.dp))
  }}
 }
}
@Composable
private fun EditorAtividade(original: Atividade?,evento: Evento,state: OrganizacaoState,vm: OrganizacaoViewModel,cancelar: ()->Unit) {
 val id=rememberSaveable {original?.id ?: UUID.randomUUID().toString()}
 var descricao by rememberSaveable {mutableStateOf(original?.descricao.orEmpty())}
 var dia by rememberSaveable {mutableLongStateOf(original?.dia ?: evento.inicio)}
 var hora by rememberSaveable {mutableStateOf(original?.hora)}
 var erro by remember {mutableStateOf<String?>(null)}
 EditorBase("Atividade do roteiro",state,cancelar,{
  if(hora==null) erro="Escolha o horário da atividade."
  else {erro=null;vm.atividade(evento,Atividade(id,descricao,dia,hora!!,original?.autorId ?: vm.uid))}
 }) {
  OutlinedTextField(descricao,{descricao=it},label={Text("Atividade")},enabled=!state.gravando)
  DataCampo("Dia",dia,!state.gravando) {dia=it}
  HoraCampo("Horário da atividade",hora,!state.gravando) {hora=it}
  erro?.let {Text(it,color=MaterialTheme.colorScheme.error)}
 }
}
