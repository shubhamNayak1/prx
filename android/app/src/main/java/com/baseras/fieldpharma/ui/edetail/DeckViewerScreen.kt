package com.baseras.fieldpharma.ui.edetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.baseras.fieldpharma.FieldPharmaApp
import com.baseras.fieldpharma.data.local.SlideEntity
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckViewerScreen(deckId: String, onClose: () -> Unit) {
    val app = FieldPharmaApp.instance
    var slides by remember { mutableStateOf<List<SlideEntity>>(emptyList()) }
    val viewSeconds = remember { mutableStateMapOf<String, Int>() }

    LaunchedEffect(deckId) {
        slides = app.edetailRepo.slidesFor(deckId)
    }

    val pager = rememberPagerState(pageCount = { slides.size })

    // Tick the seconds counter for the currently visible slide
    LaunchedEffect(pager.currentPage, slides.size) {
        if (slides.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(1000)
            val s = slides.getOrNull(pager.currentPage) ?: break
            viewSeconds[s.id] = (viewSeconds[s.id] ?: 0) + 1
        }
    }

    DisposableEffect(deckId) {
        onDispose {
            // Fire-and-forget tracking on close
            val snapshot = viewSeconds.toMap()
            MainScope().launch {
                app.edetailRepo.trackViews(visitId = null, slideViewSeconds = snapshot)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Slide ${pager.currentPage + 1}/${slides.size}") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(Color.Black)) {
            if (slides.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                    val slide = slides[page]
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AsyncImage(
                            model = slide.imageUrl,
                            contentDescription = slide.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                        slide.title?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, color = Color.White, style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Viewed: ${viewSeconds[slide.id] ?: 0}s",
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

