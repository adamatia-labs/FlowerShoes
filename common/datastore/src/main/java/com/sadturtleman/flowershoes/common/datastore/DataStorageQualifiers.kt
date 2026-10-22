package com.sadturtleman.flowershoes.common.datastore

import javax.inject.Qualifier

/**
 * 사용자 데이터 저장 파일 한정자.
 *
 * 피처가 자기 키를 선언해 이 파일에 담는다. 피처마다 수명이 갈리면 파일과 한정자를 한 쌍씩 더한다.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UserPreferences

/**
 * 기기 정보 저장 파일 한정자.
 *
 * 사용자 데이터와 파일을 나누는 이유는 수명이 다르기 때문이다 — 설치 ID 는 앱이 깔려 있는 동안
 * 한 번만 만들어져 끝까지 같아야 하고, 사용자 데이터를 비우는 자리에서 함께 지워지면 안 된다.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DevicePreferences
