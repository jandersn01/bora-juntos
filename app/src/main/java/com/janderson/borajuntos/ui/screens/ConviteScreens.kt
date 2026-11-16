package com.janderson.borajuntos.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

fun rotuloPresenca(valor: String)=when(valor) {"vou"->"Vou";"talvez"->"Talvez";"nao_vou"->"Não vou";else->valor}

@Composable
fun ConvitesEvento(id: String,status: String,state: EventosState,vm: EventosViewModel) {
 val context=LocalContext.current
 LaunchedEffect(id) {vm.carregarConvites(id)}
 Text("Convites",style=MaterialTheme.typography.titleLarge)
 TextButton(onClick={vm.carregarConvites(id)},enabled=!state.gravando) {Text("Atualizar convites")}
 if(status=="ativo") Button(onClick={vm.gerarConvite(id)},enabled=!state.gravando) {Text("Gerar código de convite")}
 state.convites.forEach { convite ->
  Text(if(convite.ativo) "Código: ${convite.token}" else "Convite revogado")
  if(convite.ativo) {
   TextButton(enabled=!state.gravando,onClick={
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
     type="text/plain";putExtra(Intent.EXTRA_TEXT,"Bora Juntos! Entre pelo código: ${convite.token}")
    },"Compartilhar convite"))
   }) {Text("Compartilhar código")}
   TextButton(onClick={vm.revogarConvite(id,convite.token)},enabled=!state.gravando && status!="excluindo") {Text("Revogar convite")}
  }
 }
}

@Composable
fun EntrarPorCodigo(state: EventosState,vm: EventosViewModel,abrir: (String)->Unit,voltar: ()->Unit) {
 var codigo by rememberSaveable {mutableStateOf("")}
 LaunchedEffect(state.salvo) {
  if(state.salvo=="ingresso") state.eventoIngresso?.let {id->vm.limparResultado();abrir(id)}
 }
 Page {
  Text("Entrar por código",style=MaterialTheme.typography.headlineMedium)
  OutlinedTextField(codigo,{codigo=it},label={Text("Código do convite")},modifier=Modifier.fillMaxWidth(),enabled=!state.gravando)
  state.erro?.let {Text(it,color=MaterialTheme.colorScheme.error)}
  if(state.gravando) CircularProgressIndicator()
  Button(onClick={vm.entrar(codigo)},enabled=!state.gravando) {Text("Entrar no evento")}
  TextButton(onClick=voltar,enabled=!state.gravando) {Text("Voltar")}
 }
}
