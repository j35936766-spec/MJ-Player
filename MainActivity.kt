package com.mj.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { MJApp() }
            }
        }
    }
}

@Composable
fun MJApp() {
    val context = LocalContext.current
    val perms = if (Build.VERSION.SDK_INT >= 33)
        arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    var granted by remember {
        mutableStateOf(perms.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> granted = result.values.all { it } }

    LaunchedEffect(Unit) { if (!granted) launcher.launch(perms) }

    if (granted) Library() else {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("ভিডিও ও অডিও ফাইল দেখাতে অনুমতি দরকার")
            Spacer(Modifier.height(16.dp))
            Button(onClick = { launcher.launch(perms) }) { Text("অনুমতি দিন") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Library() {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<MediaItemInfo>>(emptyList()) }

    LaunchedEffect(tab) {
        items = withContext(Dispatchers.IO) {
            if (tab == 0) MediaRepository.loadVideos(context) else MediaRepository.loadAudios(context)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("MJ Player") }) }) { pad ->
        Column(Modifier.padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Video") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Audio") })
            }
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("কোনো ফাইল পাওয়া যায়নি")
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(items) { index, item ->
                        ListItem(
                            modifier = Modifier.clickable {
                                val i = Intent(context, PlayerActivity::class.java).apply {
                                    putStringArrayListExtra(
                                        PlayerActivity.EXTRA_URIS,
                                        ArrayList(items.map { it.uri.toString() })
                                    )
                                    putExtra(PlayerActivity.EXTRA_INDEX, index)
                                    putExtra(PlayerActivity.EXTRA_AUDIO, tab == 1)
                                }
                                context.startActivity(i)
                            },
                            leadingContent = {
                                Icon(
                                    if (tab == 0) Icons.Default.PlayCircle else Icons.Default.MusicNote,
                                    contentDescription = null
                                )
                            },
                            headlineContent = {
                                Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = { Text(formatDuration(item.durationMs)) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}
