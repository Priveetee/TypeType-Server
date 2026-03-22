package dev.typetype.server.services

data class YoutubeTakeoutZipScanResult(
    val subscriptionsRows: List<List<String>>,
    val subscriptionsHeader: List<String>,
    val playlistsRows: List<List<String>>,
    val playlistsHeader: List<String>,
    val playlistItemsRows: List<List<String>>,
    val playlistItemsHeader: List<String>,
    val warnings: List<String>,
)
