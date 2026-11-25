package br.com.tveseriesonline

data class Channel(
    val streamUrl: String,
    val name: String,
    val groupName: String = "Sem categoria",
    val logoUrl: String? = null,
    val tvgId: String? = null,
    val isFavorite: Boolean = false
)
