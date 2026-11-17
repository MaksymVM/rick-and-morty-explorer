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
        imageUrl = image?.takeIf { it.isNotBlank() } ?: ""
    )
}
