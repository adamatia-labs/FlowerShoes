package com.sadturtleman.flowershoes.core.firebase

import dev.gitlive.firebase.storage.StorageReference

/** 암호문만 Storage에 둔다. GitLive 2.1의 바이트 다운로드는 플랫폼 SDK로 잇는다. */
internal expect suspend fun putStorageBytes(ref: StorageReference, bytes: ByteArray)
internal expect suspend fun getStorageBytes(ref: StorageReference): ByteArray
