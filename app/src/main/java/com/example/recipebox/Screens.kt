package com.example.recipebox

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    recipes: List<Recipe>,
    onRecipe: (Recipe) -> Unit,
    onAdd: () -> Unit,
    onImport: (String) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var importDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var aboutDialog by remember { mutableStateOf(false) }

    val categories = remember(recipes) { recipes.flatMap { it.categories }.distinct().sorted() }
    val filtered = remember(recipes, query, selectedCategory) {
        val q = query.trim().lowercase()
        recipes.filter { r ->
            (selectedCategory == null || selectedCategory in r.categories) &&
                (q.isBlank() || listOf(r.displayTitle(), r.title, r.description, r.notes, r.sourceName,
                    r.visibleIngredients().joinToString(" ") { it.originalText },
                    r.categories.joinToString(" ")).any { it.lowercase().contains(q) })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RecipeBox", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { importDialog = true }) { Icon(Icons.Default.Link, "Import link") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Back up to cloud / Drive") }, leadingIcon = { Icon(Icons.Default.CloudUpload, null) }, onClick = { menu = false; onBackup() })
                            DropdownMenuItem(text = { Text("Restore backup") }, leadingIcon = { Icon(Icons.Default.CloudDownload, null) }, onClick = { menu = false; onRestore() })
                            DropdownMenuItem(text = { Text("About") }, onClick = { menu = false; aboutDialog = true })
                        }
                    }
                }
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, "Add recipe") } }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search recipes, ingredients, notes…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )
            if (categories.isNotEmpty()) {
                LazyColumn(modifier = Modifier.heightIn(max = 96.dp)) {
                    item {
                        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = selectedCategory == null, onClick = { selectedCategory = null }, label = { Text("All") })
                            categories.take(5).forEach { c ->
                                FilterChip(selected = selectedCategory == c, onClick = { selectedCategory = c }, label = { Text(c) })
                            }
                        }
                    }
                }
            }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MenuBook, null, modifier = Modifier.size(54.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(if (recipes.isEmpty()) "Your cookbook is empty" else "No recipes match")
                        Text("Use + to add one, or share a recipe link to RecipeBox.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filtered, key = { it.id }) { recipe -> RecipeCard(recipe, onRecipe) }
                }
            }
        }
    }

    if (aboutDialog) AlertDialog(
        onDismissRequest = { aboutDialog = false },
        title = { Text("About RecipeBox") },
        text = { Text("Version ${BuildConfig.VERSION_NAME}") },
        confirmButton = { TextButton(onClick = { aboutDialog = false }) { Text("OK") } }
    )

    if (importDialog) AlertDialog(
        onDismissRequest = { importDialog = false },
        title = { Text("Import recipe link") },
        text = {
            OutlinedTextField(importText, { importText = it }, modifier = Modifier.fillMaxWidth(), label = { Text("https://…") })
        },
        confirmButton = {
            TextButton(onClick = {
                val url = SecureUrl.validate(importText).getOrNull()
                if (url != null) { importDialog = false; onImport(url) }
            }) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = { importDialog = false }) { Text("Cancel") } }
    )
}

@Composable
private fun RecipeCard(recipe: Recipe, onClick: (Recipe) -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().clickable { onClick(recipe) }) {
        Row(Modifier.fillMaxWidth().heightIn(min = 112.dp)) {
            if (recipe.imageUrl.startsWith("https://")) {
                AsyncImage(
                    model = recipe.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(112.dp)
                )
            }
            Column(Modifier.padding(14.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(recipe.displayTitle(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (recipe.isFavorite) Icon(Icons.Default.Favorite, null)
                }
                if (recipe.categories.isNotEmpty()) Text(recipe.categories.take(3).joinToString(" • "), style = MaterialTheme.typography.labelMedium)
                if (recipe.sourceName.isNotBlank()) Text(recipe.sourceName, style = MaterialTheme.typography.bodySmall)
                recipe.servings?.let { Text("Serves ${FractionFormatter.format(it)}", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(initialUrl: String, onBack: () -> Unit, onImported: (Recipe) -> Unit) {
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { TopAppBar(title = { Text("Secure import") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.padding(padding).padding(20.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("The webpage is fetched by the isolated extractor. RecipeBox does not run the page's scripts on your phone.")
            OutlinedTextField(url, { url = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Recipe URL") }, singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !loading,
                onClick = {
                    loading = true; error = null
                    scope.launch {
                        RecipeImporter().import(url)
                            .onSuccess(onImported)
                            .onFailure { error = it.message ?: "Import failed" }
                        loading = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Download, null)
                Spacer(Modifier.width(8.dp)); Text(if (loading) "Importing…" else "Import securely")
            }
            Text("Accepted links: http/https only. javascript:, file:, data: and app-internal schemes are rejected.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(recipe: Recipe, onBack: () -> Unit, onEdit: () -> Unit, onUpdate: (Recipe) -> Unit, onDelete: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val ingredients = recipe.visibleIngredients()
    var targetServings by remember(recipe.id) { mutableStateOf(recipe.servings ?: 1.0) }
    val multiplier = if (recipe.servings != null && recipe.servings > 0) targetServings / recipe.servings else 1.0
    var deleteConfirm by remember { mutableStateOf(false) }
    var expandedDescription by remember(recipe.id) { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Recipe", maxLines = 1) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = { onUpdate(recipe.copy(isFavorite = !recipe.isFavorite)) }) { Icon(if (recipe.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite") }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit") }
                IconButton(onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, ShareFormatter.format(recipe, multiplier)) }
                    context.startActivity(Intent.createChooser(intent, "Share recipe"))
                }) { Icon(Icons.Default.Share, "Share") }
            }
        )
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 36.dp)) {
            if (recipe.imageUrl.startsWith("https://")) item {
                AsyncImage(recipe.imageUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(160.dp))
            }
            item {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(recipe.displayTitle(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (recipe.description.isNotBlank()) {
                        Text(
                            recipe.description,
                            maxLines = if (expandedDescription) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (recipe.description.length > 120) {
                            TextButton(onClick = { expandedDescription = !expandedDescription }) {
                                Text(if (expandedDescription) "Show less" else "Show more")
                            }
                        }
                    }
                    if (recipe.categories.isNotEmpty()) Text(recipe.categories.joinToString(" • "), style = MaterialTheme.typography.labelLarge)
                    recipe.servings?.let {
                        Text("Portions", fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { targetServings = (targetServings - 1).coerceAtLeast(1.0) }) { Text("−") }
                            Text(FractionFormatter.format(targetServings), style = MaterialTheme.typography.titleLarge)
                            OutlinedButton(onClick = { targetServings += 1 }) { Text("+") }
                            TextButton(onClick = { targetServings = recipe.servings }) { Text("Reset") }
                        }
                    }
                    if (ingredients.isNotEmpty()) Text("Ingredients", style = MaterialTheme.typography.headlineSmall)
                }
            }
            items(ingredients) { ing ->
                Row(Modifier.padding(horizontal = 18.dp, vertical = 5.dp)) { Text("☐  ${ing.scaled(multiplier)}") }
            }
            if (ingredients.isEmpty()) item {
                Text("Ingredients weren't found in this link. Add them with Edit.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
            }
            if (recipe.steps.isNotEmpty()) item { Text("Instructions", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) }
            itemsIndexed(recipe.steps) { index, step ->
                Row(Modifier.padding(horizontal = 18.dp, vertical = 7.dp)) {
                    Text("${index + 1}.", fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp)); Text(step)
                }
            }
            if (recipe.steps.isEmpty()) item {
                Text("Cooking steps weren't found. Watch the video or add steps with Edit.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
            }
            if (recipe.notes.isNotBlank()) item {
                Column(Modifier.padding(18.dp)) { Text("My notes", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(6.dp)); Text(recipe.notes) }
            }
            item {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (recipe.videoUrl.startsWith("http")) Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(recipe.videoUrl))) }) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Watch original video") }
                    if (recipe.sourceUrl.startsWith("http") && recipe.sourceUrl != recipe.videoUrl) OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(recipe.sourceUrl))) }) { Icon(Icons.Default.OpenInBrowser, null); Spacer(Modifier.width(8.dp)); Text("View original recipe") }
                    TextButton(onClick = { deleteConfirm = true }) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(6.dp)); Text("Delete recipe") }
                }
            }
        }
    }
    if (deleteConfirm) AlertDialog(onDismissRequest = { deleteConfirm = false }, title = { Text("Delete this recipe?") }, confirmButton = { TextButton(onClick = onDelete) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRecipeScreen(existing: Recipe?, onBack: () -> Unit, onSave: (Recipe) -> Unit) {
    var title by remember(existing?.id) { mutableStateOf(existing?.displayTitle().orEmpty()) }
    var description by remember(existing?.id) { mutableStateOf(existing?.description.orEmpty()) }
    var servings by remember(existing?.id) { mutableStateOf(existing?.servings?.let(FractionFormatter::format).orEmpty()) }
    var ingredients by remember(existing?.id) { mutableStateOf(existing?.visibleIngredients()?.joinToString("\n") { it.originalText }.orEmpty()) }
    var steps by remember(existing?.id) { mutableStateOf(existing?.steps?.joinToString("\n").orEmpty()) }
    var categories by remember(existing?.id) { mutableStateOf(existing?.categories?.joinToString(", ").orEmpty()) }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    var imageUrl by remember(existing?.id) { mutableStateOf(existing?.imageUrl.orEmpty()) }
    var sourceUrl by remember(existing?.id) { mutableStateOf(existing?.sourceUrl.orEmpty()) }
    var videoUrl by remember(existing?.id) { mutableStateOf(existing?.videoUrl.orEmpty()) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (existing == null) "New recipe" else "Edit recipe") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.Close, "Cancel") } },
            actions = {
                TextButton(enabled = title.isNotBlank(), onClick = {
                    val recipe = Recipe(
                        id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                        title = title.trim(), description = description.trim(), servings = servings.toDoubleOrNull(),
                        sourceName = existing?.sourceName.orEmpty(), sourceUrl = sourceUrl.trim(), videoUrl = videoUrl.trim(), imageUrl = imageUrl.trim(),
                        ingredients = ingredients.lines().filter { it.isNotBlank() }.map(IngredientParser::parse),
                        steps = steps.lines().filter { it.isNotBlank() }.map { it.trim().removePrefix("- ") },
                        categories = categories.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet(),
                        notes = notes.trim(), isFavorite = existing?.isFavorite ?: false,
                        isUserRecipe = existing == null || existing.isUserRecipe || title.trim() != existing.title,
                        createdAt = existing?.createdAt ?: System.currentTimeMillis()
                    )
                    onSave(recipe)
                }) { Text("Save") }
            }
        )
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Recipe name") }) }
            item { OutlinedTextField(description, { description = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Description") }, minLines = 2) }
            item { OutlinedTextField(servings, { servings = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Servings") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) }
            item { OutlinedTextField(imageUrl, { imageUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Image URL (https)") }) }
            item { OutlinedTextField(ingredients, { ingredients = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Ingredients — one per line") }, minLines = 6) }
            item { OutlinedTextField(steps, { steps = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Instructions — one step per line") }, minLines = 7) }
            item { OutlinedTextField(categories, { categories = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Categories, comma separated") }) }
            item { OutlinedTextField(notes, { notes = it }, modifier = Modifier.fillMaxWidth(), label = { Text("My notes") }, minLines = 3) }
            item { OutlinedTextField(sourceUrl, { sourceUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Original recipe URL") }) }
            item { OutlinedTextField(videoUrl, { videoUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Original video URL") }) }
            item { Text("Unicode is stored natively, so recipes can freely mix English, 简体中文 and 繁體中文.", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
