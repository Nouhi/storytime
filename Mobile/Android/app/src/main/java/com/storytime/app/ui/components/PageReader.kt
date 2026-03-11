package com.storytime.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import kotlin.math.abs

/**
 * A page reader with a 3D page-curl rotation effect.
 *
 * Pages are 1-indexed (page 1 through [totalPages]).
 * The [currentPage] parameter controls which page is displayed,
 * and [onPageChanged] is called when the user swipes to a new page.
 */
@Composable
fun PageReader(
    currentPage: Int,
    totalPages: Int,
    onPageChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (page: Int) -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = (currentPage - 1).coerceIn(0, (totalPages - 1).coerceAtLeast(0)),
        pageCount = { totalPages }
    )

    // Sync external currentPage → pager
    LaunchedEffect(currentPage) {
        val targetIndex = (currentPage - 1).coerceIn(0, (totalPages - 1).coerceAtLeast(0))
        if (pagerState.currentPage != targetIndex) {
            pagerState.animateScrollToPage(targetIndex)
        }
    }

    // Sync pager → external currentPage
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { index ->
            val page = index + 1
            if (page != currentPage) {
                onPageChanged(page)
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        beyondViewportPageCount = 1
    ) { pageIndex ->
        val pageOffset = (pagerState.currentPage - pageIndex) +
                pagerState.currentPageOffsetFraction

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 3D page curl rotation
                    rotationY = pageOffset * -30f
                    cameraDistance = 12f * density

                    // Fade pages as they turn
                    alpha = lerp(
                        start = 0.5f,
                        stop = 1f,
                        fraction = 1f - abs(pageOffset).coerceIn(0f, 1f)
                    )

                    // Shadow / elevation for turning pages
                    if (abs(pageOffset) > 0.01f) {
                        shadowElevation = 8f
                    }
                }
        ) {
            // Page numbers are 1-indexed
            content(pageIndex + 1)
        }
    }
}
