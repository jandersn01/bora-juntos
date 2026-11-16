package com.janderson.borajuntos.model

data class Enquete(val id: String="", val pergunta: String="", val opcoes: List<String> = emptyList(), val aberta: Boolean=true, val autorId: String="")
data class Voto(val opcao: Long=0)
data class Tarefa(val id: String="", val descricao: String="", val responsavelId: String="", val concluida: Boolean=false, val autorId: String="")
data class Atividade(val id: String="", val descricao: String="", val dia: Long=0, val hora: Long=0, val autorId: String="")

fun atividadeDentro(e: Evento,dia: Long,hora: Long): Boolean =
 dia in e.inicio..e.fim && hora in 0L..1439L &&
 (dia!=e.inicio || e.horaInicio==null || hora>=e.horaInicio) &&
 (dia!=e.fim || e.horaFim==null || hora<=e.horaFim)
