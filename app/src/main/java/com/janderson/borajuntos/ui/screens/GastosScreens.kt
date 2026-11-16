package com.janderson.borajuntos.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.janderson.borajuntos.model.*
import java.util.UUID

@Composable
fun GastosTela(item: EventoItem?,state: OrganizacaoState,vm: OrganizacaoViewModel,voltar: ()->Unit) {
 var editor by rememberSaveable {mutableStateOf<String?>(null)}
 var excluir by rememberSaveable {mutableStateOf<String?>(null)}
 LaunchedEffect(state.salvo) {if(state.salvo>0) {editor=null;excluir=null}}
 val pessoas=item?.participantes.orEmpty()
 val ativo=item?.evento?.status=="ativo"
 val organizador=item?.evento?.organizadorId==vm.uid
 fun nome(uid: String)=pessoas.find {it.uid==uid}?.nome ?: "Membro indisponível"
 val calculo=runCatching {CalculoGastos.resumir(state.dados.gastos,pessoas.map {it.uid})}
 Scaffold(floatingActionButton={
  if(ativo) FloatingActionButton(onClick={editor="novo"},containerColor=Color(0xFFFF7043)) {Text("+ Adicionar gasto",Modifier.padding(12.dp))}
 }) {padding->
  LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   item {
    TextButton(onClick=voltar,enabled=!state.gravando) {Text("Voltar")}
    Text("Gastos",style=MaterialTheme.typography.headlineMedium)
    if(!ativo) Text("Evento encerrado ou indisponível. Somente leitura.")
    if(state.dados.carregando || state.gravando) CircularProgressIndicator()
    if(state.dados.pendente) Text("Dados em cache ou pendentes de confirmação.")
    state.erro?.let {Text(it,color=MaterialTheme.colorScheme.error);TextButton(onClick=vm::iniciar,enabled=!state.gravando) {Text("Tentar novamente")}}
    if(!state.dados.carregando && state.erro==null) {
     val resumo=calculo.getOrNull()
     if(resumo==null) Text("Não foi possível calcular os totais: valor acumulado fora do limite.",color=MaterialTheme.colorScheme.error)
     else Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp)) {
      Text("Total do grupo",style=MaterialTheme.typography.titleMedium);Text(dinheiro(resumo.total))
      Text("Você deve: ${dinheiro(resumo.devido(vm.uid))}")
      Text("Seu saldo: ${dinheiro(resumo.saldos[vm.uid] ?: 0L)}")
     }}
    }
   }
   if(state.dados.gastos.isEmpty() && !state.dados.carregando && state.erro==null) item {Text("Nenhuma despesa cadastrada.")}
   items(state.dados.gastos.sortedBy {it.id},key={it.id}) {g->
    Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
     Text(g.descricao,style=MaterialTheme.typography.titleLarge)
     Text("${dinheiro(g.valorCentavos)} • Pago por ${nome(g.pagadorId)}")
     Text("Registrado por ${nome(g.autorId)}")
     Text("Divisão:")
     CalculoGastos.quotas(g.valorCentavos,g.participantes).forEach {(uid,quota)->Text("${nome(uid)}: ${dinheiro(quota)}")}
     if(g.autorId==vm.uid || organizador) {
      TextButton(onClick={editor=g.id},enabled=ativo && !state.gravando) {Text("Editar")}
      TextButton(onClick={excluir=g.id},enabled=ativo && !state.gravando) {Text("Excluir")}
     }
    }}
   }
   if(!state.dados.carregando && state.erro==null) calculo.getOrNull()?.let {resumo->
    item {Text("Saldos individuais",style=MaterialTheme.typography.titleLarge)}
    items(resumo.saldos.keys.sorted(),key={"saldo-"+it}) {uid->
     val saldo=resumo.saldos.getValue(uid)
     Text("${nome(uid)}: ${dinheiro(saldo)} • ${when {saldo>0->"a receber";saldo<0->"a pagar";else->"saldo zero"}}")
    }
   }
   item {Spacer(Modifier.height(90.dp))}
  }
 }
 if(editor!=null) key(editor) {
  EditorGasto(state.dados.gastos.find {it.id==editor},pessoas,state,vm,{editor=null})
 }
 if(excluir!=null) AlertDialog(onDismissRequest={if(!state.gravando) excluir=null},title={Text("Excluir despesa?")},
  text={Column {Text("Os saldos serão recalculados.");state.erro?.let {Text(it)}}},
  confirmButton={TextButton(onClick={vm.excluir("gastos",excluir!!)},enabled=!state.gravando) {Text("Excluir")}},
  dismissButton={TextButton(onClick={excluir=null},enabled=!state.gravando) {Text("Manter")}})
}

@Composable
private fun EditorGasto(original: Gasto?,pessoas: List<Participante>,state: OrganizacaoState,vm: OrganizacaoViewModel,cancelar: ()->Unit) {
 val id=rememberSaveable {original?.id ?: UUID.randomUUID().toString()}
 var descricao by rememberSaveable {mutableStateOf(original?.descricao.orEmpty())}
 var valor by rememberSaveable {mutableStateOf(original?.let {valorEditavel(it.valorCentavos)}.orEmpty())}
 var pagador by rememberSaveable {mutableStateOf(original?.pagadorId.orEmpty())}
 var divisao by rememberSaveable {mutableStateOf(ArrayList(original?.participantes.orEmpty()))}
 EditorBase("Despesa",state,cancelar,{
  vm.gasto(id,descricao,valor,pagador,divisao.toList(),original?.autorId ?: vm.uid)
 }) {
  OutlinedTextField(descricao,{descricao=it},label={Text("Descrição")},enabled=!state.gravando)
  OutlinedTextField(valor,{valor=it},label={Text("Valor (R$) • exemplo: 100,00")},singleLine=true,enabled=!state.gravando)
  Text("Pagador")
  Text("É permitido registrar outro membro como pagador; a autoria permanece identificada.")
  pessoas.forEach {p->Row {
   RadioButton(selected=pagador==p.uid,onClick={pagador=p.uid},enabled=!state.gravando);Text(p.nome,Modifier.padding(top=12.dp))
  }}
  Text("Participantes da divisão • selecione explicitamente")
  pessoas.forEach {p->Row {
   Checkbox(checked=p.uid in divisao,onCheckedChange={marcado->
    divisao=ArrayList(if(marcado) divisao+p.uid else divisao-p.uid)
   },enabled=!state.gravando);Text(p.nome,Modifier.padding(top=12.dp))
  }}
 }
}
