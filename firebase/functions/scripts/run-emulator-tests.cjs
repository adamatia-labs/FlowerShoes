const { existsSync, mkdtempSync, copyFileSync, rmSync, writeFileSync } = require('node:fs');
const { tmpdir } = require('node:os');
const { delimiter, join, resolve, dirname } = require('node:path');
const { spawnSync } = require('node:child_process');

const suite = process.argv[2];
if (!['account', 'unlink'].includes(suite)) throw new Error('Choose account or unlink tests');
const account = suite === 'account';
const temporary = mkdtempSync(join(tmpdir(), 'kkotsin-account-test-'));
try {
  copyFileSync('../firestore.rules', join(temporary, 'firestore.rules'));
  copyFileSync('../storage.rules', join(temporary, 'storage.rules'));
  writeFileSync(join(temporary, 'firebase.json'), JSON.stringify({
    firestore: { rules: 'firestore.rules' }, storage: { rules: 'storage.rules' },
    emulators: { firestore: { port: account ? 8187 : 8186 }, ...(account ? { auth: { port: 9098 } } : {}), storage: { port: account ? 9298 : 9299 }, ui: { enabled: false }, singleProjectMode: true },
  }));
  const command = `node --test lib/test/${account ? 'account-deletion' : 'unlink'}.emulator.js`;
  const args = ['emulators:exec', '--project', `demo-kkotsin-${suite}`, '--only', account ? 'firestore,auth,storage' : 'firestore,storage', '--config', join(temporary, 'firebase.json'), command];
  // Windows npm shims are .cmd files; invoke their JS entry point without shell string interpolation.
  const entry = process.platform === 'win32' ? (process.env.PATH ?? '').split(delimiter)
    .map((directory) => join(directory, 'node_modules/firebase-tools/lib/bin/firebase.js')).find(existsSync) : undefined;
  if (process.platform === 'win32' && !entry) throw new Error('Install firebase-tools globally before running emulator tests');
  const result = spawnSync(entry ? process.execPath : 'firebase', entry ? [entry, ...args] : args, { stdio: 'inherit' });
  if (result.error) throw result.error;
  process.exitCode = result.status ?? 1;
} finally {
  if (dirname(resolve(temporary)) === resolve(tmpdir()) && temporary.includes('kkotsin-account-test-')) rmSync(temporary, { recursive: true, force: true });
}
