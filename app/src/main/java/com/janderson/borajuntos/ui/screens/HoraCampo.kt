package com.janderson.borajuntos.ui.screens

import android.app.TimePickerDialog
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

fun horaTexto(minutos: Long)=String.format(Locale.ROOT,"%02d:%02d",minutos/60,minutos%60)
@Composable
fun HoraCampo(label: String,valor: Long?,enabled: Boolean=true,alterar: (Long?)->Unit) {
 val context=LocalContext.current
 OutlinedButton(enabled=enabled,onClick={
  // Cursor do seletor não é um valor escolhido: só persistir após confirmar.
  val cursor=valor ?: 0L
  TimePickerDialog(context,{_,h,m->alterar((h*60+m).toLong())},(cursor/60).toInt(),(cursor%60).toInt(),true).show()
 }) {Text(label+": "+(valor?.let(::horaTexto) ?: "Não informado"))}
 if(valor!=null) TextButton(onClick={alterar(null)},enabled=enabled) {Text("Remover horário")}
}
