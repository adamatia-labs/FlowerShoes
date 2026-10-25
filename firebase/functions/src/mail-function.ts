import { getFirestore } from 'firebase-admin/firestore';
import { error, info } from 'firebase-functions/logger';
import { defineInt, defineSecret, defineString } from 'firebase-functions/params';
import { onDocumentCreated } from 'firebase-functions/v2/firestore';
import { deliverMail, mailMessageId } from './mail';
import { firestoreMailStore } from './mail-store';
import { sendSmtpMail } from './mail-smtp';

const SMTP_HOST = defineString('SMTP_HOST', { default: 'smtp.gmail.com' });
const SMTP_PORT = defineInt('SMTP_PORT', { default: 465 });
const SMTP_USER = defineString('SMTP_USER', { default: '' });
const SMTP_FROM = defineString('SMTP_FROM', { default: '' });
const SMTP_PASSWORD = defineSecret('SMTP_PASSWORD');

/** Firebase Extensions 없이 운영하는 2세대 Firestore 트리거. 다른 함수에는 SMTP 비밀 값을 주지 않는다. */
export const onMailCreated = onDocumentCreated({
  document: 'mail/{mailId}', region: 'asia-northeast3', secrets: [SMTP_PASSWORD],
  retry: true, timeoutSeconds: 120, maxInstances: 2, concurrency: 1,
}, async (event) => {
  if (!event.data) return;
  const ref = getFirestore().doc(`mail/${event.params.mailId}`);
  try {
    const result = await deliverMail(firestoreMailStore(ref), async (mail) => {
      const from = SMTP_FROM.value() || SMTP_USER.value();
      await sendSmtpMail({
        host: SMTP_HOST.value(), port: SMTP_PORT.value(), user: SMTP_USER.value(),
        password: SMTP_PASSWORD.value(), from,
      }, mail, mailMessageId(event.project, ref.id, from));
    });
    if (result === 'failed') error('mail_delivery_failed', { mailId: ref.id });
    else info('mail_delivery', { mailId: ref.id, result });
  } catch {
    // SMTP·Firestore 예외의 원문을 플랫폼 로그에 노출하지 않는다.
    throw new Error('MAIL_DELIVERY_RETRY');
  }
});
