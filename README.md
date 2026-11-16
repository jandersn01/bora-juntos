# Bora Juntos
Aplicativo acadêmico Android em Kotlin, Jetpack Compose e Material 3, com Firebase Authentication/Firestore e Retrofit/ViaCEP reais. Sem dados de demonstração no APK.

## Funcionalidades
Cadastro/login/logout e restauração de sessão; perfil privado; eventos com busca/filtros, capa HTTPS opcional, endereço/CEP, detalhes e horários opcionais; convites por código e presença; votações; tarefas; roteiro e despesas com divisão/saldos em centavos.
Datas usam dias de calendário e horários usam minutos do dia. Para reduzir os limites de um evento que já tem atividades, remova primeiro as atividades do roteiro.
Gastos guardam a divisão escolhida por despesa; novos membros não alteram gastos anteriores. Qualquer membro pode registrar outro membro como pagador, com autor identificado. Sem Pix ou quitação simulada.

## Ambiente e build
Abra o projeto no Android Studio compatível com o AGP existente (9.5.1), SDK 37 e JDK configurado em gradle/gradle-daemon-jvm.properties. Android mínimo: API 26.
Mantenha versões e Gradle Wrapper do projeto. Configure o SDK no IDE/local.properties (arquivo local ignorado).
No Windows: ` .\gradlew.bat :app:assembleDebug ` (sem os espaços externos).
No POSIX: `./gradlew :app:assembleDebug`.
APK debug: app/build/outputs/apk/debug/app-debug.apk.

## Firebase real
1. Use o projeto Firebase existente bora-juntos e o aplicativo Android de pacote com.janderson.borajuntos, ou configure seu próprio projeto mantendo o pacote.
2. Coloque a configuração real desse aplicativo em app/google-services.json. Esse arquivo é local/ignorado e não acompanha o repositório; não invente uma configuração.
3. Confira Authentication > Sign-in method > E-mail/senha habilitado e o Firestore (default) criado.
4. Publique firestore.rules em Firestore Database > Rules > Publish. Não use modo teste. Nenhum índice composto adicional é necessário para as consultas atuais.
Alternativa CLI: `npx -y firebase-tools@latest login`, depois `npx -y firebase-tools@latest deploy --only firestore:rules --project bora-juntos`.
Para obter a configuração do app existente: `npx -y firebase-tools@latest apps:sdkconfig ANDROID <APP_ID_ANDROID> --project bora-juntos`; salve a configuração retornada em app/google-services.json.
Não incluir service account/Admin SDK, senhas ou tokens em código/documentos. As regras, e não a configuração cliente, autorizam os dados.
A publicação das regras e o fluxo com contas reais não foram executados/verificados pelo agente. Nenhum deploy ou faturamento foi ativado automaticamente.

## API e sincronização
ViaCEP: HTTPS, baseUrl https://viacep.com.br/, oito dígitos, erros de CEP inexistente/HTTP/rede, endereço editável e preservado nas falhas. Não exige chave.
Listeners Firestore mantêm dados compartilhados; cache/escritas pendentes aparecem explicitamente. Transações de ingresso/gravação exigem conexão; erros não simulam sucesso.
Autorizações: vínculo validado para leitura, organizador para metadados/convites, próprio UID para presença/voto e regras específicas para tarefas/roteiro/despesas. Evento encerrado bloqueia colaboração.

## Testes
Cálculo Kotlin puro (Windows): ` .\gradlew.bat :app:testDebugUnitTest --tests com.janderson.borajuntos.CalculoGastosTest `.
Regras locais: Node 22 compatível, Java para Emulator Suite e `npm ci`; executar, por exemplo:
`npx -y firebase-tools@latest emulators:exec --only firestore --project demo-bora-juntos "node --test tests/gastos.rules.test.cjs"`.
Outros testes de regras estão em tests/; se combinar arquivos, use `node --test --test-concurrency=1` antes dos caminhos, pois compartilham o banco de teste.
Somente os testes usam dados sintéticos e emuladores; o APK usa o projeto real.
Validação desta entrega: build debug, quatro testes Kotlin de cálculo e quatro testes direcionados de regras de Gastos aprovados. Isso não comprova execução em dispositivo nem publicação.
Aceite manual: cadastro/perfil/login/reabertura; duas contas por convite, presença/voto/tarefa/roteiro sincronizados; despesas com pagadores/divisões diferentes, edição/exclusão e membro posterior; conta não membro sem acesso; encerramento e exclusão completa dos dependentes.

## Branches
master contém fontes, recursos, build/Wrapper, regras, testes e este README técnico. AGENTS.md, docs/*.md e docs/referencias/ são documentos de desenvolvimento preservados somente em developer.
