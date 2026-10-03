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

const validReading = (id = 'd1') => ({
  documentId: id,
  locator: '{"href":"c1.xhtml","type":"application/xhtml+xml","locations":{"progression":0.4}}',
  progressPercent: 0.4,
  updatedAt: 10,
  schemaVersion: 1,
});
const rpath = (db, uid = 'alice', id = 'd1') => doc(db, `users/${uid}/readingState/${id}`);

describe('readingState', () => {
  it('owner can create, update, read and delete', async () => {
    await assertSucceeds(setDoc(rpath(alice()), validReading()));
    await assertSucceeds(setDoc(rpath(alice()), { ...validReading(), progressPercent: 0.9, updatedAt: 20 }));
    await assertSucceeds(getDoc(rpath(alice())));
    await assertSucceeds(deleteDoc(rpath(alice())));
  });
  it('accepts a PDF page-number locator and the 0 / 1 progress bounds', async () => {
    await assertSucceeds(setDoc(rpath(alice()), { ...validReading(), locator: '12', progressPercent: 0 }));
    await assertSucceeds(setDoc(rpath(alice()), { ...validReading(), locator: '12', progressPercent: 1 }));
  });
  it('signed-out and other users are denied', async () => {
    await assertSucceeds(setDoc(rpath(alice()), validReading()));
    await assertFails(getDoc(rpath(anon())));
    await assertFails(setDoc(rpath(anon()), validReading()));
    await assertFails(getDoc(rpath(bob())));
    await assertFails(setDoc(rpath(bob()), validReading()));
    await assertFails(deleteDoc(rpath(bob())));
  });
  it('rejects unknown or missing fields', async () => {
    await assertFails(setDoc(rpath(alice()), { ...validReading(), text: 'book content' }));
    const { locator, ...rest } = validReading();
    await assertFails(setDoc(rpath(alice()), rest));
  });
  it('rejects bad types, bounds and sizes', async () => {
    await assertFails(setDoc(rpath(alice()), { ...validReading(), progressPercent: 1.5 }));
    await assertFails(setDoc(rpath(alice()), { ...validReading(), progressPercent: -0.1 }));
    await assertFails(setDoc(rpath(alice()), { ...validReading(), progressPercent: '0.4' }));
    await assertFails(setDoc(rpath(alice()), { ...validReading(), updatedAt: 'now' }));
    await assertFails(setDoc(rpath(alice()), { ...validReading(), locator: '' }));
    await assertFails(setDoc(rpath(alice()), { ...validReading(), locator: 'x'.repeat(2001) }));
    await assertFails(setDoc(rpath(alice()), { ...validReading(), locator: 5 }));
  });
  it('rejects a documentId that differs from the path', async () => {
    await assertFails(setDoc(rpath(alice()), validReading('other')));
  });
  it('does not loosen the documents collection', async () => {
    await assertFails(setDoc(path(alice()), validReading()));
  });
});
