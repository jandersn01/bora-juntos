package com.janderson.borajuntos.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.janderson.borajuntos.ui.screens.*
import com.janderson.borajuntos.AppContainer

@Composable
fun BoraApp(vm: AuthViewModel,container: AppContainer) {
 val state by vm.state.collectAsStateWithLifecycle()
 Scaffold { padding ->
  Surface(Modifier.fillMaxSize().padding(padding),color=MaterialTheme.colorScheme.background) {
   when {
    state.uid==null -> {
     val nav=rememberNavController()
     NavHost(nav,startDestination="login") {
      composable("login") {AuthScreen(false,state,{_,email,senha->vm.entrar(email,senha)},{nav.navigate("cadastro")})}
      composable("cadastro") {AuthScreen(true,state,vm::cadastrar,{nav.popBackStack()})}
     }
    }
    state.perfil==null -> PerfilPendente(state,vm::completarPerfil,vm::atualizar,vm::sair)
    else -> key(state.uid) {EventosNavigation(container,state.uid!!,state.perfil!!.nome,state.email,vm::sair)}
   }
  }
 }
}
