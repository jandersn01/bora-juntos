package com.janderson.borajuntos.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.janderson.borajuntos.AppContainer
import com.janderson.borajuntos.ui.screens.*

@Composable
fun EventosNavigation(container: AppContainer,uid: String,nome: String,email: String,sair: ()->Unit) {
 val factory=remember(uid) {object: ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T: ViewModel> create(modelClass: Class<T>): T = EventosViewModel(container.eventoRepository,container.viaCep,uid,nome) as T
 }}
 val vm: EventosViewModel=viewModel(key="eventos-"+uid,factory=factory)
 DisposableEffect(vm) {vm.iniciar();onDispose {vm.parar()}}
 val state by vm.state.collectAsStateWithLifecycle()
 val nav=rememberNavController()
 val entry by nav.currentBackStackEntryAsState()
 val rota=entry?.destination?.route
 BackHandler(enabled=state.gravando) {}
 Column {
  if(rota in listOf("inicio","eventos")) TextButton(enabled=!state.gravando,onClick={
   vm.limparResultado();nav.navigate("ingresso")
  }) {Text("Entrar por código")}
  Box(Modifier.weight(1f)) {
   NavHost(nav,startDestination="inicio") {
    composable("inicio") {ListaEventos(state,true,nome,{vm.limparResultado();nav.navigate("novo")},{vm.limparResultado();nav.navigate("detalhes/"+it)},vm::iniciar)}
    composable("eventos") {ListaEventos(state,false,nome,{vm.limparResultado();nav.navigate("novo")},{vm.limparResultado();nav.navigate("detalhes/"+it)},vm::iniciar)}
    composable("ingresso") {EntrarPorCodigo(state,vm,{id->
     nav.navigate("detalhes/"+id) {popUpTo("ingresso") {inclusive=true}}
    },{nav.popBackStack()})}
    composable("novo") {FormularioEvento(null,state,vm,{nav.popBackStack()})}
    composable("detalhes/{id}") { back ->
     val id=back.arguments?.getString("id")
     DetalhesEvento(state.dados.itens.find {it.evento.id==id},state,uid,vm,
      {vm.limparResultado();nav.navigate("editar/"+id)},{nav.popBackStack()},{secao->nav.navigate("organizacao/"+id+"/"+secao)})
    }
    composable("organizacao/{id}/{secao}") {back->
     val id=back.arguments?.getString("id").orEmpty()
     val secao=back.arguments?.getString("secao").orEmpty()
     val orgFactory=remember(uid,id) {object: ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T: ViewModel> create(modelClass: Class<T>): T =
       OrganizacaoViewModel(container.organizacaoRepository,id,uid) as T
     }}
     val org: OrganizacaoViewModel=viewModel(viewModelStoreOwner=back,factory=orgFactory)
     OrganizacaoTela(state.dados.itens.find {it.evento.id==id},secao,org,{nav.popBackStack()})
    }
    composable("editar/{id}") {back ->
     val evento=state.dados.itens.find {it.evento.id==back.arguments?.getString("id")}?.evento
     if(evento!=null) FormularioEvento(evento,state,vm,{nav.popBackStack()})
     else Page {Text("Evento indisponível.");TextButton(onClick={nav.popBackStack()}) {Text("Voltar")}}
    }
    composable("perfil") {Page {
     Text("Meu perfil",style=MaterialTheme.typography.headlineMedium);Text(nome);Text(email)
     Button(onClick=sair,enabled=!state.gravando) {Text("Sair da conta")}
    }}
   }
  }
  if(rota in listOf("inicio","eventos","perfil")) NavigationBar {
   listOf("inicio" to "Início","eventos" to "Eventos","novo" to "Novo","perfil" to "Perfil").forEach { (route,label) ->
    NavigationBarItem(selected=rota==route,enabled=!state.gravando,onClick={
     vm.limparResultado();nav.navigate(route) {launchSingleTop=true;popUpTo("inicio") {saveState=true};restoreState=route!="novo"}
    },icon={Text(when(route) {"inicio"->"⌂";"novo"->"+";"perfil"->"♙";else->"▤"})},label={Text(label)})
   }
  }
 }
}
