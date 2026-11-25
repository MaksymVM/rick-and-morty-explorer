package com.example.rickandmortyexplorer

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.rickandmortyexplorer.ui.theme.RickAndMortyExplorerTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Character(
    val id: Int,
    val name: String,
    val status: String,
    val species: String,
    val gender: String,
    val origin: String,
    val location: String,
    val episodeCount: Int,
    val imageUrl: String = ""
)

data class CharactersUiState(
    val characters: List<Character> = emptyList(),
    val categories: List<String> = listOf("Всі", "Alive", "Dead", "unknown"),
    val selectedStatus: String = "Всі",
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedCharacter: Character? = null,
    val isDetailLoading: Boolean = false,
    val detailErrorMessage: String? = null
)

class CharactersViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsManager = SettingsManager(application)

    private val _uiState = MutableStateFlow(CharactersUiState())
    val uiState: StateFlow<CharactersUiState> = _uiState.asStateFlow()

    private var fetchJob: Job? = null

    val showExtendedDetails: StateFlow<Boolean> = settingsManager.showExtendedDetailsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
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
        _uiState.update { it.copy(searchQuery = newQuery) }
        loadCharacters(
            status = _uiState.value.selectedStatus,
            query = newQuery,
            debounceMs = 350L
        )
    }

    fun retry() {
        val current = _uiState.value
        loadCharacters(status = current.selectedStatus, query = current.searchQuery)
    }

    fun resetFilters() {
        _uiState.update { it.copy(selectedStatus = "Всі", searchQuery = "") }
        loadCharacters(status = "Всі", query = "")
    }

    private fun loadCharacters(
        status: String,
        query: String,
        debounceMs: Long = 0L
    ) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            if (debounceMs > 0L) {
                delay(debounceMs)
            }
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val queryStatus = if (status == "Всі") null else status.lowercase()
                val queryName = query.trim().takeIf { it.isNotEmpty() }
                val response = RetrofitClient.api.getCharacters(
                    status = queryStatus,
                    name = queryName
                )
                val loadedCharacters = response.results.orEmpty()
                    .map { it.toCharacter() }
                    .distinctBy { it.id }
                _uiState.update {
                    it.copy(
                        characters = loadedCharacters,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e.isEmptyApiResult()) {
                    _uiState.update {
                        it.copy(
                            characters = emptyList(),
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            characters = emptyList(),
                            isLoading = false,
                            errorMessage = e.toUserErrorMessage()
                        )
                    }
                }
            }
        }
    }

    fun loadCharacterById(id: Int) {
        val existing = _uiState.value.characters.find { it.id == id }
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
                _uiState.update {
                    it.copy(
                        selectedCharacter = dto.toCharacter(),
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
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RickAndMortyExplorerTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    // Получаем ViewModel с использованием фабрики AndroidViewModel
    val viewModel: CharactersViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            LocalContext.current.applicationContext as Application
        )
    )

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
                ?: uiState.selectedCharacter?.takeIf { it.id == characterId }

            LaunchedEffect(characterId) {
                if (character == null && characterId != -1) {
                    viewModel.loadCharacterById(characterId)
                }
            }

            CharacterDetailScreen(
                character = character,
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
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharactersCatalogScreen(
    viewModel: CharactersViewModel,
    onCharacterClick: (Int) -> Unit,
    onSettingsClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val showExtended by viewModel.showExtendedDetails.collectAsState()
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Персонажі Rick & Morty",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Налаштування"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
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
                placeholder = { Text("Пошук персонажа за ім'ям...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Очистити пошук"
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
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
                uiState.characters.isEmpty() -> {
                    EmptyStateView(
                        status = uiState.selectedStatus,
                        searchQuery = uiState.searchQuery,
                        onResetFilters = { viewModel.resetFilters() }
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = uiState.characters,
                            key = { it.id },
                            contentType = { "character" }
                        ) { character ->
                            CharacterCard(
                                character = character,
                                showExtended = showExtended,
                                onClick = { onCharacterClick(character.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: CharactersViewModel,
    onBackClick: () -> Unit
) {
    val showExtended by viewModel.showExtendedDetails.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Налаштування", fontWeight = FontWeight.Bold)
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
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Розширений показ у списку",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
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
        }
    }
}

@Composable
fun CharacterCard(
    character: Character,
    showExtended: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (showExtended) 110.dp else 96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = character.name,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val badgeColor = when (character.status) {
                    "Alive" -> Color(0xFF2E7D32)
                    "Dead" -> Color(0xFFC62828)
                    else -> Color.Gray
                }

                Surface(
                    shape = RoundedCornerShape(topStart = 8.dp),
                    color = badgeColor,
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Text(
                        text = character.status,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = character.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val dotColor = when (character.status) {
                        "Alive" -> Color(0xFF4CAF50)
                        "Dead" -> Color(0xFFE53935)
                        else -> Color.Gray
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

            IconButton(onClick = { }) {
                Icon(
                    imageVector = Icons.Outlined.FavoriteBorder,
                    contentDescription = "Додати в обране",
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterDetailScreen(
    character: Character?,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = character?.name ?: "Деталі персонажа",
                        fontWeight = FontWeight.Bold
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
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
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
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Персонажа не знайдено",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onBackClick) {
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
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = character.name,
                            modifier = Modifier.size(96.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val badgeColor = when (character.status) {
                            "Alive" -> Color(0xFF2E7D32)
                            "Dead" -> Color(0xFFC62828)
                            else -> Color.Gray
                        }

                        Surface(
                            shape = RoundedCornerShape(topStart = 12.dp),
                            color = badgeColor,
                            modifier = Modifier.align(Alignment.BottomEnd)
                        ) {
                            Text(
                                text = character.status,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = character.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            DetailInfoRow(label = "Вид (Species)", value = character.species, icon = Icons.Outlined.Category)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                            DetailInfoRow(label = "Стать (Gender)", value = character.gender, icon = Icons.Outlined.Person)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                            DetailInfoRow(label = "Походження (Origin)", value = character.origin, icon = Icons.Outlined.Public)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                            DetailInfoRow(label = "Остання відома локація", value = character.location, icon = Icons.Outlined.Place)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                            DetailInfoRow(label = "Кількість епізодів", value = "${character.episodeCount} серій", icon = Icons.Outlined.Tv)
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
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isExporting) {
                                "Збереження картки..."
                            } else {
                                "Зберегти картку в Галерею"
                            }
                        )
                    }

                    if (exportStatusMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
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

@Composable
fun DetailInfoRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .padding(end = 8.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
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
                label = {
                    Text(
                        text = when (category) {
                            "Alive" -> "Живі"
                            "Dead" -> "Мертві"
                            "unknown" -> "Невідомо"
                            else -> "Всі"
                        }
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
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
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Помилка завантаження",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
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
            Button(onClick = onRetry) {
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
fun EmptyStateView(
    status: String,
    searchQuery: String = "",
    onResetFilters: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Персонажів не знайдено",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
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
                OutlinedButton(onClick = onResetFilters) {
                    Text("Скинути фільтри")
                }
            }
        }
    }
}