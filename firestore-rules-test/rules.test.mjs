import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, it } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, setDoc } from 'firebase/firestore';

const [host, port] = (process.env.FIRESTORE_EMULATOR_HOST ?? '127.0.0.1:8080').split(':');

const valid = (id = 'd1') => ({
  documentId: id,
  title: 'Book',
  type: 'pdf',
  sizeBytes: 100,
  checksum: 'abc',
  isFavorite: false,
  createdAt: 1,
  updatedAt: 2,
  schemaVersion: 1,
});

let env;
before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'paperly-rules-test',
    firestore: { rules: readFileSync('../firestore.rules', 'utf8'), host, port: Number(port) },
  });
});
after(async () => env.cleanup());
beforeEach(async () => env.clearFirestore());

const alice = () => env.authenticatedContext('alice').firestore();
const bob = () => env.authenticatedContext('bob').firestore();
const anon = () => env.unauthenticatedContext().firestore();
const path = (db, uid = 'alice', id = 'd1') => doc(db, `users/${uid}/documents/${id}`);

describe('access isolation', () => {
  it('owner can create, read and delete', async () => {
    await assertSucceeds(setDoc(path(alice()), valid()));
    await assertSucceeds(getDoc(path(alice())));
    await assertSucceeds(deleteDoc(path(alice())));
  });
  it('signed-out user is denied', async () => {
    await assertFails(setDoc(path(anon()), valid()));
    await assertFails(getDoc(path(anon())));
  });
  it('another signed-in user is denied read, write and delete', async () => {
    await assertSucceeds(setDoc(path(alice()), valid()));
    await assertFails(getDoc(path(bob())));
    await assertFails(setDoc(path(bob()), valid()));
    await assertFails(deleteDoc(path(bob())));
  });
  it('unknown paths are denied even for the owner', async () => {
    await assertFails(setDoc(doc(alice(), 'users/alice/other/x'), { a: 1 }));
    await assertFails(setDoc(doc(alice(), 'stuff/x'), { a: 1 }));
  });
});

describe('field validation', () => {
  it('accepts optional fields, null or present', async () => {
    const full = { ...valid(), folderId: 'f1', tags: ['a'], deletedAt: 5 };
    await assertSucceeds(setDoc(path(alice()), full));
    await assertSucceeds(setDoc(path(alice()), { ...valid(), folderId: null, tags: null, deletedAt: null }));
  });
  it('rejects an unknown extra field', async () => {
    await assertFails(setDoc(path(alice()), { ...valid(), localUri: 'file:///x' }));
  });
  it('rejects a missing required field', async () => {
    const { checksum, ...rest } = valid();
    await assertFails(setDoc(path(alice()), rest));
  });
  it('rejects wrong types and sizes', async () => {
    await assertFails(setDoc(path(alice()), { ...valid(), sizeBytes: '100' }));
    await assertFails(setDoc(path(alice()), { ...valid(), sizeBytes: -1 }));
    await assertFails(setDoc(path(alice()), { ...valid(), title: 'x'.repeat(301) }));
    await assertFails(setDoc(path(alice()), { ...valid(), isFavorite: 'yes' }));
    await assertFails(setDoc(path(alice()), { ...valid(), tags: 'a' }));
  });
  it('rejects a documentId that differs from the path', async () => {
    await assertFails(setDoc(path(alice()), valid('other')));
  });
});
