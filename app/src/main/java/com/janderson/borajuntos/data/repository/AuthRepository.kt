package com.janderson.borajuntos.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

data class Perfil(val uid: String, val nome: String, val email: String)

class AuthRepository(private val auth: FirebaseAuth, private val db: FirebaseFirestore) {
    val usuario get() = auth.currentUser
    fun observar(listener: FirebaseAuth.AuthStateListener) = auth.addAuthStateListener(listener)
    fun remover(listener: FirebaseAuth.AuthStateListener) = auth.removeAuthStateListener(listener)
    suspend fun entrar(email: String, senha: String) {
        auth.signInWithEmailAndPassword(email, senha).await()
    }
    suspend fun cadastrar(nome: String, email: String, senha: String) {
        val user = requireNotNull(auth.createUserWithEmailAndPassword(email, senha).await().user)
        // Auth e Firestore não compartilham transação: nunca excluir a conta se o perfil falhar.
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(nome).build()).await()
    }
    suspend fun lerPerfil(): Perfil? = withTimeout(15000) {
        val user = requireNotNull(usuario)
        val doc = db.collection("usuarios").document(user.uid).get(Source.SERVER).await()
        if (!doc.exists()) null else Perfil(user.uid, requireNotNull(doc.getString("nome")), user.email.orEmpty())
    }
    suspend fun salvarPerfil(nome: String) = withTimeout(15000) {
        val user = requireNotNull(usuario)
        val ref = db.collection("usuarios").document(user.uid)
        // Transação exige conexão e permite repetir após resposta perdida sem duplicar documentos.
        db.runTransaction { transaction ->
            transaction.set(ref, mapOf("uid" to user.uid, "nome" to nome, "email" to user.email.orEmpty()))
        }.await()
    }
    fun sair() = auth.signOut()
}
