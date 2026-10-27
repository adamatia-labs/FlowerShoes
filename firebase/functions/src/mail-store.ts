import { DocumentReference } from 'firebase-admin/firestore';
import { Delivery, MailStore, nextMailClaim } from './mail';

export function firestoreMailStore(ref: DocumentReference): MailStore {
  return {
    claim: (now, claimId) => ref.firestore.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      if (!snap.exists) return 'skip';
      const delivery = nextMailClaim(snap.get('delivery'), now, claimId);
      if (typeof delivery === 'string') return delivery;
      tx.update(ref, { delivery });
      return { data: snap.data(), delivery };
    }),
    finish: (claimId: string, delivery: Delivery) => ref.firestore.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      if (!snap.exists || snap.get('delivery.claimId') !== claimId || snap.get('delivery.state') !== 'PROCESSING') return false;
      tx.update(ref, { delivery });
      return true;
    }),
  };
}
