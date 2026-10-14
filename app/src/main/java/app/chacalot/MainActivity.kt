package app.chacalot

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var granted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { if (granted) Cleaner() else Gate() } } }
    }

    override fun onResume() {
        super.onResume()
        granted = Environment.isExternalStorageManager()
    }

    @Composable
    private fun Gate() = Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("Chacalot needs all-files access to find things to clean.")
        Spacer(Modifier.height(16.dp))
        Button({
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
        }) { Text("Grant access") }
    }
}

private enum class Mode(val label: String) { FILES("Files"), LEFTOVERS("Leftovers") }
private enum class Filter(val label: String, val ok: (Entry) -> Boolean) {
    ALL("All", { true }), IMAGES("Pics", { it.kind == Kind.IMAGE }),
    VIDEOS("Videos", { it.kind == Kind.VIDEO }), OTHER("Other", { it.kind == Kind.OTHER }),
}

@Composable
private fun Cleaner() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val loader = remember { ImageLoader.Builder(ctx).components { add(VideoFrameDecoder.Factory()) }.build() }
    var mode by remember { mutableStateOf(Mode.FILES) }
    var filter by remember { mutableStateOf(Filter.ALL) }
    var sort by remember { mutableStateOf(Sort.BIGGEST) }
    var all by remember { mutableStateOf<List<Entry>?>(null) }
    var index by remember { mutableIntStateOf(0) }
    val marked = remember { mutableStateListOf<Entry>() }
    var confirm by remember { mutableStateOf(false) }

    LaunchedEffect(mode) {
        all = null; index = 0; marked.clear()
        all = withContext(Dispatchers.IO) { if (mode == Mode.FILES) scanFiles() else scanLeftovers(ctx) }
    }
    val deck = remember(all, filter, sort, mode) {
        all?.filter { mode == Mode.LEFTOVERS || filter.ok(it) }?.sortedWith(sort.cmp)
    }
    // Re-sorting/filtering restarts the deck; marks are kept.
    LaunchedEffect(deck) { index = 0 }
    val pending = deck?.filter { it !in marked }

    Column(Modifier.fillMaxSize().padding(16.dp).statusBarsPadding()) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Mode.entries.forEach { FilterChip(mode == it, { mode = it }, { Text(it.label) }) }
            if (mode == Mode.FILES) {
                Filter.entries.forEach { FilterChip(filter == it, { filter = it }, { Text(it.label) }) }
            }
            Sort.entries.forEach { FilterChip(sort == it, { sort = it }, { Text(it.label) }) }
            UpdateButton()
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp), Alignment.Center) {
            val item = deck?.drop(index)?.firstOrNull { it !in marked }
            when {
                deck == null -> CircularProgressIndicator()
                item == null -> Text(if (deck.isEmpty()) "Nothing found." else "Deck finished.")
                else -> key(item) {
                    Card(item, loader) { delete ->
                        if (delete) marked += item
                        index = deck.indexOf(item) + 1
                    }
                }
            }
        }
        Text("← delete   ·   keep →", Modifier.align(Alignment.CenterHorizontally))
        Button({ confirm = true }, Modifier.fillMaxWidth(), enabled = marked.isNotEmpty()) {
            Text("Delete ${marked.size} (${Formatter.formatFileSize(ctx, marked.sumOf { it.size })})")
        }
    }

    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Delete ${marked.size} items?") },
        text = { Text("This permanently removes them. It cannot be undone.") },
        confirmButton = {
            TextButton({
                confirm = false
                val doomed = marked.toList()
                scope.launch {
                    val gone = withContext(Dispatchers.IO) { doomed.filter { it.file.deleteRecursively() } }
                    all = all?.filter { it !in gone }
                    marked.removeAll(gone.toSet())
                }
            }) { Text("Delete") }
        },
        dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } },
    )
}

@Composable
private fun Card(e: Entry, loader: ImageLoader, onSwiped: (delete: Boolean) -> Unit) {
    val ctx = LocalContext.current
    val x = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    ElevatedCard(
        Modifier.fillMaxWidth().fillMaxHeight(0.9f)
            .graphicsLayer { translationX = x.value; rotationZ = x.value / 40f }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        val w = size.width
                        scope.launch {
                            when {
                                x.value < -w / 3 -> { x.animateTo(-w * 1.5f); onSwiped(true) }
                                x.value > w / 3 -> { x.animateTo(w * 1.5f); onSwiped(false) }
                                else -> x.animateTo(0f)
                            }
                        }
                    },
                ) { change, drag -> change.consume(); scope.launch { x.snapTo(x.value + drag.x) } }
            },
    ) {
        Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
            if (e.kind == Kind.IMAGE || e.kind == Kind.VIDEO) {
                AsyncImage(e.file, e.file.name, loader, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else Text(if (e.kind == Kind.FOLDER) "📁" else "📄", style = MaterialTheme.typography.displayLarge)
        }
        Column(Modifier.padding(16.dp)) {
            Text(e.file.name, style = MaterialTheme.typography.titleMedium)
            Text(e.file.parent.orEmpty(), style = MaterialTheme.typography.bodySmall)
            Text("${Formatter.formatFileSize(ctx, e.size)} · ${DateFormat.format("yyyy-MM-dd", e.modified)}")
            if (e.note.isNotEmpty()) Text(e.note, color = MaterialTheme.colorScheme.error)
        }
    }
}
