package com.janderson.borajuntos.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import coil.compose.SubcomposeAsyncImage
import com.janderson.borajuntos.model.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

fun dataTexto(dia: Long): String=LocalDate.ofEpochDay(dia).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

@Composable
fun Capa(url: String) {
 Box(Modifier.fillMaxWidth().height(150.dp),contentAlignment=Alignment.Center) {
  if(url.isBlank()) Text("♙",style=MaterialTheme.typography.displayMedium)
  else SubcomposeAsyncImage(model=url,contentDescription="Capa do evento",contentScale=ContentScale.Crop,
   modifier=Modifier.fillMaxSize(),loading={ Text("Carregando capa") },error={ Text("Imagem indisponível • ♙") })
 }
}

@Composable
fun ListaEventos(state: EventosState,inicio: Boolean,nome: String,novo: ()->Unit,abrir: (String)->Unit,retry: ()->Unit) {
 var busca by rememberSaveable { mutableStateOf("") }
 var tipo by rememberSaveable { mutableStateOf("Todos") }
 var filtro by rememberSaveable { mutableStateOf("Todos") }
 val hoje=LocalDate.now().toEpochDay()
 fun categoria(e: Evento)= when { e.status!="ativo" -> e.status; e.fim<hoje -> "passados"; else -> "proximos" }
 val itens=state.dados.itens.filter { it.evento.titulo.contains(busca,true) && (tipo=="Todos" || it.evento.tipo==tipo)
  && (filtro=="Todos" || categoria(it.evento)==filtro) }.sortedBy { it.evento.inicio }
 Scaffold(floatingActionButton={ FloatingActionButton(onClick=novo,containerColor=Color(0xFFFF7043)) { Text("+ Novo evento",Modifier.padding(12.dp)) } }) { pad ->
  LazyColumn(Modifier.fillMaxSize().padding(pad).widthIn(max=800.dp),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
   item { Text(if(inicio) "Olá, ${nome}!" else "Meus eventos",style=MaterialTheme.typography.headlineMedium) }
   if(!inicio) item {
    OutlinedTextField(busca,{busca=it},label={Text("Buscar por título")},modifier=Modifier.fillMaxWidth(),singleLine=true)
    Escolha("Tipo",listOf("Todos")+tiposEvento,tipo) {tipo=it}
    Escolha("Status",listOf("Todos","proximos","passados","cancelado","concluido","excluindo"),filtro) {filtro=it}
   }
   if(state.dados.pendente) item { Text("Dados em cache ou pendentes. Ainda sem confirmação do servidor.") }
   if(state.dados.carregando) item { CircularProgressIndicator() }
   state.erro?.let { mensagem -> item { Text(mensagem,color=MaterialTheme.colorScheme.error); TextButton(onClick=retry) {Text("Tentar novamente")} } }
   if(!state.dados.carregando && state.erro==null && itens.isEmpty()) item { Text("Nenhum evento encontrado. Crie seu primeiro evento.") }
   val grupos=if(inicio) listOf("proximos","passados","cancelado","concluido","excluindo") else listOf("Todos")
   grupos.forEach { grupo ->
    val lista=if(grupo=="Todos") itens else itens.filter {categoria(it.evento)==grupo}.take(3)
    if(lista.isNotEmpty() && inicio) item {Text(rotuloStatus(grupo),style=MaterialTheme.typography.titleLarge)}
    items(lista,key={it.evento.id}) { item ->
     Card(onClick={abrir(item.evento.id)},modifier=Modifier.fillMaxWidth()) {
      Capa(item.evento.capaUrl)
      Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
       Text(item.evento.titulo,style=MaterialTheme.typography.titleLarge)
       Text("${dataTexto(item.evento.inicio)} a ${dataTexto(item.evento.fim)}")
       Text(item.evento.local)
       Text("${item.participantes.count {it.presenca=="vou"}} confirmados • ${rotuloStatus(categoria(item.evento))}")
      }
     }
    }
   }
   item {Spacer(Modifier.height(85.dp))}
  }
 }
}
fun rotuloStatus(s: String)=when(s) { "proximos"->"Próximos";"passados"->"Passados";"cancelado"->"Cancelado";"concluido"->"Concluído";"excluindo"->"Exclusão pendente";"ativo"->"Ativo";else->s }

@Composable
fun Escolha(label: String,opcoes: List<String>,valor: String,selecionar: (String)->Unit) {
 Text(label,style=MaterialTheme.typography.labelLarge)
 Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
  opcoes.forEach { opcao -> FilterChip(selected=opcao==valor,onClick={selecionar(opcao)},label={Text(rotuloPresenca(rotuloStatus(opcao)))}) }
 }
}

@Composable
fun FormularioEvento(original: Evento?,state: EventosState,vm: EventosViewModel,voltar: ()->Unit) {
 val id=rememberSaveable { original?.id ?: UUID.randomUUID().toString() }
 var titulo by rememberSaveable {mutableStateOf(original?.titulo.orEmpty())}
 var tipo by rememberSaveable {mutableStateOf(original?.tipo ?: "Viagem")}
 var local by rememberSaveable {mutableStateOf(original?.local.orEmpty())}
 var detalhesLocal by rememberSaveable {mutableStateOf(original?.detalhesLocal.orEmpty())}
 var inicio by rememberSaveable {mutableLongStateOf(original?.inicio ?: LocalDate.now().toEpochDay())}
 var fim by rememberSaveable {mutableLongStateOf(original?.fim ?: inicio)}
 var horaInicio by rememberSaveable {mutableStateOf(original?.horaInicio)}
 var horaFim by rememberSaveable {mutableStateOf(original?.horaFim)}
 var descricao by rememberSaveable {mutableStateOf(original?.descricao.orEmpty())}
 var capa by rememberSaveable {mutableStateOf(original?.capaUrl.orEmpty())}
 var cep by rememberSaveable {mutableStateOf("")}
 var erro by remember {mutableStateOf<String?>(null)}
 LaunchedEffect(state.endereco,state.cepConsultado) {
  if(state.endereco!=null && state.cepConsultado==cep) {
   local=state.endereco; vm.consumirEndereco()
  }
 }
 DisposableEffect(vm) {onDispose {vm.alterarCep()}}
 LaunchedEffect(state.salvo) {if(state.salvo==id) {vm.limparResultado();voltar()}}
 Page {
  Text(if(original==null) "Novo evento" else "Editar evento",style=MaterialTheme.typography.headlineMedium)
  OutlinedTextField(titulo,{titulo=it},label={Text("Título")},modifier=Modifier.fillMaxWidth(),enabled=!state.gravando)
  Escolha("Tipo",tiposEvento,tipo) {if(!state.gravando) tipo=it}
  OutlinedTextField(cep,{cep=it;vm.alterarCep()},label={Text("CEP • oito dígitos")},singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!state.gravando)
  TextButton(onClick={vm.buscarCep(cep)},enabled=!state.buscandoCep && !state.gravando) {Text("Buscar CEP")}
  if(state.buscandoCep) CircularProgressIndicator()
  state.erroCep?.let {Text(it,color=MaterialTheme.colorScheme.error)}
  OutlinedTextField(local,{local=it},label={Text("Endereço")},modifier=Modifier.fillMaxWidth(),enabled=!state.gravando)
  OutlinedTextField(detalhesLocal,{detalhesLocal=it},label={Text("Detalhes do local (opcional)")},
   supportingText={Text("Número, complemento, estabelecimento ou referência")},
   modifier=Modifier.fillMaxWidth(),enabled=!state.gravando)
  DataCampo("Início",inicio,!state.gravando) {inicio=it}
  HoraCampo("Horário de início (opcional)",horaInicio,!state.gravando) {horaInicio=it}
  DataCampo("Fim",fim,!state.gravando) {fim=it}
  HoraCampo("Horário de fim (opcional)",horaFim,!state.gravando) {horaFim=it}
  OutlinedTextField(descricao,{descricao=it},label={Text("Descrição")},modifier=Modifier.fillMaxWidth(),enabled=!state.gravando)
  OutlinedTextField(capa,{capa=it},label={Text("Capa opcional • URL HTTPS")},modifier=Modifier.fillMaxWidth(),enabled=!state.gravando)
  (erro ?: state.erro)?.let {Text(it,color=MaterialTheme.colorScheme.error)}
  if(state.gravando) {CircularProgressIndicator();Text("Aguardando confirmação do servidor…")}
  Button(onClick={
   val evento=Evento(id,original?.organizadorId.orEmpty(),titulo,tipo,local,inicio,fim,descricao,capa.trim(),original?.status ?: "ativo",detalhesLocal,horaInicio,horaFim)
   erro=validarEvento(evento)
   if(erro==null) vm.salvar(evento,original==null)
  },enabled=!state.gravando,modifier=Modifier.fillMaxWidth()) {Text("Salvar evento")}
  TextButton(onClick=voltar,enabled=!state.gravando) {Text("Voltar")}
 }
}
@Composable
fun DataCampo(label: String,dia: Long,enabled: Boolean,change: (Long)->Unit) {
 val context=LocalContext.current
 OutlinedButton(enabled=enabled,onClick={
  val d=LocalDate.ofEpochDay(dia)
  DatePickerDialog(context,{_,y,m,day->change(LocalDate.of(y,m+1,day).toEpochDay())},d.year,d.monthValue-1,d.dayOfMonth).show()
 }) {Text("${label}: ${dataTexto(dia)}")}
}

@Composable
fun DetalhesEvento(item: EventoItem?,state: EventosState,uid: String,vm: EventosViewModel,editar: ()->Unit,voltar: ()->Unit,organizacao: (String)->Unit) {
 var confirmar by remember {mutableStateOf(false)}
 LaunchedEffect(state.salvo) {if(state.salvo=="excluido") {vm.limparResultado();voltar()}}
 Page {
  TextButton(onClick=voltar,enabled=!state.gravando) {Text("Voltar")}
  if(item==null) {
   Text(if(state.dados.carregando) "Carregando evento…" else "Evento indisponível.")
   state.erro?.let {Text(it)}
   TextButton(onClick=vm::iniciar) {Text("Tentar novamente")}
  } else {
   val e=item.evento
   Capa(e.capaUrl);Text(e.titulo,style=MaterialTheme.typography.headlineMedium)
   Text("${dataTexto(e.inicio)} a ${dataTexto(e.fim)} • ${e.tipo}")
   e.horaInicio?.let {Text("Início: ${horaTexto(it)}")}
   e.horaFim?.let {Text("Fim: ${horaTexto(it)}")}
   listOf("enquetes" to "Votações","tarefas" to "Tarefas","roteiro" to "Roteiro","gastos" to "Gastos").forEach { (rota,label) ->
    OutlinedButton(onClick={organizacao(rota)},enabled=!state.gravando) {Text(label)}
   }
   Text(e.local)
   if(e.detalhesLocal.isNotBlank()) Text(e.detalhesLocal)
   Text(rotuloStatus(e.status));Text(e.descricao)
   Text("Participantes",style=MaterialTheme.typography.titleLarge)
   item.participantes.forEach {Text("${it.nome} • ${rotuloPresenca(it.presenca)} • ${it.papel}")}
   if(state.dados.pendente) Text("Dados em cache ou pendentes de confirmação.")
   state.erro?.let {Text(it,color=MaterialTheme.colorScheme.error)}
   if(state.gravando) CircularProgressIndicator()
   Text("${item.participantes.count {it.presenca=="vou"}} confirmados")
   if(e.status=="ativo") {
    Escolha("Minha presença",listOf("vou","talvez","nao_vou"),item.participantes.find {it.uid==uid}?.presenca.orEmpty()) {
     if(!state.gravando) vm.presenca(e.id,it)
    }
   }
   if(e.organizadorId==uid) {
    ConvitesEvento(e.id,e.status,state,vm)
    if(e.status!="excluindo") {
     Button(onClick=editar,enabled=!state.gravando) {Text("Editar")}
     if(e.status=="ativo") {
      TextButton(onClick={vm.mudarStatus(e.id,"cancelado")},enabled=!state.gravando) {Text("Cancelar evento")}
      TextButton(onClick={vm.mudarStatus(e.id,"concluido")},enabled=!state.gravando) {Text("Concluir evento")}
     }
    }
    TextButton(onClick={confirmar=true},enabled=!state.gravando) {Text(if(e.status=="excluindo") "Retomar exclusão" else "Excluir evento")}
    if(confirmar) AlertDialog(onDismissRequest={confirmar=false},title={Text("Excluir evento?")},
     text={Text("O evento e seus vínculos serão removidos definitivamente.")},
     confirmButton={TextButton(onClick={confirmar=false;vm.excluir(e.id)}) {Text("Excluir")}},
     dismissButton={TextButton(onClick={confirmar=false}) {Text("Manter")}})
   }
  }
 }
}
