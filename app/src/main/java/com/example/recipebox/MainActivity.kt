package com.example.recipebox

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private lateinit var store: RecipeStore
    private var incomingSharedText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = RecipeStore(applicationContext)
        readShareIntent(intent)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    RecipeBoxApp(store, incomingSharedText) { incomingSharedText = null }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readShareIntent(intent)
    }

    private fun readShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            incomingSharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        }
    }
}

@Composable
fun RecipeBoxApp(store: RecipeStore, incomingSharedText: String?, consumeIncoming: () -> Unit) {
    val recipes by store.recipes.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf<Screen>(Screen.Library) }
    var pendingImport by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(incomingSharedText) {
        if (!incomingSharedText.isNullOrBlank()) {
            val url = SecureUrl.validate(incomingSharedText).getOrNull()
            if (url != null) {
                pendingImport = url
                screen = Screen.Import
            }
            consumeIncoming()
        }
    }

    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                AppContextHolder.context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(store.exportJson().toByteArray(Charsets.UTF_8))
                }
            }
        }
    }
    val restoreBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val text = AppContextHolder.context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!text.isNullOrBlank()) store.importJson(text)
            }
        }
    }

    when (val s = screen) {
        Screen.Library -> LibraryScreen(
            recipes = recipes,
            onRecipe = { screen = Screen.Detail(it.id) },
            onAdd = { screen = Screen.Edit(null) },
            onImport = { pendingImport = it; screen = Screen.Import },
            onBackup = { createBackup.launch("RecipeBox-backup.json") },
            onRestore = { restoreBackup.launch(arrayOf("application/json", "text/json", "text/plain")) }
        )
        Screen.Import -> ImportScreen(
            initialUrl = pendingImport.orEmpty(),
            onBack = { screen = Screen.Library },
            onImported = { recipe -> store.upsert(recipe); screen = Screen.Detail(recipe.id) }
        )
        is Screen.Detail -> {
            val recipe = recipes.firstOrNull { it.id == s.id }
            if (recipe == null) screen = Screen.Library else DetailScreen(
                recipe = recipe,
                onBack = { screen = Screen.Library },
                onEdit = { screen = Screen.Edit(recipe.id) },
                onUpdate = store::upsert,
                onDelete = { store.delete(recipe.id); screen = Screen.Library }
            )
        }
        is Screen.Edit -> {
            val recipe = s.id?.let { id -> recipes.firstOrNull { it.id == id } }
            EditRecipeScreen(
                existing = recipe,
                onBack = { screen = if (recipe == null) Screen.Library else Screen.Detail(recipe.id) },
                onSave = { saved -> store.upsert(saved); screen = Screen.Detail(saved.id) }
            )
        }
    }
}

sealed interface Screen {
    data object Library : Screen
    data object Import : Screen
    data class Detail(val id: String) : Screen
    data class Edit(val id: String?) : Screen
}

object AppContextHolder {
    lateinit var context: android.content.Context
}
