package com.example.rickandmortyexplorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rickandmortyexplorer.ui.theme.RickAndMortyExplorerTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// 1. Модель даних персонажа
data class Character(
    val id: Int,
    val name: String,
    val status: String,
    val species: String,
    val gender: String,
    @DrawableRes val imageRes: Int
)

// Тестовий датасет
val sampleCharacters = listOf(
    Character(1, "Rick Sanchez", "Alive", "Human", "Male", R.drawable.rick),
    Character(2, "Morty Smith", "Alive", "Human", "Male", R.drawable.morty),
    Character(3, "Summer Smith", "Alive", "Human", "Female", R.drawable.summer),
    Character(4, "Krombopulos Michael", "Dead", "Gromflomite", "Male", R.drawable.rick),
    Character(5, "Talking Cat", "unknown", "Animal", "Male", R.drawable.morty),
    Character(6, "Abradolf Lincler", "Dead", "Human / Genetic Experiment", "Male", R.drawable.summer)
)

// 2. Стан екрана окремим data class з полями val (Незмінний/Immutable)
data class CharactersUiState(
    val allCharacters: List<Character> = emptyList(),
    val categories: List<String> = listOf("Всі", "Alive", "Dead", "unknown"),
    val selectedStatus: String = "Всі"
) {
    // Обчислювальне поле для відфільтрованого списку
    val filteredCharacters: List<Character>
        get() = if (selectedStatus == "Всі") {
            allCharacters
        } else {
            allCharacters.filter { it.status.equals(selectedStatus, ignoreCase = true) }
        }
}

// 3. ViewModel: тримає стан і закриває його від прямого запису
class CharactersViewModel : ViewModel() {
    // Приватний змінюваний стан
    private val _uiState = MutableStateFlow(
        CharactersUiState(allCharacters = sampleCharacters)
    )
    // Публічний незмінний стан для UI
    val uiState: StateFlow<CharactersUiState> = _uiState.asStateFlow()

    // Функція-подія для оновлення стану через copy()
    fun onStatusSelected(newStatus: String) {
        _uiState.update { currentState ->
            currentState.copy(selectedStatus = newStatus)
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RickAndMortyExplorerTheme {
                // Штатний виклик екрана
                CharactersCatalogScreen()
            }
        }
    }
}

// 4. Composable-екран: отримує ViewModel штатним способом (через viewModel())
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharactersCatalogScreen(
    viewModel: CharactersViewModel = viewModel()
) {
    // Підписка на StateFlow через collectAsState (без жодного remember для стану фільтра)
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Персонажі Rick & Morty",
                        fontWeight = FontWeight.Bold
                    )
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
            StatusFilterRow(
                categories = uiState.categories,
                selectedCategory = uiState.selectedStatus,
                onCategorySelected = { newStatus ->
                    // Виклик функції-події у ViewModel
                    viewModel.onStatusSelected(newStatus)
                }
            )

            if (uiState.filteredCharacters.isEmpty()) {
                EmptyStateView(status = uiState.selectedStatus)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.filteredCharacters, key = { it.id }) { character ->
                        CharacterCard(character = character)
                    }
                }
            }
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
fun EmptyStateView(status: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "🔍", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Персонажів не знайдено",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Немає персонажів із категорією «$status». Спробуйте вибрати інший фільтр.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CharacterCard(character: Character) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
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
                    .size(96.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Image(
                    painter = painterResource(id = character.imageRes),
                    contentDescription = character.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
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

                Spacer(modifier = Modifier.height(6.dp))

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

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Стать: ${character.gender}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            IconButton(onClick = { /* дія для обраного */ }) {
                Text(text = "🤍", fontSize = 20.sp)
            }
        }
    }
}