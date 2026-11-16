package com.janderson.borajuntos.model

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

data class Gasto(
 val id: String="", val descricao: String="", val valorCentavos: Long=0,
 val pagadorId: String="", val participantes: List<String> = emptyList(), val autorId: String=""
)
data class ResumoGastos(val total: Long, val saldos: Map<String,Long>) {
 fun devido(uid: String)=maxOf(0L,-(saldos[uid] ?: 0L))
}

// Especialista GRASP: somente Kotlin/JVM, sem dependência de Android ou Firestore.
object CalculoGastos {
 fun converterBR(texto: String): Long {
  val valor=texto.trim()
  require(valor.matches(Regex("(?:[0-9]+|[0-9]{1,3}(?:\\.[0-9]{3})+)(?:,[0-9]{1,2})?"))) {
   "Informe um valor brasileiro, por exemplo 100,00."
  }
  val centavos=try {
   BigDecimal(valor.replace(".","").replace(',','.')).movePointRight(2).longValueExact()
  } catch(e: ArithmeticException) {throw IllegalArgumentException("Valor fora do limite permitido.",e)}
  require(centavos>0) {"O valor deve ser positivo."}
  return centavos
 }
 fun quotas(valor: Long,participantes: List<String>): Map<String,Long> {
  require(valor>0 && participantes.isNotEmpty() && participantes.distinct().size==participantes.size)
  val ordenados=participantes.sorted()
  val quociente=valor/ordenados.size
  val resto=valor%ordenados.size
  return ordenados.mapIndexed {index,uid->uid to (quociente+if(index.toLong()<resto) 1L else 0L)}.toMap()
 }
 fun resumir(gastos: List<Gasto>,membros: Collection<String> = emptyList()): ResumoGastos {
  var total=0L
  val saldos=membros.associateWith {0L}.toMutableMap()
  gastos.forEach {g->
   total=Math.addExact(total,g.valorCentavos)
   saldos[g.pagadorId]=Math.addExact(saldos[g.pagadorId] ?: 0L,g.valorCentavos)
   quotas(g.valorCentavos,g.participantes).forEach {(uid,quota)->
    saldos[uid]=Math.subtractExact(saldos[uid] ?: 0L,quota)
   }
  }
  // A soma matemática das quotas e dos saldos decorre da divisão inteira acima.
  return ResumoGastos(total,saldos.toMap())
 }
}
fun dinheiro(centavos: Long): String =
 NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(BigDecimal.valueOf(centavos,2))
fun valorEditavel(centavos: Long)=BigDecimal.valueOf(centavos,2).toPlainString().replace('.',',')
