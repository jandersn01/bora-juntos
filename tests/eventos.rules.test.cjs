const {readFileSync}=require('node:fs');
const {test,before,after,beforeEach}=require('node:test');
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc,getDocs,collection,updateDoc,deleteDoc,writeBatch}=require('firebase/firestore');
let env;
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-bora-juntos',firestore:{rules:readFileSync('firestore.rules','utf8'),host:'127.0.0.1',port:8080}})});
beforeEach(async()=>{await env.clearFirestore();await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),'usuarios/ana'),{uid:'ana',nome:'Ana Silva',email:'ana@example.com'}))});
after(async()=>{if(env) await env.cleanup()});
const db=(uid='ana')=>uid?env.authenticatedContext(uid,{email:uid+'@example.com'}).firestore():env.unauthenticatedContext().firestore();
const evento={organizadorId:'ana',titulo:'Viagem real',tipo:'Viagem',local:'Fortaleza',inicio:21000,fim:21002,descricao:'',capaUrl:'',status:'ativo'};
function criar(d=evento){const own=db(),b=writeBatch(own);b.set(doc(own,'eventos/e'),d);b.set(doc(own,'eventos/e/participantes/ana'),{uid:'ana',nome:'Ana Silva',papel:'organizador',presenca:'vou'});b.set(doc(own,'usuarios/ana/eventos/e'),{eventoId:'e'});return b.commit()}
test('evento exige criação atômica e não autoriza terceiros nem listagem global',async()=>{
 await assertFails(setDoc(doc(db(),'eventos/e'),evento));
 await assertSucceeds(criar());
 await assertSucceeds(getDoc(doc(db(),'eventos/e')));
 await assertSucceeds(getDocs(collection(db(),'usuarios/ana/eventos')));
 await assertSucceeds(getDocs(collection(db(),'eventos/e/participantes')));
 for(const uid of ['bruno',null]) {
  await assertFails(getDoc(doc(db(uid),'eventos/e')));
  await assertFails(updateDoc(doc(db(uid),'eventos/e'),{titulo:'Invadido'}));
  await assertFails(deleteDoc(doc(db(uid),'eventos/e')));
 }
 await assertFails(getDocs(collection(db(),'eventos')));
 await assertFails(setDoc(doc(db('bruno'),'usuarios/bruno/eventos/e'),{eventoId:'e'}));
 await assertFails(setDoc(doc(db('bruno'),'eventos/e/participantes/bruno'),{uid:'bruno',nome:'Bruno',papel:'organizador',presenca:'vou'}));
});
test('tipos, datas, capa e identidade validados também na edição; encerramento sem reabertura',async()=>{
 await assertFails(criar({...evento,fim:20999}));
 await assertSucceeds(criar());
 const ref=doc(db(),'eventos/e');
 for(const change of [{organizadorId:'bruno'},{inicio:21003},{titulo:4},{tipo:'Fake'},{capaUrl:'http://inseguro'},{admin:true}]) await assertFails(updateDoc(ref,change));
 await assertSucceeds(updateDoc(ref,{titulo:'Título editado'}));
 await assertSucceeds(updateDoc(ref,{status:'concluido'}));
 await assertFails(updateDoc(ref,{status:'ativo'}));
 await assertSucceeds(updateDoc(ref,{local:'Outro local'}));
 await assertFails(setDoc(doc(db(),'eventos/e/gastos/um'),{valor:10}));
});
test('exclusão exige limpeza conjunta dos vínculos e mantém acesso até finalizar',async()=>{
 await assertSucceeds(criar());
 const own=db(),ref=doc(own,'eventos/e');
 await assertFails(deleteDoc(ref));
 await assertSucceeds(updateDoc(ref,{status:'excluindo'}));
 await assertSucceeds(getDoc(ref));
 await assertFails(updateDoc(ref,{titulo:'Mudança durante exclusão'}));
 await assertFails(deleteDoc(ref));
 const b=writeBatch(own);
 b.delete(doc(own,'eventos/e/participantes/ana'));b.delete(doc(own,'usuarios/ana/eventos/e'));b.delete(ref);
 await assertSucceeds(b.commit());
 await assertSucceeds(getDocs(collection(own,'usuarios/ana/eventos')));
});
