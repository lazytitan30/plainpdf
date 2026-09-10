package com.leaf.app.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

@Serializable
data object ToolsRoute

@Serializable
data object SettingsRoute

@Serializable
data object SupporterRoute

@Serializable
data class ReaderRoute(val uri: String)

@Serializable
data class FolderRoute(val folderId: Long)

@Serializable
data class OrganiseRoute(val uri: String)

@Serializable
data class MergeRoute(val uris: List<String> = emptyList())

@Serializable
data class SplitRoute(val uri: String? = null)

@Serializable
data class ImagesToPdfRoute(val uris: List<String> = emptyList())

@Serializable
data class PdfToImagesRoute(val uri: String? = null)

@Serializable
data class PasswordRoute(val uri: String? = null)

@Serializable
data class SignRoute(val uri: String)

@Serializable
data class CompressRoute(val uri: String? = null)

@Serializable
data class PageNumbersRoute(val uri: String? = null)

@Serializable
data class MakeSearchableRoute(val uri: String? = null)

@Serializable
data class RedactRoute(val uri: String)

@Serializable
data object NoteRoute

@Serializable
data object OcrLanguagesRoute
