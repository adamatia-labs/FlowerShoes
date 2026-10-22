package com.sadturtleman.flowershoes.common.domain.message

/**
 * 화면 위에 잠깐 뜨는 것들. 호스트([RootComposable])가 받아서 실제로 그린다.
 *
 * 네비게이션과 같은 이유로 단일 채널이다 — 스낵바 호스트와 다이얼로그는 앱에 하나뿐이라
 * 화면마다 두면 같은 코드가 화면 수만큼 복제된다.
 *
 * 다이얼로그의 버튼 동작은 람다로 싣는다. 직렬화되지 않으므로 프로세스가 죽으면 사라진다 —
 * 되살아나야 하는 선택지는 이 경로가 아니라 화면의 UiState 로 다뤄야 한다
 * (필터 시트가 `isFilterSheetVisible` 로 되어 있는 것이 그 예다).
 */
sealed interface MessageEffect {

    /** 시스템 토스트. 화면을 떠나도 뜬다. */
    data class ShowToast(val message: String) : MessageEffect

    /**
     * 스낵바.
     *
     * @param actionLabel 함께 그릴 버튼 라벨. null 이면 버튼을 그리지 않는다.
     * @param onAction 버튼을 눌렀을 때. 라벨만 있고 동작이 없으면 버튼은 닫기로만 쓰인다.
     */
    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null,
        val onAction: (() -> Unit)? = null,
    ) : MessageEffect

    /**
     * 버튼 하나짜리 알림 다이얼로그.
     *
     * @param dismissible false 면 바깥 탭 · 뒤로가기로 닫히지 않아 버튼을 눌러야 한다.
     */
    data class ShowOneButtonDialog(
        val title: String? = null,
        val description: String,
        val dismissible: Boolean = true,
        val buttonText: String,
        val onClickButton: (() -> Unit)? = null,
    ) : MessageEffect

    /** 버튼 둘짜리 확인 다이얼로그. */
    data class ShowTwoButtonDialog(
        val title: String? = null,
        val description: String,
        val dismissible: Boolean = true,
        val confirmText: String,
        val onConfirm: (() -> Unit)? = null,
        val cancelText: String,
        val onCancel: (() -> Unit)? = null,
    ) : MessageEffect
}
