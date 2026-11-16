const {readFileSync} = require('node:fs');
const {test, before, after, beforeEach} = require('node:test');
const {initializeTestEnvironment, assertSucceeds, assertFails} = require('@firebase/rules-unit-testing');
const {doc, setDoc, getDoc, getDocs, collection, updateDoc, deleteDoc} = require('firebase/firestore');
let env;
before(async () => {
  env = await initializeTestEnvironment({projectId:'demo-bora-juntos',
    firestore:{rules:readFileSync('firestore.rules','utf8'),host:'127.0.0.1',port:8080}});
});
beforeEach(async () => env.clearFirestore());
after(async () => { if(env) await env.cleanup(); });
const db = (uid) => uid ? env.authenticatedContext(uid,{email:uid+'@example.com'}).firestore() : env.unauthenticatedContext().firestore();
const perfil = {uid:'ana',nome:'Ana Silva',email:'ana@example.com'};
test('dono cria, lê e atualiza o perfil; não lista nem exclui', async () => {
 const own=db('ana'), ref=doc(own,'usuarios/ana');
 await assertSucceeds(setDoc(ref,perfil));
 await assertSucceeds(getDoc(ref));
 await assertSucceeds(updateDoc(ref,{nome:'Ana Maria'}));
 await assertFails(getDocs(collection(own,'usuarios')));
 await assertFails(deleteDoc(ref));
});
test('anônimo e outra conta não acessam perfil nem criam associação forjada',async()=>{
 await env.withSecurityRulesDisabled(async c=>setDoc(doc(c.firestore(),'usuarios/ana'),perfil));
 for(const uid of [null,'bruno']) {
  const ref=doc(db(uid),'usuarios/ana');
  await assertFails(getDoc(ref)); await assertFails(setDoc(ref,perfil));
  await assertFails(deleteDoc(ref));
 }
 await assertFails(setDoc(doc(db('bruno'),'usuarios/bruno'),perfil));
});
test('create e update rejeitam tipos, campos extra, nome e identidade inválidos',async()=>{
 const ref=doc(db('ana'),'usuarios/ana');
 for(const data of [{...perfil,senha:'segredo'},{...perfil,role:'admin'},
  {...perfil,nome:7},{...perfil,nome:'a'},{...perfil,nome:'a'.repeat(81)},
  {...perfil,email:'outro@example.com'},{...perfil,uid:'bruno'},
  {uid:'ana',nome:'Ana'}]) {
   await assertFails(setDoc(ref,data));
 }
 await assertSucceeds(setDoc(ref,perfil));
 for(const change of [{nome:9},{nome:'a'.repeat(81)},{uid:'bruno'},
  {email:'outro@example.com'},{role:'admin'}]) await assertFails(updateDoc(ref,change));
 await assertFails(setDoc(ref,{uid:'ana',email:'ana@example.com'}));
});
test('nega outras coleções e subcoleções mesmo ao dono',async()=>{
 for(const path of ['eventos/um','usuarios/ana/eventos/um','usuarios/ana/privado/um']) {
  const ref=doc(db('ana'),path);
  await assertFails(setDoc(ref,{nome:'Teste'})); await assertFails(getDoc(ref));
 }
});
