import { DocumentSnapshot, getFirestore, QueryDocumentSnapshot, Timestamp, Transaction } from 'firebase-admin/firestore';

interface InviteCreation { token: string; createdAt: Timestamp }

const creation = (doc: DocumentSnapshot): InviteCreation | undefined =>
  doc.createTime ? { token: doc.id, createdAt: doc.createTime } : undefined;

function compare(a: InviteCreation, b: InviteCreation): number {
  if (a.createdAt.seconds !== b.createdAt.seconds) return a.createdAt.seconds < b.createdAt.seconds ? -1 : 1;
  if (a.createdAt.nanoseconds !== b.createdAt.nanoseconds) return a.createdAt.nanoseconds < b.createdAt.nanoseconds ? -1 : 1;
  return a.token === b.token ? 0 : a.token < b.token ? -1 : 1;
}

/** Server-only high-water mark survives failed cleanup and expiry/cancellation of the newest link. */
export const inviteHeadRef = (uid: string) => getFirestore().doc(`inviteHeads/${uid}`);

/** Read inside the same transaction that accepts or advances a link. Legacy clients need no new fields. */
export async function readInviteOrder(tx: Transaction, uid: string, observed?: DocumentSnapshot) {
  const db = getFirestore();
  const [head, pending] = await Promise.all([
    tx.get(inviteHeadRef(uid)), tx.get(db.collection('pairings').where('uid', '==', uid)),
  ]);
  let latest: InviteCreation | undefined = head.exists && typeof head.get('token') === 'string' && head.get('createdAt') instanceof Timestamp
    ? { token: head.get('token'), createdAt: head.get('createdAt') } : undefined;
  for (const doc of [...pending.docs, ...(observed ? [observed] : [])]) {
    const candidate = creation(doc);
    if (candidate && (!latest || compare(candidate, latest) > 0)) latest = candidate;
  }
  return {
    latest,
    isLatest: (doc: DocumentSnapshot) => {
      const value = creation(doc);
      return !!value && !!latest && compare(value, latest) === 0;
    },
    obsolete: pending.docs.filter((doc: QueryDocumentSnapshot) => latest && compare(creation(doc)!, latest) < 0),
  };
}
