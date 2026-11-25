package com.example.rickandmortyexplorer.network

import com.example.rickandmortyexplorer.Character
import kotlinx.serialization.Serializable

@Serializable
data class CharactersResponseDto(
    val info: PageInfoDto? = null,
    val results: List<CharacterDto>? = null
)

@Serializable
data class PageInfoDto(
    val count: Int? = null,
    val pages: Int? = null,
    val next: String? = null,
    val prev: String? = null
)

@Serializable
data class CharacterDto(
    val id: Int = 0,
    val name: String? = null,
    val status: String? = null,
    val species: String? = null,
    val type: String? = null,
    val gender: String? = null,
    val origin: LocationDto? = null,
    val location: LocationDto? = null,
    val image: String? = null,
    val episode: List<String>? = null
)

@Serializable
data class LocationDto(
    val name: String? = null,
    val url: String? = null
)

@Serializable
data class EpisodeDto(
    val id: Int = 0,
    val name: String? = null,
    val episode: String? = null
) {
    fun toDisplayTitle(): String {
        val code = episode?.trim().orEmpty()
        val title = name?.trim().orEmpty()
        return when {
            code.isNotEmpty() && title.isNotEmpty() -> "$code · $title"
            title.isNotEmpty() -> title
            code.isNotEmpty() -> code
            else -> ""
        }
    }
}

private const val API_HOST = "https://rickandmortyapi.com"
private val HTTP_SCHEME_REGEX = Regex("^http://", RegexOption.IGNORE_CASE)

fun normalizeImageUrl(rawUrl: String?): String {
    val trimmed = rawUrl?.trim().orEmpty()
    if (trimmed.isEmpty()) return ""
    val normalized = when {
        trimmed.startsWith("//") -> "https:$trimmed"
        HTTP_SCHEME_REGEX.containsMatchIn(trimmed) -> trimmed.replaceFirst(HTTP_SCHEME_REGEX, "https://")
        trimmed.startsWith("/") -> "$API_HOST$trimmed"
        else -> trimmed
    }
    return if (normalized.startsWith("https://", ignoreCase = true)) normalized else ""
}

fun extractEpisodeId(episodeUrl: String?): Int? {
    val trimmed = episodeUrl?.trim()?.trimEnd('/') ?: return null
    return trimmed.substringAfterLast('/').toIntOrNull()
}

fun CharacterDto.toCharacter(): Character {
    val cleanSpecies = species?.takeIf { it.isNotBlank() } ?: "Невідомий вид"
    val cleanType = type?.takeIf { it.isNotBlank() }
    val displaySpecies = if (cleanType != null && !cleanSpecies.equals(cleanType, ignoreCase = true)) {
        "$cleanSpecies ($cleanType)"
    } else {
        cleanSpecies
    }

    return Character(
        id = id,
        name = name?.takeIf { it.isNotBlank() } ?: "Невідомий персонаж",
        status = status?.takeIf { it.isNotBlank() } ?: "unknown",
        species = displaySpecies,
        gender = gender?.takeIf { it.isNotBlank() } ?: "unknown",
        origin = origin?.name?.takeIf { it.isNotBlank() } ?: "Невідомо",
        location = location?.name?.takeIf { it.isNotBlank() } ?: "Невідомо",
        episodeCount = episode?.size ?: 0,
        imageUrl = normalizeImageUrl(image),
        firstEpisodeId = extractEpisodeId(episode?.firstOrNull())
    )
}
