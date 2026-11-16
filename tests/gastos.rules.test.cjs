const {readFileSync}=require('node:fs');
const {strict:assert}=require('node:assert');
const {test,before,after,beforeEach}=require('node:test');
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc,getDocs,collection,updateDoc,deleteDoc}=require('firebase/firestore');
let env;
const db=(uid='bruno')=>uid?env.authenticatedContext(uid,{email:uid+'@example.com'}).firestore():env.unauthenticatedContext().firestore();
const ref=(uid,path)=>doc(db(uid),'eventos/e/'+path);
const gasto={descricao:'Almoço',valorCentavos:10000,pagadorId:'carla',participantes:['ana','bruno','carla'],autorId:'bruno'};
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-bora-juntos',firestore:{rules:readFileSync('firestore.rules','utf8'),host:'127.0.0.1',port:8080}})});
beforeEach(async()=>{
 await env.clearFirestore();
 await env.withSecurityRulesDisabled(async c=>{
  await setDoc(doc(c.firestore(),'eventos/e'),{organizadorId:'ana',titulo:'Viagem',tipo:'Viagem',local:'Fortaleza',inicio:21000,fim:21001,descricao:'',capaUrl:'',status:'ativo'});
  for(const uid of ['ana','bruno','carla']) await setDoc(doc(c.firestore(),'eventos/e/participantes/'+uid),{uid,nome:uid,papel:uid==='ana'?'organizador':'membro',presenca:'vou'});
 });
});
after(async()=>{if(env)await env.cleanup()});
async function registrar(uid) {
 const cadastro=ref('bruno','controle/membrosGastos'),snapshot=await getDoc(cadastro);
 const uids=snapshot.exists()?snapshot.data().uids:[];
 if(!uids.includes(uid))return setDoc(cadastro,{uids:[...uids,uid],ultimoUid:uid});
}
async function preparar(){for(const uid of ['ana','bruno','carla'])await assertSucceeds(registrar(uid))}
test('registro auxiliar só inclui membros reais, um por vez, sem forjar acesso',async()=>{
 await assertFails(setDoc(ref('bruno','controle/membrosGastos'),{uids:['ana','bruno'],ultimoUid:'bruno'}));
 await assertFails(registrar('fora'));
 await assertSucceeds(registrar('ana'));await assertSucceeds(registrar('bruno'));
 await assertFails(updateDoc(ref('bruno','controle/membrosGastos'),{uids:['ana','fora','bruno'],ultimoUid:'fora'}));
 await assertFails(updateDoc(ref('bruno','controle/membrosGastos'),{uids:['ana','carla'],ultimoUid:'carla'}));
 await assertFails(deleteDoc(ref('ana','controle/membrosGastos')));
 await assertFails(getDoc(ref('fora','controle/membrosGastos')));
 await assertFails(getDoc(ref(null,'controle/membrosGastos')));
});
test('membro registra outro pagador; somente autor/organizador editam e excluem',async()=>{
 await assertFails(setDoc(ref('bruno','gastos/g'),gasto));
 await preparar();await assertSucceeds(setDoc(ref('bruno','gastos/g'),gasto));
 await assertSucceeds(getDocs(collection(db('carla'),'eventos/e/gastos')));
 await assertFails(updateDoc(ref('carla','gastos/g'),{descricao:'Alterar pagamento'}));
 await assertFails(deleteDoc(ref('carla','gastos/g')));
 await assertSucceeds(updateDoc(ref('bruno','gastos/g'),{valorCentavos:9000}));
 await assertSucceeds(updateDoc(ref('ana','gastos/g'),{pagadorId:'ana',participantes:['ana','carla']}));
 await assertFails(updateDoc(ref('ana','gastos/g'),{autorId:'ana'}));
 await assertFails(getDoc(ref('fora','gastos/g')));
 await assertFails(setDoc(ref('fora','gastos/outro'),{...gasto,autorId:'fora'}));
 await assertSucceeds(deleteDoc(ref('bruno','gastos/g')));
});
test('validar centavos, divisão, participantes e autoria também nas edições',async()=>{
 await preparar();
 for(const patch of [
  {valorCentavos:0},{valorCentavos:-1},{valorCentavos:10.5},{valorCentavos:'10000'},
  {descricao:''},{descricao:'x'.repeat(201)},{pagadorId:'fora'},
  {participantes:[]},{participantes:['ana','ana']},{participantes:['ana','fora']},{participantes:[1]},
  {autorId:'ana'},{quotas:{ana:0}}
 ])await assertFails(setDoc(ref('bruno','gastos/g'),{...gasto,...patch}));
 await assertSucceeds(setDoc(ref('bruno','gastos/g'),gasto));
 for(const patch of [{valorCentavos:0},{participantes:['fora']},{pagadorId:'fora'},{extra:true}])
  await assertFails(updateDoc(ref('bruno','gastos/g'),patch));
 await assertSucceeds(updateDoc(ref('bruno','gastos/g'),{pagadorId:'bruno',participantes:['ana','carla']}));
});
test('entrada posterior não modifica despesas e encerramento bloqueia novas ações; limpeza administrativa permitida',async()=>{
 await preparar();await assertSucceeds(setDoc(ref('bruno','gastos/g'),gasto));
 await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),'eventos/e/participantes/davi'),{uid:'davi',nome:'Davi',papel:'membro',presenca:'vou'}));
 assert.deepEqual((await getDoc(ref('davi','gastos/g'))).data().participantes,['ana','bruno','carla']);
 await assertSucceeds(updateDoc(doc(db('ana'),'eventos/e'),{status:'concluido'}));
 await assertFails(setDoc(ref('bruno','gastos/outro'),gasto));
 await assertFails(updateDoc(ref('bruno','gastos/g'),{descricao:'Outra'}));
 await assertFails(deleteDoc(ref('ana','gastos/g')));
 await assertFails(registrar('davi'));
 await assertSucceeds(getDoc(ref('davi','gastos/g')));
 await assertSucceeds(updateDoc(doc(db('ana'),'eventos/e'),{status:'excluindo'}));
 await assertSucceeds(deleteDoc(ref('ana','gastos/g')));
 await assertSucceeds(deleteDoc(ref('ana','controle/membrosGastos')));
 assert.equal((await getDoc(ref('ana','gastos/g'))).exists(),false);
});
