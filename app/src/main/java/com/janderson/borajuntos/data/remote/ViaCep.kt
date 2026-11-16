package com.janderson.borajuntos.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

data class EnderecoCep(
    val logradouro: String? = null, val bairro: String? = null,
    val localidade: String? = null, val uf: String? = null, val erro: Boolean = false
) {
    fun texto() = listOfNotNull(logradouro, bairro, localidade, uf).filter { it.isNotBlank() }.joinToString(", ")
}
interface ViaCepApi {
    @GET("ws/{cep}/json/")
    suspend fun consultar(@Path("cep") cep: String): EnderecoCep
}
class ViaCep {
    private val api = Retrofit.Builder().baseUrl("https://viacep.com.br/")
        .addConverterFactory(GsonConverterFactory.create()).build().create(ViaCepApi::class.java)
    suspend fun buscar(cep: String): String {
        require(cep.matches(Regex("[0-9]{8}"))) { "Informe exatamente oito dígitos no CEP." }
        val endereco = api.consultar(cep)
        require(!endereco.erro) { "CEP inexistente." }
        return endereco.texto().also { require(it.isNotBlank()) { "CEP sem endereço disponível." } }
    }
}
