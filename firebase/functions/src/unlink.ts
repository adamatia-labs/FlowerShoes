/** 연결 해제 작업. 서버 전용 저장소가 최초 커플 구성원을 보존해 재시도 권한을 판단한다. */
export interface UnlinkOperation {
  members: string[];
  completed: boolean;
}

export interface UnlinkStore {
  /** 최초 호출에서는 커플을 확인하고 작업을 생성한다. 이후에는 저장된 구성원을 확인한다. */
  begin(coupleId: string, uid: string): Promise<UnlinkOperation>;
  deleteCouple(coupleId: string): Promise<void>;
  deleteFiles(coupleId: string): Promise<void>;
  /** 현재도 이 커플에 속할 때만 연결 정보와 알림 상태를 지운다. */
  clearMember(coupleId: string, uid: string): Promise<void>;
  /** 완료와 알림 대기 항목을 하나의 트랜잭션으로 기록한다. */
  complete(coupleId: string): Promise<void>;
}

export class UnlinkNotFound extends Error {}
export class UnlinkForbidden extends Error {}

/** 같은 작업을 여러 번, 또는 두 사람이 동시에 호출해도 완료할 수 있다. */
export async function unlink(store: UnlinkStore, coupleId: string, uid: string): Promise<void> {
  const operation = await store.begin(coupleId, uid);
  if (operation.completed) return;
  await store.deleteCouple(coupleId);
  await store.deleteFiles(coupleId);
  await Promise.all(operation.members.map((member) => store.clearMember(coupleId, member)));
  await store.complete(coupleId);
}

export interface UnlinkHistory {
  operationsFor(uid: string): Promise<string[]>;
  forgetMember(coupleId: string, uid: string): Promise<void>;
  forgetNotifications(uid: string): Promise<void>;
}

/** 탈퇴자의 해제 작업을 완료한 후 식별자와 수신 대기 알림을 지운다. */
export async function forgetUnlinkMember(store: UnlinkStore, history: UnlinkHistory, uid: string): Promise<void> {
  for (const coupleId of await history.operationsFor(uid)) {
    await unlink(store, coupleId, uid);
    await history.forgetMember(coupleId, uid);
  }
  await history.forgetNotifications(uid);
}

export interface UnlinkNotificationStore {
  read(): Promise<{ recipient: string; coupleId: string } | null>;
  currentCouple(recipient: string): Promise<string | null>;
  send(recipient: string, coupleId: string): Promise<void>;
  remove(): Promise<void>;
}

/** FCM 응답 유실은 중복될 수 있다. 이미 새 커플이면 이전 연결 알림을 보내지 않는다. */
export async function deliverUnlinkNotification(store: UnlinkNotificationStore): Promise<void> {
  const notice = await store.read();
  if (!notice) return;
  const current = await store.currentCouple(notice.recipient);
  if (current === null || current === notice.coupleId) await store.send(notice.recipient, notice.coupleId);
  await store.remove();
}
