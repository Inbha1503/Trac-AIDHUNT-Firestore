package com.example.data.sync

data class SyncResult(
    val isSuccess: Boolean,
    val syncedItemsCount: Int,
    val message: String
)
