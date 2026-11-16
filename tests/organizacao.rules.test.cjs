const {readFileSync}=require('node:fs');
const {strict:assert}=require('node:assert');
const {test,before,after,beforeEach}=require('node:test');
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc,getDocs,collection,updateDoc,deleteDoc,deleteField,writeBatch}=require('firebase/firestore');
let env;
const evento={organizadorId:'ana',titulo:'Viagem',tipo:'Viagem',local:'Fortaleza',inicio:21000,fim:21001,descricao:'',capaUrl:'',status:'ativo'};
const db=(uid='ana')=>uid?env.authenticatedContext(uid,{email:uid+'@example.com'}).firestore():env.unauthenticatedContext().firestore();
const ref=(uid,path)=>doc(db(uid),'eventos/e/'+path);
const poll={pergunta:'Qual atividade?',opcoes:['Praia','Museu'],autorId:'ana',aberta:true};
const task={descricao:'Comprar água',responsavelId:'carla',autorId:'bruno',concluida:false};
const activity={descricao:'Passeio',dia:21000,hora:1380,autorId:'bruno'};
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-bora-juntos',firestore:{rules:readFileSync('firestore.rules','utf8'),host:'127.0.0.1',port:8080}})});
beforeEach(async()=>{
 await env.clearFirestore();
 await env.withSecurityRulesDisabled(async c=>{
  await setDoc(doc(c.firestore(),'eventos/e'),evento);
  for(const uid of ['ana','bruno','carla']) await setDoc(doc(c.firestore(),'eventos/e/participantes/'+uid),{uid,nome:uid,papel:uid==='ana'?'organizador':'membro',presenca:'vou'});
 });
});
after(async()=>{if(env)await env.cleanup()});
async function criarAtividade(data=activity) {
 const d=db('bruno'),pai=doc(d,'eventos/e'),atual=await getDoc(pai),b=writeBatch(d);
 b.set(doc(d,'eventos/e/roteiro/a'),data);
 b.update(pai,{roteiroQuantidade:(atual.data().roteiroQuantidade||0)+1,roteiroAlteracaoId:'a'});
 return b.commit();
}
async function excluirAtividade() {
 const d=db('bruno'),pai=doc(d,'eventos/e'),atual=await getDoc(pai),b=writeBatch(d);
 b.delete(doc(d,'eventos/e/roteiro/a'));
 b.update(pai,{roteiroQuantidade:atual.data().roteiroQuantidade-1,roteiroAlteracaoId:'a'});
 return b.commit();
}
test('horários opcionais/legados, ordem conjunta, meia-noite e tipos inválidos',async()=>{
 const e=doc(db(),'eventos/e');
 await assertSucceeds(updateDoc(e,{horaInicio:1380,horaFim:60}));
 await assertFails(updateDoc(e,{fim:21000}));
 await assertSucceeds(updateDoc(e,{horaInicio:60,horaFim:60,fim:21000}));
 for(const patch of [{horaInicio:1440},{horaFim:-1},{horaInicio:'23:00'},{horaFim:3.5}]) await assertFails(updateDoc(e,patch));
 await assertSucceeds(updateDoc(e,{horaInicio:deleteField(),horaFim:deleteField()}));
 await assertSucceeds(updateDoc(e,{horaInicio:null,horaFim:null}));
});
test('votos: próprio UID, opção válida, troca única e fechamento só pelo organizador',async()=>{
 await assertFails(setDoc(ref('bruno','enquetes/p'),{...poll,autorId:'bruno'}));
 await assertFails(setDoc(ref('ana','enquetes/p'),{...poll,opcoes:['Igual','Igual']}));
 await assertFails(setDoc(ref('ana','enquetes/p'),{...poll,opcoes:['Válida',4]}));
 await assertSucceeds(setDoc(ref('ana','enquetes/p'),poll));
 await assertFails(setDoc(ref('bruno','enquetes/p/votos/ana'),{opcao:0}));
 for(const opcao of [-1,2,'0',0.5]) await assertFails(setDoc(ref('bruno','enquetes/p/votos/bruno'),{opcao}));
 await assertSucceeds(setDoc(ref('bruno','enquetes/p/votos/bruno'),{opcao:0}));
 await assertSucceeds(setDoc(ref('bruno','enquetes/p/votos/bruno'),{opcao:1}));
 const votos=await assertSucceeds(getDocs(collection(db('ana'),'eventos/e/enquetes/p/votos')));
 assert.equal(votos.size,1);assert.equal(votos.docs[0].data().opcao,1);
 await assertFails(updateDoc(ref('bruno','enquetes/p'),{aberta:false}));
 await assertFails(updateDoc(ref('ana','enquetes/p'),{opcoes:['Outra','Nova']}));
 await assertSucceeds(updateDoc(ref('ana','enquetes/p'),{aberta:false}));
 await assertFails(updateDoc(ref('bruno','enquetes/p/votos/bruno'),{opcao:0}));
 await assertFails(updateDoc(ref('ana','enquetes/p'),{aberta:true}));
 await assertFails(getDoc(ref('fora','enquetes/p')));
 await assertFails(getDocs(collection(db(null),'eventos/e/enquetes')));
});
test('tarefas: responsável membro, autor/organizador editam; responsável/organizador concluem',async()=>{
 await assertFails(setDoc(ref('bruno','tarefas/t'),{...task,responsavelId:'fora'}));
 await assertFails(setDoc(ref('bruno','tarefas/t'),{...task,autorId:'ana'}));
 await assertSucceeds(setDoc(ref('bruno','tarefas/t'),task));
 await assertFails(updateDoc(ref('bruno','tarefas/t'),{concluida:true}));
 await assertSucceeds(updateDoc(ref('carla','tarefas/t'),{concluida:true}));
 await assertFails(updateDoc(ref('carla','tarefas/t'),{descricao:'Outra'}));
 await assertFails(deleteDoc(ref('carla','tarefas/t')));
 await assertSucceeds(updateDoc(ref('bruno','tarefas/t'),{descricao:'Comprar água e gelo'}));
 await assertFails(updateDoc(ref('bruno','tarefas/t'),{autorId:'carla'}));
 await assertSucceeds(updateDoc(ref('ana','tarefas/t'),{concluida:false}));
 await assertSucceeds(deleteDoc(ref('ana','tarefas/t')));
 await assertFails(getDoc(ref('fora','tarefas/t')));
});
test('roteiro: limites de data/hora no servidor, independência dos horários gerais e permissão de CRUD',async()=>{
 await assertFails(setDoc(ref('bruno','roteiro/a'),activity));
 await assertFails(updateDoc(doc(db(),'eventos/e'),{roteiroQuantidade:1,roteiroAlteracaoId:'fantasma'}));
 await assertSucceeds(criarAtividade({...activity,hora:0}));
 await assertFails(updateDoc(doc(db(),'eventos/e'),{horaInicio:1380,horaFim:60}));
 await assertFails(updateDoc(doc(db(),'eventos/e'),{roteiroQuantidade:0}));
 await assertSucceeds(excluirAtividade());
 await assertSucceeds(updateDoc(doc(db(),'eventos/e'),{horaInicio:1380,horaFim:60}));
 await assertFails(criarAtividade({...activity,hora:1379}));
 await assertSucceeds(criarAtividade());
 await assertFails(updateDoc(ref('bruno','roteiro/a'),{hora:1379}));
 await assertSucceeds(updateDoc(ref('bruno','roteiro/a'),{dia:21001,hora:60}));
 for(const patch of [{dia:21001,hora:61},{dia:20999},{dia:21002},{hora:1440},{hora:'01:00'}]) await assertFails(updateDoc(ref('bruno','roteiro/a'),patch));
 await assertFails(updateDoc(ref('carla','roteiro/a'),{descricao:'Invadir'}));
 await assertFails(deleteDoc(ref('carla','roteiro/a')));
 await assertSucceeds(updateDoc(ref('ana','roteiro/a'),{descricao:'Passeio editado'}));
 await assertSucceeds(excluirAtividade());
 await assertFails(setDoc(ref('fora','roteiro/a'),{...activity,autorId:'fora'}));
});
test('encerramento bloqueia colaboração; exclusão administrativa remove votos e dependentes',async()=>{
 await assertSucceeds(setDoc(ref('ana','enquetes/p'),poll));
 await assertSucceeds(setDoc(ref('bruno','enquetes/p/votos/bruno'),{opcao:0}));
 await assertSucceeds(setDoc(ref('bruno','tarefas/t'),task));
 await assertSucceeds(criarAtividade());
 await assertSucceeds(updateDoc(doc(db(),'eventos/e'),{status:'concluido'}));
 await assertFails(setDoc(ref('ana','enquetes/outra'),poll));
 await assertFails(updateDoc(ref('ana','enquetes/p'),{aberta:false}));
 await assertFails(updateDoc(ref('bruno','enquetes/p/votos/bruno'),{opcao:1}));
 await assertFails(updateDoc(ref('carla','tarefas/t'),{concluida:true}));
 await assertFails(updateDoc(ref('bruno','roteiro/a'),{descricao:'Outra'}));
 await assertFails(deleteDoc(ref('ana','tarefas/t')));
 await assertSucceeds(getDoc(ref('bruno','tarefas/t')));
 await assertSucceeds(updateDoc(doc(db(),'eventos/e'),{status:'excluindo'}));
 for(const path of ['enquetes/p/votos/bruno','enquetes/p','tarefas/t','roteiro/a']) {
  await assertFails(deleteDoc(ref('bruno',path)));
  await assertSucceeds(deleteDoc(ref('ana',path)));
  assert.equal((await getDoc(ref('ana',path))).exists(),false);
 }
});
