package com.sadturtleman.flowershoes.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** 표 한 줄(개인정보 · 광고성 정보): 항목 · 내용 · 보유 기간(brand). */
data class TermsRow(val title: String, val body: String, val retention: String? = null)

/**
 * 약관 문서(Figma 1 · 약관 문서, 꽃신_약관.md 1–3). 회사명 Adamatia Labs · 시행일 2026.10.24(1.1: 유료 서비스 · 청약철회).
 * [table] 은 흰 카드 안 줄, [sections] 는 카드 아래 제목 + 본문.
 */
enum class TermsDoc(
    val title: String,
    val header: String,
    val table: List<TermsRow>,
    val sections: List<Pair<String, String>>,
) {
    SERVICE(
        "서비스 이용약관",
        "시행일 2026.10.24 · 버전 1.1",
        emptyList(),
        listOf(
            "제1조 목적" to "이 약관은 Adamatia Labs이 제공하는 꽃신 앱과 관련 서비스를 이용할 때 회사와 회원 사이의 권리, 의무, 책임을 정합니다.",
            "제2조 용어" to "회원: 약관에 동의하고 소셜 로그인으로 이용하는 사람\n" +
                "연결: 두 회원이 초대 링크로 서로를 연인으로 등록한 상태\n" +
                "콘텐츠: 편지, 그림, 사진, 일정, 휴가 기록, 상태, 보고싶어 등",
            "제3조 약관의 게시와 변경" to "설정 › 정보와 가입 화면에 게시합니다. 바꿀 때는 시행 7일 전(불리한 변경은 30일 전)부터 알리고 다시 동의를 받습니다. 동의하지 않으면 탈퇴할 수 있습니다.",
            "제4조 회원가입" to "만 14세 이상만 가입할 수 있습니다. 소셜 로그인 후 약관과 개인정보 수집·이용에 동의하면 가입이 완료됩니다.",
            "제5조 서비스 내용" to "전역일·복무 일수 계산과 D-day, 일정·휴가 기록과 공유, 편지·그림(리플레이)·사진·예약 편지, 상태·보고싶어·위젯·알림, 추억 책과 내 기록 내보내기, 유료 서비스",
            "제6조 콘텐츠와 암호화" to "콘텐츠는 기기에서 암호화되어 연결된 상대 기기로만 전달되며, 회사는 내용을 열람하거나 제공할 수 없습니다. 콘텐츠의 권리는 만든 회원에게 있습니다. 서버는 전달이 끝난 콘텐츠를 보관하지 않으므로, 두 기기를 모두 잃으면 회사도 복구할 수 없습니다. 예약 편지의 공개 전 열람 제한은 앱 기능으로 제공됩니다.",
            "제7조 군 관련 정보" to "전역일·진급일·휴가 일수 등은 입력값과 공개 기준으로 계산한 참고용이며 법적·행정적 효력이 없습니다. 실제 복무와 휴가는 소속 부대와 관련 규정을 따릅니다.",
            "제8조 회원의 의무" to "다음 행위를 해서는 안 됩니다.\n" +
                "· 부대 위치·시설·훈련·작전 정보, 군사시설 사진 등 군사 보안을 해치는 내용을 올리거나 보내는 행위\n" +
                "· 다른 사람의 계정이나 기기로 이용하는 행위\n" +
                "· 상대의 동의 없이 콘텐츠를 외부에 퍼뜨리는 행위\n" +
                "· 법령에 어긋나는 콘텐츠를 주고받는 행위\n" +
                "· 서비스 역설계나 운영 방해",
            "제8조의2 유료 서비스" to "꽃신 평생 9,900원(출시 기념 7,900원) · 추억 책 PDF 5,900원. 앱마켓 결제이며 결제 · 환불은 앱마켓 정책도 따릅니다. " +
                "\"평생\"은 서비스가 운영되는 동안 기능을 쓸 수 있다는 뜻이고, 연결된 상대와 함께 쓰는 혜택은 연결된 동안만입니다. 같은 계정으로 로그인하면 구매가 복원됩니다.",
            "제8조의3 청약철회와 환불" to "꽃신 평생은 결제 후 7일 안에, 혜택을 쓰지 않았다면 철회할 수 있습니다. " +
                "추억 책 PDF 는 구매 전 책 전체를 미리 볼 수 있으며, 만들어 받은 뒤에는 철회가 제한됩니다(전자상거래법 제17조 제2항). 파일이 만들어지지 않는 등 회사 책임의 문제는 다시 만들어 드리거나 환불합니다.",
            "제9조 서비스의 변경과 중단" to "운영·기술상 필요에 따라 서비스를 바꿀 수 있으며 중요한 변경은 미리 알립니다. 종료할 때는 30일 전에 알리고, 그동안 내 기록 내보내기 · 추억 책 PDF 로 보관할 수 있게 합니다.",
            "제10조 연결 해제와 탈퇴" to "연결은 언제든 혼자서 해제할 수 있고, 서버에서 전달 중인 데이터는 바로 삭제됩니다. 각자 기기에 남은 사본은 각자 정합니다. 탈퇴하면 계정 정보 · 유료 서비스 권리가 삭제되며 되돌릴 수 없습니다. 탈퇴 전에 내 기록 내보내기를 안내합니다.",
            "제11조 책임의 제한" to "기기 분실, 부대 보안 앱이나 통신 환경으로 인한 알림 지연, 참고용 군 정보를 실제 판단에 쓴 경우 등은 회사의 고의·중대한 과실이 없으면 책임지지 않습니다.",
            "제12조 분쟁 해결" to "문의는 설정 › 문의하기로 접수합니다. 대한민국 법을 따르며 분쟁은 관할 법원에서 해결합니다.",
        ),
    ),
    PRIVACY(
        "개인정보 수집 · 이용 동의",
        "필수 · Adamatia Labs",
        listOf(
            TermsRow("회원 식별 · 로그인", "소셜 로그인 식별자, 제공자가 넘겨주는 이름(있는 경우)", "탈퇴 시까지"),
            TermsRow("연인 연결 · 소식 전달", "역할, 기기 공개키, 알림 토큰, 기기 종류 · OS 버전", "탈퇴 또는 연결 해제 시까지"),
            TermsRow("알림 시간 조절", "조용한 시간 설정, 시간대", "탈퇴 시까지"),
            TermsRow("유료 서비스 · 구매 복원", "구매 상품, 결제 시각, 앱마켓 거래 ID, 이용권 상태", "5년(전자상거래법)"),
            TermsRow("부정 이용 방지", "접속 기록(IP, 접속 시각)", "3개월"),
            TermsRow("앱 이용 통계(Google Analytics 4)", "화면 이름, 고른 선택지(편지지 · 글꼴 · 상품)와 개수, 앱 설치 단위 ID, 기기 종류 · OS 버전 · 국가", "14개월"),
        ),
        listOf(
            "수집하지 않는 것" to "편지 · 그림 · 사진 · 일정 · 휴가 기록의 내용(기기에서 암호화), 연락처, 위치 정보, 결제 수단 정보(앱마켓이 처리)",
            "이용 통계" to "어느 화면에서 무엇을 골랐는지를 Google Analytics 4 로 모아 앱을 고치는 데 씁니다. 정해진 항목만 보내고, 편지 · 제목 · 이름처럼 직접 입력한 글자는 보내지 않습니다. 광고 ID(ADID · IDFA)는 통계에 모으지 않고, 광고 개인화에도 쓰지 않습니다.",
            "서버가 알 수 있는 것" to "누가 · 언제 · 어떤 종류의 소식을 보냈는지와 그 크기. 알림을 보내고 조용한 시간을 지키는 데 씁니다.",
            "동의를 거부할 권리" to "거부할 수 있지만, 필수 항목이라 거부하면 서비스를 이용할 수 없습니다.",
        ),
    ),
    MARKETING(
        "이벤트 · 혜택 알림",
        "선택 · 동의하지 않아도 이용에 영향 없음",
        listOf(
            TermsRow("목적", "이벤트, 혜택, 새 기능 안내"),
            TermsRow("받는 방법", "앱 푸시 알림"),
            TermsRow("이용 항목", "알림 토큰"),
            TermsRow("보유 기간", "동의 철회 또는 탈퇴 시까지"),
        ),
        listOf(
            "보내는 시간" to "밤 9시부터 아침 8시까지는 보내지 않습니다.",
            "철회" to "설정 › 알림에서 언제든 끌 수 있습니다. 2년마다 수신 동의 여부를 다시 확인해 드립니다.",
            "표시" to "광고성 알림 앞에는 \"(광고)\"를 붙입니다.",
        ),
    ),
}

/** 약관 문서(온보딩 약관 동의 › 문서 · 설정 › 이용약관): 머리말 · 표 카드 · 제목 + 본문, 아래 확인. */
@Composable
fun KkTermsScreen(doc: TermsDoc, onClose: () -> Unit) {
    val c = KkTheme.colors
    Column(Modifier.fillMaxSize().background(c.canvas).statusBarsPadding().navigationBarsPadding()) {
        KkTopBar(doc.title, onLeading = onClose, leading = KkTopBarLeading.Close)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = KkSpacing.xl, vertical = KkSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(doc.header, style = KkTheme.type.caption, color = c.sub)
            if (doc.table.isNotEmpty()) {
                val shape = RoundedCornerShape(KkRadius.lg)
                Column(Modifier.fillMaxWidth().kkElevation(KkElevation.Low, shape).clip(shape).background(c.surface)) {
                    doc.table.forEachIndexed { i, row ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(row.title, style = KkTheme.type.label, color = c.ink)
                            Text(row.body, style = KkTheme.type.body, color = c.sub)
                            row.retention?.let { Text(it, style = KkTheme.type.caption, color = c.accent) }
                        }
                    }
                }
            }
            doc.sections.forEach { (title, body) ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = KkTheme.type.labelBold, color = c.ink)
                    Text(body, style = KkTheme.type.body, color = c.sub)
                }
            }
        }
        KkPrimaryButton("확인", onClose, Modifier.padding(start = KkSpacing.xl, end = KkSpacing.xl, top = KkSpacing.xxl, bottom = 14.dp))
    }
}
