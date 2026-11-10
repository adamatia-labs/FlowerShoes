package com.sadturtleman.flowershoes.core.data

/** 편집을 시작한 뒤 원본이 변경·삭제되거나 다른 관계의 기록으로 바뀌었을 때 저장을 거부한다. */
class StaleRecordException : IllegalStateException("기록이 바뀌었어요. 다시 열어 주세요")
