package com.janderson.borajuntos.model

import java.time.LocalDate
import java.net.URI

data class Evento(
    val id: String = "", val organizadorId: String = "", val titulo: String = "",
    val tipo: String = "Viagem", val local: String = "", val inicio: Long = LocalDate.now().toEpochDay(),
    val fim: Long = LocalDate.now().toEpochDay(), val descricao: String = "",
    val capaUrl: String = "", val status: String = "ativo", val detalhesLocal: String = "", val horaInicio: Long? = null, val horaFim: Long? = null,
    val roteiroQuantidade: Long = 0, val roteiroAlteracaoId: String = ""
)
data class Participante(val uid: String = "", val nome: String = "", val papel: String = "", val presenca: String = "")
data class EventoItem(val evento: Evento, val participantes: List<Participante> = emptyList())
val tiposEvento = listOf("Viagem", "Festa", "Encontro", "Outro", "Churrasco")
fun validarEvento(e: Evento): String? = when {
    e.titulo.trim().length !in 2..100 -> "Título deve ter entre 2 e 100 caracteres."
    e.tipo !in tiposEvento -> "Escolha um tipo válido."
    e.local.trim().length !in 2..300 -> "Local deve ter entre 2 e 300 caracteres."
    e.inicio !in 0L..2932896L || e.fim !in e.inicio..2932896L -> "Datas inválidas: início deve ser anterior ou igual ao fim."
    listOfNotNull(e.horaInicio,e.horaFim).any { it !in 0L..1439L } -> "Horário inválido."
    e.inicio == e.fim && e.horaInicio != null && e.horaFim != null && e.horaInicio > e.horaFim -> "O fim deve ser posterior ou igual ao início."
    e.detalhesLocal.length > 500 -> "Detalhes do local devem ter até 500 caracteres."
    e.descricao.length > 3000 -> "Descrição deve ter até 3000 caracteres."
    e.capaUrl.length > 2000 || (e.capaUrl.isNotEmpty() && runCatching {
        val uri = URI(e.capaUrl); uri.scheme != "https" || uri.host.isNullOrBlank()
    }.getOrDefault(true)) -> "Informe uma URL HTTPS válida para a capa."
    else -> null
}
