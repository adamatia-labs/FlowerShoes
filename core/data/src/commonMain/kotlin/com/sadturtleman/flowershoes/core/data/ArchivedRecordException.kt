package com.sadturtleman.flowershoes.core.data

/** 보관본의 수정·재전송을 시도했을 때 화면이 안내할 수 있는 오류. 읽기와 로컬 삭제는 허용한다. */
class ArchivedRecordException : IllegalStateException("보관한 기록은 읽거나 지울 수 있어요")
