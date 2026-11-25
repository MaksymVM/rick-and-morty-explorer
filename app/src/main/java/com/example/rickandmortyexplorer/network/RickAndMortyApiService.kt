package com.example.rickandmortyexplorer.network

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException
import java.net.SocketTimeoutException

interface RickAndMortyApiService {
    @GET("character")
    suspend fun getCharacters(
        @Query("status") status: String? = null,
        @Query("name") name: String? = null
    ): CharactersResponseDto

    @GET("character/{id}")
    suspend fun getCharacterById(
        @Path("id") id: Int
    ): CharacterDto

    @GET("episode/{id}")
    suspend fun getEpisodeById(
        @Path("id") id: Int
    ): EpisodeDto
}

object RetrofitClient {
    private const val BASE_URL = "https://rickandmortyapi.com/api/"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    val api: RickAndMortyApiService by lazy {
        retrofit.create(RickAndMortyApiService::class.java)
    }
}

/**
 * Rick and Morty API повертає HTTP 404 {"error": "There is nothing here"},
 * коли за фільтром/пошуком не знайдено жодного персонажа.
 * Це порожній результат запиту, а не збій сервера.
 */
fun Throwable.isEmptyApiResult(): Boolean {
    if (this is HttpException && code() == 404) {
        val errorBody = runCatching { response()?.errorBody()?.string().orEmpty() }.getOrDefault("")
        return errorBody.contains("There is nothing here", ignoreCase = true)
    }
    return false
}

/**
 * Перетворює технічні винятки мережі/сервера/серіалізації у зрозумілий користувачеві текст.
 */
fun Throwable.toUserErrorMessage(): String = when (this) {
    is SerializationException -> {
        "Сервер повернув дані у неочікуваному форматі. Спробуйте оновити пізніше."
    }
    is SocketTimeoutException -> {
        "Час очікування відповіді від сервера вичерпано. Перевірте інтернет і спробуйте ще раз."
    }
    is IOException -> {
        "Немає підключення до інтернету. Вимкніть авіарежим або увімкніть Wi-Fi чи мобільні дані."
    }
    is HttpException -> when (code()) {
        429 -> "Перевищено ліміт запитів до сервера. Зачекайте хвилину та спробуйте знову."
        in 500..599 -> "Сервер тимчасово недоступний (помилка ${code()}). Спробуйте трохи пізніше."
        else -> "Сервер повернув помилку (код ${code()}). Спробуйте повторити запит."
    }
    else -> {
        "Не вдалося завантажити дані. Перевірте з'єднання та спробуйте ще раз."
    }
}
