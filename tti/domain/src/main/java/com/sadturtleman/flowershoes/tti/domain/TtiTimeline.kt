package com.sadturtleman.flowershoes.tti.domain

/**
 * TTI(Time To Interactive) 를 이루는 구간.
 *
 * 화면 하나가 "쓸 수 있는 상태" 가 되기까지 거치는 네 단계이고,
 * 네 구간이 모두 닫혀야 한 건의 측정이 완성된다([TtiRecord.isComplete]).
 *
 * 순서대로 이어지는 것을 전제로 하지만 겹쳐도 기록 자체는 남는다 —
 * 합계는 구간 길이의 합이지 처음과 끝의 차이가 아니다([TtiRecord.totalTimeMillis]).
 */
enum class TtiTimeline {
    /** 화면 진입 → ViewModel 생성 · 첫 컴포지션. */
    VIEW_CREATE,

    /** 서버 요청 → 응답. */
    BACKEND,

    /** 받은 데이터로 다시 그리기 시작 → 그 프레임이 실제로 그려짐(recomposition). */
    VIEW_BINDING,

    /** 이미지 · 동영상처럼 뒤늦게 채워지는 큰 덩어리 로딩. */
    BIG_PART_LOADING,
    ;

    companion object {
        /** 한 건이 완성되려면 채워야 하는 구간 수. */
        val REQUIRED_COUNT: Int = entries.size
    }
}
