package com.janderson.borajuntos

import com.janderson.borajuntos.model.*
import org.junit.Assert.*
import org.junit.Test

class CalculoGastosTest {
 private fun gasto(id: String,valor: Long,pagador: String,divisao: List<String>) =
  Gasto(id,"Despesa de teste",valor,pagador,divisao,"a")
 @Test fun cemReaisEntreTresPreservaTodosOsCentavos() {
  val quotas=CalculoGastos.quotas(10000,listOf("c","b","a"))
  assertEquals(mapOf("a" to 3334L,"b" to 3333L,"c" to 3333L),quotas)
  assertEquals(10000L,quotas.values.sum())
  val r=CalculoGastos.resumir(listOf(gasto("1",10000,"a",listOf("a","b","c"))))
  assertEquals(mapOf("a" to 6666L,"b" to -3333L,"c" to -3333L),r.saldos)
  assertEquals(3333L,r.devido("b"));assertEquals(0L,r.devido("a"))
 }
 @Test fun multiplosPagadoresEdicaoExclusaoEEntradaPosterior() {
  val inicial=gasto("1",10000,"a",listOf("c","a","b"))
  val segundo=gasto("2",6000,"b",listOf("b","a"))
  val r=CalculoGastos.resumir(listOf(inicial,segundo))
  assertEquals(16000L,r.total)
  assertEquals(mapOf("a" to 3666L,"b" to -333L,"c" to -3333L),r.saldos)
  val comNovo=CalculoGastos.resumir(listOf(inicial,segundo),listOf("a","b","c","d"))
  assertEquals(0L,comNovo.saldos["d"])
  r.saldos.forEach {(uid,saldo)->assertEquals(saldo,comNovo.saldos[uid])}
  assertEquals(listOf("c","a","b"),inicial.participantes)
  val editado=inicial.copy(valorCentavos=9000)
  val aposEdicao=CalculoGastos.resumir(listOf(editado,segundo))
  assertEquals(mapOf("a" to 3000L,"b" to 0L,"c" to -3000L),aposEdicao.saldos)
  val aposExclusao=CalculoGastos.resumir(listOf(editado))
  assertEquals(mapOf("a" to 6000L,"b" to -3000L,"c" to -3000L),aposExclusao.saldos)
  listOf(r,comNovo,aposEdicao,aposExclusao).forEach {assertEquals(0L,it.saldos.values.sum())}
  assertEquals(0L,CalculoGastos.resumir(emptyList()).total)
 }
 @Test fun parsingBrasileiroExatoSemArredondar() {
  assertEquals(123456L,CalculoGastos.converterBR("1.234,56"))
  assertEquals(10000L,CalculoGastos.converterBR("100"))
  assertEquals(1L,CalculoGastos.converterBR("0,01"))
  for(valor in listOf("0","-1,00","1,001","1.23","1e3","92233720368547758,08")) {
   assertThrows(IllegalArgumentException::class.java) {CalculoGastos.converterBR(valor)}
  }
 }
 @Test fun restaEstavelMesmoQuandoValorMenorQueNumeroDePessoas() {
  assertEquals(mapOf("a" to 1L,"b" to 0L,"c" to 0L),CalculoGastos.quotas(1,listOf("c","a","b")))
  assertThrows(IllegalArgumentException::class.java) {CalculoGastos.quotas(100,listOf("a","a"))}
 }
}
