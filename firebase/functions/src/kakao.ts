// 카카오 사용자 정보(v2/user/me)에서 쓰는 것. 이름은 온보딩에 이름 단계가 없어 처음 내 이름으로 쓴다.

/** 닉네임(앞뒤 공백 제거, 10자). 동의 항목 profile_nickname 이 꺼져 있으면 없음. */
export function kakaoNickname(me: unknown): string | undefined {
  const nickname = (me as { kakao_account?: { profile?: { nickname?: unknown } } } | null)?.kakao_account?.profile?.nickname;
  if (typeof nickname !== 'string') return undefined;
  const trimmed = nickname.trim();
  return trimmed ? [...trimmed].slice(0, 10).join('') : undefined;
}
