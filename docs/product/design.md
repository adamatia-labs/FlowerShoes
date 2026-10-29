# 꽃신 디자인 기준

화면과 디자인 시스템의 기준은 Figma 파일이다. 이 문서는 어디를 봐야 하는지와, 화면을 만들 때 지킬 규칙을 적는다. 기능 결정은 [planning.md](planning.md)를 본다.

## Figma

파일: [꽃신 디자인](https://www.figma.com/design/fJubLUZMoiPmIV2tzgCx1k) (fileKey `fJubLUZMoiPmIV2tzgCx1k`)

| 페이지 | 쓰임 |
|---|---|
| 🎨 꽃신 v2 디자인 시스템 | 컴포넌트(기본 `340:89` · 조합 `341:82`), Phosphor 아이콘, 큰 화면 Nav Rail `428:15898` |
| ✅ 최종 화면 v2 | **구현 기준 화면.** 화면마다 가질 수 있는 모든 상태가 있다 |
| 🧪 리디자인 시안 | 시안(참고용) |
| 🗂 최종 정리 외 옛 페이지 | 옛 디자인. 구현에 쓰지 않는다 |

최종 화면 v2 섹션:

| 섹션 | 노드 |
|---|---|
| 1 · 로그인 · 온보딩 · 연결 | [`343:83`](https://www.figma.com/design/fJubLUZMoiPmIV2tzgCx1k?node-id=343-83) |
| 2 · 홈 | [`350:686`](https://www.figma.com/design/fJubLUZMoiPmIV2tzgCx1k?node-id=350-686) |
| 3 · 캘린더 · 휴가 | [`354:1267`](https://www.figma.com/design/fJubLUZMoiPmIV2tzgCx1k?node-id=354-1267) |
| 4 · 편지 | [`360:2610`](https://www.figma.com/design/fJubLUZMoiPmIV2tzgCx1k?node-id=360-2610) |
| 5 · 그림 그리기 | [`364:3873`](https://www.figma.com/design/fJubLUZMoiPmIV2tzgCx1k?node-id=364-3873) |
| 6 · 추억 | [`366:3958`](https://www.figma.com/design/fJubLUZMoiPmIV2tzgCx1k?node-id=366-3958) |
| 7 · 기기 바꾸기 · 연결 해제 | [`366:3961`](https://www.figma.com/design/fJubLUZMoiPmIV2tzgCx1k?node-id=366-3961) |
| 8 · 설정 | [`376:4458`](https://www.figma.com/design/fJubLUZMoiPmIV2tzgCx1k?node-id=376-4458) |
| 9 · 위젯 · 알림 · 공통 상태 | [`383:5321`](https://www.figma.com/design/fJubLUZMoiPmIV2tzgCx1k?node-id=383-5321) |
| 10 · 테마 · 다크 확인 | [`386:5350`](https://www.figma.com/design/fJubLUZMoiPmIV2tzgCx1k?node-id=386-5350) |
| 11 · 큰 화면 | [`429:10725`](https://www.figma.com/design/fJubLUZMoiPmIV2tzgCx1k?node-id=429-10725) |

화면 이름은 `"<화면> · <상태>"` 형식이다. 예시 데이터: 지수(곰신) · 민준(군인, 공군 일병), 오늘 2026-10-28(수), 입대 2026.04.20, 전역 2028.02.19(D-479).

## 테마 v2 토큰

Figma 변수 컬렉션 "테마 v2"(`VariableCollectionId:334:122`), 모드 4개: **곰신 · Light / 곰신 · Dark / 군인 · Light / 군인 · Dark**. 기본 색은 "Primitives" 컬렉션에 있고, 테마 v2가 그것을 가리킨다. 값은 Figma에서 읽는다.

| 토큰 | Figma 변수 | 쓰임 |
|---|---|---|
| canvas | paper/canvas | 화면 바탕 |
| surface | paper/surface | 시트 · 카드 · 입력란 |
| bar | paper/bar | 탭 바 · 위 막대 |
| tag | paper/tag | 종이 태그 바탕(위에는 ink 글자만) |
| tape | paper/tape | 테이프 |
| ink | ink/primary | 본문 글자 |
| sub | ink/secondary | 보조 글자 |
| line | line/default | 구분선 · 테두리 |
| accent | accent/default | 주 동작 |
| love · loveOn | love/bg · love/on | **보고싶어 버튼에만** |
| stamp | stamp/ink | 소인 · 우표 |
| plant | deco/plant | 장식 식물 |
| danger · success | | 오류 · 완료 |
| 일정 색 6개 | | 휴가 · 외박 · 면회 · 훈련 · 당직 · 기념일 |

글꼴 변수: display · number · hand · body. 텍스트 스타일(`테마 v2/…`): D-day/L · D-day/M · D-day/S · Title · Hand/L · Hand/Letter · Body · Label · Label/Bold · Caption.

## 화면 규칙

시각 언어:
- 카드를 줄인다. 중요한 것 하나만 종이 · 봉투 · 폴라로이드처럼 다르게 생긴 물건으로 보여 준다.
- 자리표시 그림 대신 실제 편지 첫 줄과 실제 그림을 보여 준다.
- 하트는 보고싶어에만. 나머지 상징은 우표 · 소인 · 봉투 · 군번줄.
- 기울기 · 테이프는 **보여 주기만 하는 물건**(홈 태그 · 폴라로이드 · 추억 페이지 · 책 표지)에만. 읽고 행동하거나 입력하는 것(온보딩 안내, 편지 쓰기 · 읽기, 입력란, 목록, 편지함 봉투, 시트, 버튼)은 반듯하게.
- 제목 · D-day 숫자는 손글씨 글꼴, 본문은 고딕.
- 아이콘은 **Phosphor Regular**(MIT). 쓰는 아이콘만 복사해 넣는다(라이브러리 의존 없음).

테마:
- 곰신 테마(편지지 · 말린 꽃 · 크라프트 · 로즈)와 군인 테마(군사우편 · 카키 · 스텐실 · 군번줄). 역할에 따라 기본값이 정해지고 설정에서 바꿀 수 있다. 다크 모드도 출시 때 함께.
- **편지지는 콘텐츠다.** 보낸 사람이 고른 편지지 그대로 보이고, 받는 사람의 테마나 다크 모드에 따라 바뀌지 않는다. UI(바탕 · 글자 · 버튼 · 봉투 · 탭 바)만 토큰을 따른다.
- 소인 문구: 군인이 보낸 편지는 "군사우편", 곰신이 보낸 편지는 "꽃신 우편".

편지함 봉투 표시: 봉인("새") = 안 읽은 편지 · 폴라로이드 = 그림이 든 편지 · 소인("보냄") = 내가 보낸 편지 · 우표(D-day) = 그 밖의 받은 편지.

글(카피):
- 설명 문구를 지운다. 남기는 글은 제목 · 버튼 · 입력란 라벨 · 오류 · 되돌릴 수 없는 동작 앞 한 줄 경고 · 법적 고지뿐. 화면이 무엇인지 설명하는 "~해요" 문장은 쓰지 않는다.
- 선택 카드에도 설명 문장을 붙이지 않는다. 고르는 데 필요한 사실만 남긴다(예: 군종 "18개월", 지우기 "되돌릴 수 없어요"). 입력란 예시 문구(placeholder)도 쓰지 않는다.
- 슬로건은 **"편지로 이어지는 우리 둘만의 달력"** 하나(로그인 · 스플래시 · 스토어 · OG).
- 버튼엔 동작 이름("해제하기", "지우기"). 예/아니요는 쓰지 않는다.
- 알림은 제목 + 사실 한 줄. 매년 반복 기념일 당일 09:00: 제목 "오늘은 {일정 제목}", 본문 "N년째 돌아온 날"(커플 기념일은 "N주년이에요" / "오늘은 우리 기념일").

플랫폼(Material 3 · Apple HIG):
- 화면은 공통으로 만들고, 뒤로 가기(iOS 가장자리 스와이프 · Android 예측형) · 공유 시트 · 사진 선택기 · 생체 인증 · Apple 로그인은 각 플랫폼 기본 기능을 쓴다.
- 소셜 로그인 버튼은 각 사 가이드 그대로(카카오 #FEE500 · 말풍선 심볼 · "카카오 로그인" / Apple 검정 "Apple로 계속하기" / Google 흰 바탕 · 회색 테두리 · 컬러 G "Google로 계속하기"). 같은 크기 세로 스택, 순서 카카오 → Apple → Google. 브랜드 색은 토큰 규칙의 예외.
- 날짜 입력은 모두 Material 3 피커(하루: 모달 캘린더, 기간: 기간 피커), 시간은 입력형.
- 확인 창은 버튼 3개까지, 위험한 동작엔 항상 "취소". 정보 알림은 확인 창 대신 토스트.
- 설정은 제목 붙은 둥근 카드 그룹(inset grouped). 하위 화면으로 가는 줄에만 ›.
- 접근성: 터치 영역 48dp(44pt), 글자 최소 11pt(장식 글자 예외), 대비 4.5:1(18pt 이상 · 굵은 글자 3:1), 글자 200% 확대.
- 큰 화면: 콘텐츠 최대 폭 600dp 가운데 정렬. 편지함은 목록 | 읽기 2단, 캘린더는 달력 옆에 일정 목록, 내비게이션은 Nav Rail.

## Figma에서 코드로

- 화면을 구현할 때는 Figma MCP(`get_design_context`, `get_screenshot`)로 위 섹션의 해당 화면 노드를 읽는다. 결과는 참고용이고, 코드는 이 프로젝트의 테마 · 컴포넌트로 다시 짠다.
- 색 · 글자는 하드코딩하지 않고 테마 v2 토큰과 텍스트 스타일에 대응하는 코드 토큰을 쓴다.
