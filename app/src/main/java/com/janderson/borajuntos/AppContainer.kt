package com.janderson.borajuntos

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.janderson.borajuntos.data.repository.AuthRepository

class AppContainer {
    val organizacaoRepository = com.janderson.borajuntos.data.repository.OrganizacaoRepository(FirebaseFirestore.getInstance())
    val eventoRepository = com.janderson.borajuntos.data.repository.EventoRepository(FirebaseFirestore.getInstance())
    val viaCep = com.janderson.borajuntos.data.remote.ViaCep()
    val authRepository = AuthRepository(FirebaseAuth.getInstance(), FirebaseFirestore.getInstance())
}
