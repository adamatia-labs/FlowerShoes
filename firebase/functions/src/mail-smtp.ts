import nodemailer from 'nodemailer';
import SMTPTransport from 'nodemailer/lib/smtp-transport';
import { isEmail, Mail } from './mail';

export type SmtpConfig = { host: string; port: number; user: string; password: string; from: string };

export function smtpOptions(config: SmtpConfig): SMTPTransport.Options {
  if (!config.host || /[\s/:@]/.test(config.host) || ![465, 587].includes(config.port)
    || !isEmail(config.user) || !isEmail(config.from) || !config.password.trim()) {
    throw Object.assign(new Error('MAIL_CONFIG'), { code: 'MAIL_CONFIG' });
  }
  return {
    host: config.host, port: config.port, secure: config.port === 465, requireTLS: true,
    auth: { user: config.user, pass: config.password },
    tls: { minVersion: 'TLSv1.2', rejectUnauthorized: true },
    connectionTimeout: 10_000, greetingTimeout: 10_000, socketTimeout: 20_000,
    disableFileAccess: true, disableUrlAccess: true, logger: false, debug: false,
  };
}

export async function sendSmtpMail(config: SmtpConfig, mail: Mail, messageId: string): Promise<void> {
  const transporter = nodemailer.createTransport(smtpOptions(config));
  try {
    const result = await transporter.sendMail({
      from: { name: '꽃신', address: config.from }, to: mail.to,
      subject: mail.message.subject, text: mail.message.text, messageId,
    });
    if (!result.accepted.length || result.rejected.length) {
      throw Object.assign(new Error('SMTP_REJECTED'), { responseCode: 550 });
    }
  } finally { transporter.close(); }
}
