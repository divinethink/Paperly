package com.paperly.app.feature.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paperly.app.R
import com.paperly.app.core.ui.theme.ReadingFontFamily
import com.paperly.app.feature.common.documentMeta

/** Full-screen Reader (not in bottom-nav). P2-A: first PDF page only; paging/zoom P2-B, EPUB P3. */
@Composable
fun ReaderScreen(onBack: () -> Unit, viewModel: ReaderViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reader_back))
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val doc = state.document
            val page = state.firstPage
            when {
                state.loading -> CircularProgressIndicator()
                doc == null -> Text(stringResource(R.string.reader_missing))
                page != null -> {
                    val image = remember(page) { page.asImageBitmap() }
                    Image(
                        bitmap = image,
                        contentDescription = stringResource(R.string.reader_page_description, 1, state.pageCount),
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    )
                }
                else -> Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        doc.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = ReadingFontFamily,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(documentMeta(context, doc), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(errorText(state.error)), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun errorText(error: ReaderError): Int = when (error) {
    ReaderError.PASSWORD_REQUIRED -> R.string.reader_password_required
    ReaderError.UNSUPPORTED -> R.string.reader_unsupported
    else -> R.string.reader_open_failed
}
