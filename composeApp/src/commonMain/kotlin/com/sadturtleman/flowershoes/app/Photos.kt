package com.sadturtleman.flowershoes.app

import androidx.compose.runtime.Composable

/** 그림 배경 사진의 긴 변 최대 픽셀. 위젯 · 리플레이에 충분하고 blob 이 수백 KB 로 머문다. */
internal const val PHOTO_MAX_EDGE = 1600

/**
 * 앨범 · 카메라에서 사진 한 장(그림 / 사진). 고른 사진은 긴 변 [PHOTO_MAX_EDGE] 로 줄인 JPEG 로 [onPicked] 에 온다.
 * 돌려받은 함수를 `true` 로 부르면 카메라, `false` 면 앨범.
 */
@Composable
internal expect fun rememberPhotoPicker(onPicked: (ByteArray) -> Unit): (fromCamera: Boolean) -> Unit

/** 완성 PNG 를 사진첩에 저장(그림 보내기 › 저장). 성공하면 true. */
@Composable
internal expect fun rememberImageSaver(): suspend (png: ByteArray) -> Boolean
