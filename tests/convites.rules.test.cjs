const {readFileSync}=require('node:fs');
const {test,before,after,beforeEach}=require('node:test');
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc,getDocs,collection,updateDoc,deleteDoc,writeBatch}=require('firebase/firestore');
let env;
const token='ab'.repeat(32),outro='cd'.repeat(32);
const evento={organizadorId:'ana',titulo:'Churrasco real',tipo:'Churrasco',local:'Rua A, Centro, Fortaleza, CE',inicio:21000,fim:21002,descricao:'',capaUrl:'',status:'ativo'};
const db=(uid='ana')=>uid?env.authenticatedContext(uid,{email:uid+'@example.com'}).firestore():env.unauthenticatedContext().firestore();
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-bora-juntos',firestore:{rules:readFileSync('firestore.rules','utf8'),host:'127.0.0.1',port:8080}})});
beforeEach(async()=>{
 await env.clearFirestore();
 await env.withSecurityRulesDisabled(async c=>{
  for(const [uid,nome] of [['ana','Ana Silva'],['bruno','Bruno Silva'],['carla','Carla Silva']])
   await setDoc(doc(c.firestore(),'usuarios/'+uid),{uid,nome,email:uid+'@example.com'});
 });
 const d=db(),b=writeBatch(d);
 b.set(doc(d,'eventos/e'),evento);
 b.set(doc(d,'eventos/e/participantes/ana'),{uid:'ana',nome:'Ana Silva',papel:'organizador',presenca:'vou'});
 b.set(doc(d,'usuarios/ana/eventos/e'),{eventoId:'e'});
 await assertSucceeds(b.commit());
});
after(async()=>{if(env)await env.cleanup()});
function convite(uid='ana',codigo=token) {
 const d=db(uid),b=writeBatch(d);
 b.set(doc(d,'convites/'+codigo),{eventoId:'e',organizadorId:uid,ativo:true});
 b.set(doc(d,'eventos/e/convites/'+codigo),{token:codigo});
 return b.commit();
}
function ingresso(uid='bruno',codigo=token,patch={},indice=true) {
 const d=db(uid),b=writeBatch(d);
 b.set(doc(d,'eventos/e/participantes/'+uid),{uid,nome:uid==='bruno'?'Bruno Silva':'Carla Silva',papel:'membro',presenca:'vou',conviteToken:codigo,...patch});
 if(indice)b.set(doc(d,'usuarios/'+uid+'/eventos/e'),{eventoId:'e'});
 return b.commit();
}
test('campos opcionais compatíveis e Churrasco; validação também na edição',async()=>{
 const ref=doc(db(),'eventos/e');
 await assertSucceeds(updateDoc(ref,{detalhesLocal:'123, salão dos fundos'}));
 await assertSucceeds(getDoc(ref));
 await assertFails(updateDoc(ref,{detalhesLocal:123}));
 await assertFails(updateDoc(ref,{detalhesLocal:'x'.repeat(501)}));
 for(const tipo of ['Viagem','Festa','Encontro','Outro','Churrasco']) await assertSucceeds(updateDoc(ref,{tipo}));
});
test('organizador gera/revoga; código exato sem listagem; revogado não reativa',async()=>{
 await assertFails(convite('bruno'));
 await assertFails(convite('ana','curto'));
 await assertSucceeds(convite());
 await assertSucceeds(getDoc(doc(db('bruno'),'convites/'+token)));
 await assertFails(getDocs(collection(db(),'convites')));
 await assertFails(getDoc(doc(db(null),'convites/'+token)));
 await assertFails(getDoc(doc(db('bruno'),'eventos/e')));
 await assertFails(updateDoc(doc(db('bruno'),'convites/'+token),{ativo:false}));
 await assertSucceeds(updateDoc(doc(db(),'convites/'+token),{ativo:false}));
 await assertFails(ingresso());
 await assertFails(updateDoc(doc(db(),'convites/'+token),{ativo:true}));
});
test('ingresso atômico, sem leitura prévia; impede vínculos forjados e duplicados',async()=>{
 await assertSucceeds(convite());
 await assertFails(ingresso('bruno',outro));
 await assertFails(ingresso('bruno',token,{},false));
 await assertFails(setDoc(doc(db('bruno'),'usuarios/bruno/eventos/e'),{eventoId:'e'}));
 await assertFails(ingresso('bruno',token,{papel:'organizador'}));
 await assertFails(ingresso('bruno',token,{uid:'ana'}));
 await assertSucceeds(ingresso());
 await assertSucceeds(getDoc(doc(db('bruno'),'eventos/e')));
 await assertFails(ingresso());
 await assertFails(updateDoc(doc(db('bruno'),'eventos/e'),{titulo:'Invadido'}));
 await assertFails(convite('bruno',outro));
 await assertFails(getDoc(doc(db('carla'),'eventos/e')));
});
test('cada usuário altera só sua presença e evento encerrado bloqueia colaboração',async()=>{
 await assertSucceeds(convite());await assertSucceeds(ingresso());
 const ref=doc(db('bruno'),'eventos/e/participantes/bruno');
 for(const presenca of ['talvez','nao_vou','vou']) await assertSucceeds(updateDoc(ref,{presenca}));
 await assertFails(updateDoc(ref,{presenca:'inventado'}));
 await assertFails(updateDoc(ref,{papel:'organizador'}));
 await assertFails(updateDoc(doc(db(),'eventos/e/participantes/bruno'),{presenca:'nao_vou'}));
 await assertSucceeds(updateDoc(doc(db(),'eventos/e'),{status:'cancelado'}));
 await assertFails(updateDoc(ref,{presenca:'talvez'}));
 await assertFails(ingresso('carla'));
 await assertFails(convite('ana',outro));
});
test('exclusão remove convites e vínculos em lotes preservando autorização do organizador',async()=>{
 await assertSucceeds(convite());await assertSucceeds(ingresso());
 const d=db(),ref=doc(d,'eventos/e');
 await assertFails(deleteDoc(doc(d,'convites/'+token)));
 await assertSucceeds(updateDoc(ref,{status:'excluindo'}));
 await assertSucceeds(getDocs(collection(d,'eventos/e/convites')));
 let b=writeBatch(d);b.delete(doc(d,'convites/'+token));b.delete(doc(d,'eventos/e/convites/'+token));await assertSucceeds(b.commit());
 b=writeBatch(d);b.delete(doc(d,'eventos/e/participantes/bruno'));b.delete(doc(d,'usuarios/bruno/eventos/e'));await assertSucceeds(b.commit());
 await assertSucceeds(getDoc(ref));await assertFails(getDoc(doc(db('bruno'),'eventos/e')));
 b=writeBatch(d);b.delete(doc(d,'eventos/e/participantes/ana'));b.delete(doc(d,'usuarios/ana/eventos/e'));b.delete(ref);await assertSucceeds(b.commit());
 await assertSucceeds(getDoc(doc(db('bruno'),'convites/'+token)));
});
