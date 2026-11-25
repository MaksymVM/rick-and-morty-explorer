package com.example.rickandmortyexplorer

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import coil.Coil
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.rickandmortyexplorer.network.RetrofitClient
import com.example.rickandmortyexplorer.network.isEmptyApiResult
import com.example.rickandmortyexplorer.network.toCharacter
import com.example.rickandmortyexplorer.network.toUserErrorMessage
import com.example.rickandmortyexplorer.ui.theme.LocalPortalColors
import com.example.rickandmortyexplorer.ui.theme.RickAndMortyExplorerTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import retrofit2.HttpException

data class Character(
    val id: Int,
    val name: String,
    val status: String,
    val species: String,
    val gender: String,
    val origin: String,
    val location: String,
    val episodeCount: Int,
    val imageUrl: String = "",
    val firstEpisodeId: Int? = null,
    val firstEpisode: String = ""
)

data class CharactersUiState(
    val characters: List<Character> = emptyList(),
    val categories: List<String> = listOf("Всі", "Alive", "Dead", "unknown"),
    val selectedStatus: String = "Всі",
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val totalCount: Int = 0,
    val hasNextPage: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loadMoreErrorMessage: String? = null,
    val imageReloadGeneration: Int = 0,
    val errorMessage: String? = null,
    val refreshErrorMessage: String? = null,
    val selectedCharacter: Character? = null,
    val isDetailLoading: Boolean = false,
    val detailErrorMessage: String? = null,
    val favoriteCharacters: List<Character> = emptyList(),
    val isFavoritesLoading: Boolean = false,
    val favoritesErrorMessage: String? = null
)

private data class CachedCatalogPageState(
    val characters: List<Character>,
    val currentPage: Int,
    val totalPages: Int,
    val totalCount: Int,
    val hasNextPage: Boolean
)

class CharactersViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsManager = SettingsManager(application)

    private val _uiState = MutableStateFlow(CharactersUiState())
    val uiState: StateFlow<CharactersUiState> = _uiState.asStateFlow()

    private var fetchJob: Job? = null
    private var loadMoreJob: Job? = null
    private var favoritesJob: Job? = null
    private val episodeCache = mutableMapOf<Int, String>()
    private val catalogCache = mutableMapOf<String, CachedCatalogPageState>()

    val showExtendedDetails: StateFlow<Boolean> = settingsManager.showExtendedDetailsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    val darkThemePreference: StateFlow<Boolean?> = settingsManager.darkThemeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    val favoriteIds: StateFlow<Set<Int>> = settingsManager.favoriteIdsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptySet()
        )

    init {
        loadCharacters(
            status = _uiState.value.selectedStatus,
            query = _uiState.value.searchQuery
        )
    }

    fun onStatusSelected(newStatus: String) {
        val current = _uiState.value
        if (newStatus == current.selectedStatus &&
            current.characters.isNotEmpty() &&
            current.errorMessage == null
        ) {
            return
        }
        _uiState.update { it.copy(selectedStatus = newStatus) }
        loadCharacters(status = newStatus, query = _uiState.value.searchQuery)
    }

    fun onSearchQueryChanged(newQuery: String) {
        val current = _uiState.value
        val previousTrimmed = current.searchQuery.trim()
        val newTrimmed = newQuery.trim()

        _uiState.update { it.copy(searchQuery = newQuery) }

        if (newTrimmed.isEmpty()) {
            restoreCatalogForCurrentStatus(current.selectedStatus)
            return
        }

        if (newTrimmed == previousTrimmed && current.errorMessage == null) {
            return
        }

        loadCharacters(
            status = current.selectedStatus,
            query = newTrimmed,
            debounceMs = 350L
        )
    }

    fun clearSearch() {
        val current = _uiState.value
        if (current.searchQuery.isEmpty() && current.errorMessage == null) return
        _uiState.update { it.copy(searchQuery = "") }
        restoreCatalogForCurrentStatus(current.selectedStatus)
    }

    private fun restoreCatalogForCurrentStatus(status: String) {
        fetchJob?.cancel()
        loadMoreJob?.cancel()
        val cached = catalogCache[status]
        if (cached != null) {
            _uiState.update {
                it.copy(
                    characters = cached.characters,
                    currentPage = cached.currentPage,
                    totalPages = cached.totalPages,
                    totalCount = cached.totalCount,
                    hasNextPage = cached.hasNextPage,
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    errorMessage = null,
                    refreshErrorMessage = null,
                    loadMoreErrorMessage = null
                )
            }
        } else {
            loadCharacters(status = status, query = "")
        }
    }

    fun retry() {
        val current = _uiState.value
        loadCharacters(status = current.selectedStatus, query = current.searchQuery)
    }

    fun refresh() {
        val current = _uiState.value
        if (current.isRefreshing || current.isLoading) return

        if (current.characters.isEmpty()) {
            loadCharacters(
                status = current.selectedStatus,
                query = current.searchQuery,
                isPullToRefresh = false
            )
            return
        }

        val targetStatus = current.selectedStatus
        val targetQuery = current.searchQuery
        val pagesToRefresh = current.currentPage.coerceAtLeast(1)

        fetchJob?.cancel()
        loadMoreJob?.cancel()
        fetchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRefreshing = true,
                    isLoadingMore = false,
                    refreshErrorMessage = null,
                    loadMoreErrorMessage = null,
                    imageReloadGeneration = it.imageReloadGeneration + 1
                )
            }
            try {
                val queryStatus = if (targetStatus == "Всі") null else targetStatus.lowercase()
                val queryName = targetQuery.trim().takeIf { it.isNotEmpty() }

                val accumulated = mutableListOf<Character>()
                var lastPage = 1
                var totalPages = current.totalPages
                var totalCount = current.totalCount
                var hasNext = false

                for (page in 1..pagesToRefresh) {
                    val response = withRateLimitRetry {
                        RetrofitClient.api.getCharacters(
                            page = page,
                            status = queryStatus,
                            name = queryName
                        )
                    }
                    accumulated += response.results.orEmpty().map { it.toCharacter() }
                    lastPage = page
                    totalPages = response.info?.pages ?: totalPages
                    totalCount = response.info?.count ?: accumulated.size
                    hasNext = !response.info?.next.isNullOrBlank()
                    if (!hasNext) break
                }

                val latestState = _uiState.value
                if (latestState.selectedStatus != targetStatus || latestState.searchQuery != targetQuery) {
                    return@launch
                }

                val distinctCharacters = accumulated.distinctBy { it.id }
                val enrichedCharacters = enrichWithFirstEpisodes(
                    characters = distinctCharacters,
                    forceRefresh = true
                )
                if (queryName == null) {
                    catalogCache[targetStatus] = CachedCatalogPageState(
                        characters = enrichedCharacters,
                        currentPage = lastPage,
                        totalPages = totalPages,
                        totalCount = totalCount,
                        hasNextPage = hasNext
                    )
                }
                _uiState.update {
                    it.copy(
                        characters = enrichedCharacters,
                        currentPage = lastPage,
                        totalPages = totalPages,
                        totalCount = totalCount,
                        hasNextPage = hasNext,
                        isRefreshing = false,
                        isLoadingMore = false,
                        refreshErrorMessage = null,
                        loadMoreErrorMessage = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        isLoadingMore = false,
                        refreshErrorMessage = e.toUserErrorMessage()
                    )
                }
            }
        }
    }

    fun consumeRefreshError() {
        _uiState.update { it.copy(refreshErrorMessage = null) }
    }

    fun resetFilters() {
        _uiState.update { it.copy(selectedStatus = "Всі", searchQuery = "") }
        loadCharacters(status = "Всі", query = "")
    }

    private fun loadCharacters(
        status: String,
        query: String,
        debounceMs: Long = 0L,
        isPullToRefresh: Boolean = false
    ) {
        fetchJob?.cancel()
        loadMoreJob?.cancel()
        fetchJob = viewModelScope.launch {
            if (debounceMs > 0L) {
                delay(debounceMs)
            }
            if (isPullToRefresh) {
                _uiState.update {
                    it.copy(
                        isRefreshing = true,
                        isLoadingMore = false,
                        refreshErrorMessage = null,
                        loadMoreErrorMessage = null
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        isLoadingMore = false,
                        errorMessage = null,
                        refreshErrorMessage = null,
                        loadMoreErrorMessage = null
                    )
                }
            }
            try {
                val queryStatus = if (status == "Всі") null else status.lowercase()
                val queryName = query.trim().takeIf { it.isNotEmpty() }
                val response = withRateLimitRetry {
                    RetrofitClient.api.getCharacters(
                        page = 1,
                        status = queryStatus,
                        name = queryName
                    )
                }
                val loadedCharacters = response.results.orEmpty()
                    .map { it.toCharacter() }
                    .distinctBy { it.id }
                val enrichedCharacters = enrichWithFirstEpisodes(
                    characters = loadedCharacters,
                    forceRefresh = isPullToRefresh
                )
                val totalPages = response.info?.pages ?: 1
                val totalCount = response.info?.count ?: enrichedCharacters.size
                val hasNext = !response.info?.next.isNullOrBlank()
                if (queryName == null) {
                    catalogCache[status] = CachedCatalogPageState(
                        characters = enrichedCharacters,
                        currentPage = 1,
                        totalPages = totalPages,
                        totalCount = totalCount,
                        hasNextPage = hasNext
                    )
                }
                _uiState.update {
                    it.copy(
                        characters = enrichedCharacters,
                        currentPage = 1,
                        totalPages = totalPages,
                        totalCount = totalCount,
                        hasNextPage = hasNext,
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        errorMessage = null,
                        refreshErrorMessage = null,
                        loadMoreErrorMessage = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e.isEmptyApiResult()) {
                    _uiState.update {
                        it.copy(
                            characters = emptyList(),
                            currentPage = 0,
                            totalPages = 0,
                            totalCount = 0,
                            hasNextPage = false,
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            errorMessage = null,
                            refreshErrorMessage = null,
                            loadMoreErrorMessage = null
                        )
                    }
                } else if (isPullToRefresh && _uiState.value.characters.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            isLoadingMore = false,
                            refreshErrorMessage = e.toUserErrorMessage()
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            characters = emptyList(),
                            currentPage = 0,
                            totalPages = 0,
                            totalCount = 0,
                            hasNextPage = false,
                            isLoading = false,
                            isRefreshing = false,
                            isLoadingMore = false,
                            errorMessage = e.toUserErrorMessage(),
                            loadMoreErrorMessage = null
                        )
                    }
                }
            }
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoading || state.isRefreshing || state.isLoadingMore || !state.hasNextPage) return

        val targetStatus = state.selectedStatus
        val targetQuery = state.searchQuery.trim()
        val nextPage = state.currentPage + 1

        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, loadMoreErrorMessage = null) }
            try {
                val queryStatus = if (targetStatus == "Всі") null else targetStatus.lowercase()
                val queryName = targetQuery.takeIf { it.isNotEmpty() }
                val response = withRateLimitRetry {
                    RetrofitClient.api.getCharacters(
                        page = nextPage,
                        status = queryStatus,
                        name = queryName
                    )
                }
                val loadedPage = response.results.orEmpty().map { it.toCharacter() }
                val enrichedPage = enrichWithFirstEpisodes(loadedPage)

                val current = _uiState.value
                if (current.selectedStatus != targetStatus || current.searchQuery.trim() != targetQuery) {
                    return@launch
                }

                val merged = (current.characters + enrichedPage).distinctBy { it.id }
                val hasNext = !response.info?.next.isNullOrBlank()
                val totalPages = response.info?.pages ?: current.totalPages
                val totalCount = response.info?.count ?: merged.size

                if (queryName == null) {
                    catalogCache[targetStatus] = CachedCatalogPageState(
                        characters = merged,
                        currentPage = nextPage,
                        totalPages = totalPages,
                        totalCount = totalCount,
                        hasNextPage = hasNext
                    )
                }
                _uiState.update {
                    it.copy(
                        characters = merged,
                        currentPage = nextPage,
                        totalPages = totalPages,
                        totalCount = totalCount,
                        hasNextPage = hasNext,
                        isLoadingMore = false,
                        loadMoreErrorMessage = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e.isEmptyApiResult()) {
                    _uiState.update {
                        it.copy(
                            hasNextPage = false,
                            isLoadingMore = false,
                            loadMoreErrorMessage = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            loadMoreErrorMessage = e.toUserErrorMessage()
                        )
                    }
                }
            }
        }
    }

    private suspend fun <T> withRateLimitRetry(
        maxRetries: Int = 2,
        block: suspend () -> T
    ): T {
        var attempt = 0
        var backoffMs = 1200L
        while (true) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                if (e.code() == 429 && attempt < maxRetries) {
                    val retryAfterSeconds = e.response()?.headers()?.get("Retry-After")?.toLongOrNull()
                    val waitMs = if (retryAfterSeconds != null && retryAfterSeconds in 1L..5L) {
                        retryAfterSeconds * 1000L
                    } else {
                        backoffMs
                    }
                    delay(waitMs)
                    backoffMs *= 2
                    attempt++
                } else {
                    throw e
                }
            }
        }
    }

    private suspend fun enrichWithFirstEpisodes(
        characters: List<Character>,
        forceRefresh: Boolean = false
    ): List<Character> {
        val uniqueEpisodeIds = characters.mapNotNull { it.firstEpisodeId }.distinct()
        val idsToFetch = if (forceRefresh) {
            uniqueEpisodeIds
        } else {
            uniqueEpisodeIds.filterNot { episodeCache.containsKey(it) }
        }

        if (idsToFetch.isNotEmpty()) {
            idsToFetch.chunked(50).forEach { chunk ->
                try {
                    val episodes = if (chunk.size == 1) {
                        listOf(RetrofitClient.api.getEpisodeById(chunk.first()))
                    } else {
                        RetrofitClient.api.getEpisodesByIds(chunk.joinToString(","))
                    }
                    episodes.forEach { episode ->
                        val title = episode.toDisplayTitle()
                        if (title.isNotBlank()) {
                            episodeCache[episode.id] = title
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Ігноруємо часткові помилки завантаження епізодів, щоб не блокувати показ персонажів
                }
            }
        }

        return characters.map { character ->
            val episodeTitle = character.firstEpisodeId?.let { episodeCache[it] }.orEmpty()
            if (episodeTitle.isNotBlank()) character.copy(firstEpisode = episodeTitle) else character
        }
    }

    fun loadCharacterById(id: Int) {
        val existing = _uiState.value.characters.find { it.id == id }
            ?: _uiState.value.favoriteCharacters.find { it.id == id }
        if (existing != null) {
            _uiState.update {
                it.copy(
                    selectedCharacter = existing,
                    isDetailLoading = false,
                    detailErrorMessage = null
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedCharacter = null,
                    isDetailLoading = true,
                    detailErrorMessage = null
                )
            }
            try {
                val dto = RetrofitClient.api.getCharacterById(id)
                val enriched = enrichWithFirstEpisodes(listOf(dto.toCharacter())).first()
                _uiState.update {
                    it.copy(
                        selectedCharacter = enriched,
                        isDetailLoading = false,
                        detailErrorMessage = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e.isEmptyApiResult()) {
                    _uiState.update {
                        it.copy(
                            selectedCharacter = null,
                            isDetailLoading = false,
                            detailErrorMessage = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            selectedCharacter = null,
                            isDetailLoading = false,
                            detailErrorMessage = e.toUserErrorMessage()
                        )
                    }
                }
            }
        }
    }

    fun toggleExtendedDetails(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setExtendedDetails(enabled)
        }
    }

    fun toggleDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setDarkTheme(enabled)
        }
    }

    fun toggleFavorite(characterId: Int) {
        val wasFavorite = characterId in favoriteIds.value
        viewModelScope.launch {
            settingsManager.toggleFavorite(characterId)
            if (wasFavorite) {
                _uiState.update { state ->
                    state.copy(
                        favoriteCharacters = state.favoriteCharacters.filterNot { it.id == characterId }
                    )
                }
            }
        }
    }

    fun loadFavorites() {
        favoritesJob?.cancel()
        favoritesJob = viewModelScope.launch {
            _uiState.update { it.copy(isFavoritesLoading = true, favoritesErrorMessage = null) }
            try {
                val savedIds = settingsManager.favoriteIdsFlow.first().sorted()
                if (savedIds.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            favoriteCharacters = emptyList(),
                            isFavoritesLoading = false,
                            favoritesErrorMessage = null
                        )
                    }
                    return@launch
                }
                val loadedDtos = savedIds.chunked(50).flatMap { chunk ->
                    if (chunk.size == 1) {
                        listOf(RetrofitClient.api.getCharacterById(chunk.first()))
                    } else {
                        RetrofitClient.api.getCharactersByIds(chunk.joinToString(","))
                    }
                }
                val loadedCharacters = loadedDtos
                    .map { it.toCharacter() }
                    .distinctBy { it.id }
                val enriched = enrichWithFirstEpisodes(loadedCharacters)
                _uiState.update {
                    it.copy(
                        favoriteCharacters = enriched,
                        isFavoritesLoading = false,
                        favoritesErrorMessage = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e.isEmptyApiResult()) {
                    _uiState.update {
                        it.copy(
                            favoriteCharacters = emptyList(),
                            isFavoritesLoading = false,
                            favoritesErrorMessage = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isFavoritesLoading = false,
                            favoritesErrorMessage = e.toUserErrorMessage()
                        )
                    }
                }
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    companion object {
        @Volatile
        private var isImageLoaderConfigured = false

        private fun configureCoilImageLoader(context: Context) {
            if (isImageLoaderConfigured) return
            synchronized(this) {
                if (isImageLoaderConfigured) return
                val dispatcher = Dispatcher().apply {
                    maxRequestsPerHost = 2
                    maxRequests = 6
                }
                val okHttpClient = OkHttpClient.Builder()
                    .dispatcher(dispatcher)
                    .addInterceptor { chain ->
                        val request = chain.request()
                        var response = chain.proceed(request)
                        var attempt = 0
                        var backoffMs = 800L
                        while ((response.code == 429 || response.code == 503) && attempt < 3) {
                            val retryAfterSeconds = response.header("Retry-After")?.toLongOrNull()
                            val waitMs = if (retryAfterSeconds != null && retryAfterSeconds in 1L..5L) {
                                retryAfterSeconds * 1000L
                            } else {
                                backoffMs
                            }
                            response.close()
                            Thread.sleep(waitMs)
                            backoffMs *= 2
                            attempt++
                            response = chain.proceed(request)
                        }
                        response
                    }
                    .build()

                Coil.setImageLoader(
                    ImageLoader.Builder(context)
                        .okHttpClient(okHttpClient)
                        .crossfade(true)
                        .build()
                )
                isImageLoaderConfigured = true
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        configureCoilImageLoader(applicationContext)
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: CharactersViewModel = viewModel(
                factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            )
            val darkThemePref by viewModel.darkThemePreference.collectAsState()
            val isDarkTheme = darkThemePref ?: isSystemInDarkTheme()
            RickAndMortyExplorerTheme(darkTheme = isDarkTheme) {
                AppNavigation(
                    viewModel = viewModel,
                    isDarkTheme = isDarkTheme
                )
            }
        }
    }
}

@Composable
fun AppNavigation(
    viewModel: CharactersViewModel,
    isDarkTheme: Boolean
) {
    val navController = rememberNavController()
    val favoriteIds by viewModel.favoriteIds.collectAsState()

    val navigateToCatalog: () -> Unit = {
        navController.navigate("catalog") {
            popUpTo("catalog") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val navigateToFavorites: () -> Unit = {
        navController.navigate("favorites") {
            popUpTo("catalog") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = "catalog"
    ) {
        composable("catalog") {
            CharactersCatalogScreen(
                viewModel = viewModel,
                onCharacterClick = { characterId ->
                    navController.navigate("detail/$characterId")
                },
                onSettingsClick = {
                    navController.navigate("settings")
                },
                bottomBar = {
                    CatalogBottomBar(
                        currentRoute = "catalog",
                        favoritesCount = favoriteIds.size,
                        onCatalogClick = navigateToCatalog,
                        onFavoritesClick = navigateToFavorites
                    )
                }
            )
        }

        composable("favorites") {
            FavoritesScreen(
                viewModel = viewModel,
                onCharacterClick = { characterId ->
                    navController.navigate("detail/$characterId")
                },
                onExploreClick = navigateToCatalog,
                bottomBar = {
                    CatalogBottomBar(
                        currentRoute = "favorites",
                        favoritesCount = favoriteIds.size,
                        onCatalogClick = navigateToCatalog,
                        onFavoritesClick = navigateToFavorites
                    )
                }
            )
        }

        composable(
            route = "detail/{characterId}",
            arguments = listOf(
                navArgument("characterId") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val characterId = backStackEntry.arguments?.getInt("characterId") ?: -1
            val uiState by viewModel.uiState.collectAsState()
            val character = uiState.characters.find { it.id == characterId }
                ?: uiState.favoriteCharacters.find { it.id == characterId }
                ?: uiState.selectedCharacter?.takeIf { it.id == characterId }

            LaunchedEffect(characterId) {
                if (character == null && characterId != -1) {
                    viewModel.loadCharacterById(characterId)
                }
            }

            CharacterDetailScreen(
                character = character,
                isFavorite = characterId in favoriteIds,
                onFavoriteClick = {
                    if (characterId != -1) {
                        viewModel.toggleFavorite(characterId)
                    }
                },
                isLoading = uiState.isDetailLoading && character == null,
                errorMessage = uiState.detailErrorMessage,
                onRetry = {
                    if (characterId != -1) {
                        viewModel.loadCharacterById(characterId)
                    }
                },
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                isDarkTheme = isDarkTheme,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun PortalScreenContainer(
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            PortalSpaceBackground(modifier = Modifier.fillMaxSize())
            content()
        }
    }
}

@Composable
fun PortalSpaceBackground(modifier: Modifier = Modifier) {
    val isDark = LocalPortalColors.current.isDark
    Image(
        painter = painterResource(
            id = if (isDark) R.drawable.bg_portal_space_dark else R.drawable.bg_portal_space_light
        ),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

@Composable
fun CatalogBottomBar(
    currentRoute: String,
    favoritesCount: Int,
    onCatalogClick: () -> Unit,
    onFavoritesClick: () -> Unit
) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            val isCatalogSelected = currentRoute == "catalog"
            val isFavoritesSelected = currentRoute == "favorites"
            val itemColors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            NavigationBarItem(
                selected = isCatalogSelected,
                onClick = onCatalogClick,
                colors = itemColors,
                icon = {
                    Icon(
                        imageVector = if (isCatalogSelected) Icons.Filled.Home else Icons.Outlined.Home,
                        contentDescription = "Каталог"
                    )
                },
                label = { Text("Каталог") }
            )

            NavigationBarItem(
                selected = isFavoritesSelected,
                onClick = onFavoritesClick,
                colors = itemColors,
                icon = {
                    Icon(
                        imageVector = if (isFavoritesSelected) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Обране"
                    )
                },
                label = {
                    Text(
                        if (favoritesCount > 0) "Обране ($favoritesCount)" else "Обране"
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharactersCatalogScreen(
    viewModel: CharactersViewModel,
    onCharacterClick: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val showExtended by viewModel.showExtendedDetails.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.refreshErrorMessage) {
        val message = uiState.refreshErrorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeRefreshError()
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleIndex >= totalItems - 4
        }
    }

    LaunchedEffect(shouldLoadMore, uiState.hasNextPage, uiState.isLoadingMore, uiState.loadMoreErrorMessage) {
        if (shouldLoadMore && uiState.hasNextPage && !uiState.isLoadingMore && uiState.loadMoreErrorMessage == null) {
            viewModel.loadNextPage()
        }
    }

    PortalScreenContainer {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = bottomBar,
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_portal_logo),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Text(
                                    text = "Rick & Morty",
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { viewModel.refresh() },
                                enabled = !uiState.isRefreshing && !uiState.isLoading
                            ) {
                                if (uiState.isRefreshing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.Refresh,
                                        contentDescription = "Оновити список"
                                    )
                                }
                            }
                            IconButton(onClick = onSettingsClick) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Налаштування"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = {
                        Text(
                            text = "Пошук персонажа за ім'ям...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearSearch() }) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Очистити пошук"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                        unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                StatusFilterRow(
                    categories = uiState.categories,
                    selectedCategory = uiState.selectedStatus,
                    onCategorySelected = { newStatus ->
                        viewModel.onStatusSelected(newStatus)
                    }
                )

                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    uiState.errorMessage != null -> {
                        ErrorStateView(
                            message = uiState.errorMessage!!,
                            onRetry = { viewModel.retry() }
                        )
                    }
                    uiState.characters.isEmpty() && uiState.searchQuery.isNotBlank() -> {
                        EmptySearchStateView(
                            query = uiState.searchQuery.trim(),
                            status = uiState.selectedStatus,
                            onClearSearch = { viewModel.clearSearch() }
                        )
                    }
                    uiState.characters.isEmpty() -> {
                        EmptyStateView(
                            status = uiState.selectedStatus,
                            searchQuery = uiState.searchQuery,
                            onResetFilters = { viewModel.resetFilters() }
                        )
                    }
                    else -> {
                        PullToRefreshBox(
                            isRefreshing = uiState.isRefreshing,
                            onRefresh = { viewModel.refresh() },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(
                                    items = uiState.characters,
                                    key = { it.id },
                                    contentType = { "character" }
                                ) { character ->
                                    CharacterCard(
                                        character = character,
                                        showExtended = showExtended,
                                        isFavorite = character.id in favoriteIds,
                                        imageReloadTrigger = uiState.imageReloadGeneration,
                                        onFavoriteClick = { viewModel.toggleFavorite(character.id) },
                                        onClick = { onCharacterClick(character.id) }
                                    )
                                }

                                item(key = "pagination_footer", contentType = "footer") {
                                    when {
                                        uiState.isLoadingMore -> {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 12.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = "Завантаження... (${uiState.characters.size} / ${uiState.totalCount})",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        uiState.loadMoreErrorMessage != null -> {
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 6.dp),
                                                shape = RoundedCornerShape(14.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(14.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text(
                                                        text = uiState.loadMoreErrorMessage!!,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        textAlign = TextAlign.Center
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    OutlinedButton(
                                                        onClick = { viewModel.loadNextPage() },
                                                        shape = RoundedCornerShape(20.dp),
                                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                                    ) {
                                                        Text("Спробувати ще")
                                                    }
                                                }
                                            }
                                        }
                                        !uiState.hasNextPage && uiState.totalCount > 0 -> {
                                            Text(
                                                text = "Показано всіх: ${uiState.characters.size} із ${uiState.totalCount}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 12.dp)
                                            )
                                        }
                                        uiState.hasNextPage -> {
                                            Text(
                                                text = "Завантажено ${uiState.characters.size} із ${uiState.totalCount}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: CharactersViewModel,
    onCharacterClick: (Int) -> Unit,
    onExploreClick: () -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val showExtended by viewModel.showExtendedDetails.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadFavorites()
    }

    PortalScreenContainer {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            bottomBar = bottomBar,
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Обране",
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when {
                    uiState.isFavoritesLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    uiState.favoritesErrorMessage != null -> {
                        ErrorStateView(
                            message = uiState.favoritesErrorMessage!!,
                            onRetry = { viewModel.loadFavorites() }
                        )
                    }
                    uiState.favoriteCharacters.isEmpty() -> {
                        EmptyFavoritesStateView(onExploreClick = onExploreClick)
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(
                                items = uiState.favoriteCharacters,
                                key = { it.id },
                                contentType = { "character" }
                            ) { character ->
                                CharacterCard(
                                    character = character,
                                    showExtended = showExtended,
                                    isFavorite = character.id in favoriteIds,
                                    onFavoriteClick = { viewModel.toggleFavorite(character.id) },
                                    onClick = { onCharacterClick(character.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyFavoritesStateView(
    onExploreClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Добірка порожня",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Натисніть на сердечко в каталозі або на екрані персонажа, щоб зберегти його сюди.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onExploreClick,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Text("Перейти до каталогу")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: CharactersViewModel,
    isDarkTheme: Boolean,
    onBackClick: () -> Unit
) {
    val showExtended by viewModel.showExtendedDetails.collectAsState()

    PortalScreenContainer {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Налаштування",
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Назад"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Розширений показ у списку",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Показувати рідну планету та кількість серій прямо на картці списку",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Switch(
                            checked = showExtended,
                            onCheckedChange = { isChecked ->
                                viewModel.toggleExtendedDetails(isChecked)
                            }
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DarkMode,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Темна тема",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isDarkTheme) {
                                    "Зараз активна темна тема"
                                } else {
                                    "Зараз активна світла тема"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Switch(
                            checked = isDarkTheme,
                            onCheckedChange = { isChecked ->
                                viewModel.toggleDarkTheme(isChecked)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CharacterCard(
    character: Character,
    showExtended: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    imageReloadTrigger: Int = 0,
    onFavoriteClick: () -> Unit = {}
) {
    val portalColors = LocalPortalColors.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (showExtended) 104.dp else 92.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                CharacterImage(
                    imageUrl = character.imageUrl,
                    contentDescription = character.name,
                    placeholderIconSize = 42.dp,
                    reloadTrigger = imageReloadTrigger
                )

                val badgeColor = when (character.status) {
                    "Alive" -> portalColors.statusAlive
                    "Dead" -> portalColors.statusDead
                    else -> portalColors.statusUnknown
                }

                Surface(
                    shape = RoundedCornerShape(topStart = 8.dp),
                    color = badgeColor,
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Text(
                        text = character.status,
                        color = portalColors.onStatusBadge,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = character.name,
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(3.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val dotColor = when (character.status) {
                        "Alive" -> portalColors.statusAliveDot
                        "Dead" -> portalColors.statusDeadDot
                        else -> portalColors.statusUnknown
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${character.status} - ${character.species}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Стать: ${character.gender}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                if (character.firstEpisode.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Дебют: ${character.firstEpisode}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (showExtended) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Public,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = character.origin,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Tv,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "${character.episodeCount} серій",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onFavoriteClick) {
                    Icon(
                        imageVector = if (isFavorite) {
                            Icons.Filled.Favorite
                        } else {
                            Icons.Outlined.FavoriteBorder
                        },
                        contentDescription = if (isFavorite) "Прибрати з обраного" else "Додати в обране",
                        modifier = Modifier.size(18.dp),
                        tint = if (isFavorite) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CharacterImage(
    imageUrl: String,
    contentDescription: String,
    placeholderIconSize: Dp,
    modifier: Modifier = Modifier,
    reloadTrigger: Int = 0
) {
    if (imageUrl.isBlank()) {
        Icon(
            imageVector = Icons.Outlined.Person,
            contentDescription = contentDescription,
            modifier = Modifier.size(placeholderIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val context = LocalContext.current
    var imageState by remember(imageUrl) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }
    var retryCount by remember(imageUrl) { mutableIntStateOf(0) }

    LaunchedEffect(reloadTrigger) {
        if (reloadTrigger > 0 && imageState is AsyncImagePainter.State.Error) {
            retryCount++
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        key(imageUrl, retryCount) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .setParameter("retry", retryCount, memoryCacheKey = null)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onState = { imageState = it }
            )
        }

        when (imageState) {
            is AsyncImagePainter.State.Empty,
            is AsyncImagePainter.State.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
            is AsyncImagePainter.State.Error -> {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(placeholderIconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is AsyncImagePainter.State.Success -> Unit
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterDetailScreen(
    character: Character?,
    isFavorite: Boolean = false,
    onFavoriteClick: () -> Unit = {},
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    var exportStatusMessage by remember { mutableStateOf<String?>(null) }
    var isPermissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && character != null) {
            isPermissionDenied = false
            isExporting = true
            coroutineScope.launch {
                val result = CharacterCardExporter.saveCharacterPosterToGallery(context, character)
                isExporting = false
                exportStatusMessage = result.getOrElse {
                    "Не вдалося зберегти картку в Галерею. Спробуйте ще раз."
                }
            }
        } else {
            isPermissionDenied = true
            exportStatusMessage = "Дозвіл не надано: збереження картки в Галерею недоступне. Усі інші функції застосунку працюють."
        }
    }

    val portalColors = LocalPortalColors.current

    PortalScreenContainer {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Text(
                                text = character?.name ?: "Деталі персонажа",
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Назад"
                                )
                            }
                        },
                        actions = {
                            if (character != null) {
                                IconButton(onClick = onFavoriteClick) {
                                    Icon(
                                        imageVector = if (isFavorite) {
                                            Icons.Filled.Favorite
                                        } else {
                                            Icons.Outlined.FavoriteBorder
                                        },
                                        contentDescription = if (isFavorite) {
                                            "Прибрати з обраного"
                                        } else {
                                            "Додати в обране"
                                        },
                                        tint = if (isFavorite) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        ) { innerPadding ->
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                errorMessage != null && character == null -> {
                    Box(modifier = Modifier.padding(innerPadding)) {
                        ErrorStateView(
                            message = errorMessage,
                            onRetry = onRetry
                        )
                    }
                }
                character == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.errorContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.WarningAmber,
                                    contentDescription = null,
                                    modifier = Modifier.size(34.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Персонажа не знайдено",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onBackClick,
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text("Повернутися назад")
                            }
                        }
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = portalColors.heroTintAlpha)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(168.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(
                                            width = 2.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(24.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CharacterImage(
                                        imageUrl = character.imageUrl,
                                        contentDescription = character.name,
                                        placeholderIconSize = 72.dp
                                    )

                                    val badgeColor = when (character.status) {
                                        "Alive" -> portalColors.statusAlive
                                        "Dead" -> portalColors.statusDead
                                        else -> portalColors.statusUnknown
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(topStart = 10.dp),
                                        color = badgeColor,
                                        modifier = Modifier.align(Alignment.BottomEnd)
                                    ) {
                                        Text(
                                            text = character.status,
                                            color = portalColors.onStatusBadge,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = character.name,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "${character.species} · ${character.gender} · ${character.origin}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shadowElevation = 2.dp
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                DetailInfoRow(label = "Вид (Species)", value = character.species, icon = Icons.Outlined.Category)
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )

                                DetailInfoRow(label = "Стать (Gender)", value = character.gender, icon = Icons.Outlined.Person)
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )

                                DetailInfoRow(label = "Походження (Origin)", value = character.origin, icon = Icons.Outlined.Public)
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )

                                DetailInfoRow(label = "Остання відома локація", value = character.location, icon = Icons.Outlined.Place)
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )

                                DetailInfoRow(label = "Кількість епізодів", value = "${character.episodeCount} серій", icon = Icons.Outlined.Tv)
                                if (character.firstEpisode.isNotBlank()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                    DetailInfoRow(label = "Перша поява", value = character.firstEpisode, icon = Icons.Outlined.Tv)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                if (CharacterCardExporter.hasPermission(context)) {
                                    isPermissionDenied = false
                                    isExporting = true
                                    coroutineScope.launch {
                                        val result = CharacterCardExporter.saveCharacterPosterToGallery(context, character)
                                        isExporting = false
                                        exportStatusMessage = result.getOrElse {
                                            "Не вдалося зберегти картку в Галерею. Спробуйте ще раз."
                                        }
                                    }
                                } else {
                                    permissionLauncher.launch(CharacterCardExporter.requiredPermission())
                                }
                            },
                            enabled = !isExporting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isExporting) {
                                    "Збереження картки..."
                                } else {
                                    "Зберегти картку в Галерею"
                                },
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        if (exportStatusMessage != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isPermissionDenied) {
                                        MaterialTheme.colorScheme.errorContainer
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    }
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = exportStatusMessage!!,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isPermissionDenied) {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        }
                                    )
                                    if (isPermissionDenied) {
                                        TextButton(
                                            onClick = {
                                                val intent = Intent(
                                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                    Uri.fromParts("package", context.packageName, null)
                                                )
                                                context.startActivity(intent)
                                            }
                                        ) {
                                            Text("Відкрити налаштування дозволів")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailInfoRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun StatusFilterRow(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { category ->
            val isSelected = category == selectedCategory
            FilterChip(
                selected = isSelected,
                onClick = { onCategorySelected(category) },
                shape = RoundedCornerShape(20.dp),
                label = {
                    Text(
                        text = when (category) {
                            "Alive" -> "Живі"
                            "Dead" -> "Мертві"
                            "unknown" -> "Невідомо"
                            else -> "Всі"
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
        }
    }
}

@Composable
fun ErrorStateView(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Помилка завантаження",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Спробувати ще")
            }
        }
    }
}

@Composable
fun EmptySearchStateView(
    query: String,
    status: String,
    onClearSearch: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Нічого не знайдено",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            val categoryNote = if (status == "Всі") "" else " у категорії «$status»"
            Text(
                text = "За запитом «$query»$categoryNote персонажів не знайдено.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onClearSearch,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Text("Очистити пошук")
            }
        }
    }
}

@Composable
fun EmptyStateView(
    status: String,
    searchQuery: String = "",
    onResetFilters: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Персонажів не знайдено",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            val description = if (searchQuery.isNotBlank()) {
                "За запитом «$searchQuery» (категорія: «$status») API не повернув жодного персонажа."
            } else {
                "Немає персонажів із категорією «$status». Спробуйте вибрати інший фільтр."
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
            if (status != "Всі" || searchQuery.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onResetFilters,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Text("Скинути фільтри")
                }
            }
        }
    }
}