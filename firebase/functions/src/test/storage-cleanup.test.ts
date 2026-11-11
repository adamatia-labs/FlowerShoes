import { test } from 'node:test';
import assert from 'node:assert/strict';
import { deleteStoragePrefix } from '../storage-cleanup';

test('storage cleanup processes the full prefix and accepts already missing individual files', async () => {
  let options: unknown;
  await deleteStoragePrefix({ deleteFiles: async (value) => { options = value; throw [{ code: 404 }, { code: 404 }]; } }, 'couples/old/');
  assert.deepEqual(options, { prefix: 'couples/old/', force: true });
});

test('storage cleanup preserves permission and service failures even alongside missing files', async () => {
  for (const code of [403, 429, 500]) {
    const errors = [{ code: 404 }, { code }];
    await assert.rejects(deleteStoragePrefix({ deleteFiles: async () => { throw errors; } }, 'couples/old/'), (error) => error === errors);
  }
});

test('storage cleanup does not treat a missing bucket or failed listing as an empty prefix', async () => {
  for (const error of [Object.assign(new Error('bucket missing'), { code: 404 }), new Error('offline'), []]) {
    await assert.rejects(deleteStoragePrefix({ deleteFiles: async () => { throw error; } }, 'couples/old/'), (actual) => actual === error);
  }
});

test('storage cleanup accepts a fully deleted batch', async () => {
  await deleteStoragePrefix({ deleteFiles: async () => {} }, 'couples/old/');
});
