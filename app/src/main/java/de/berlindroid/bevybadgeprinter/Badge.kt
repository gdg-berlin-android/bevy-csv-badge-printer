package de.berlindroid.bevybadgeprinter

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

@Preview(widthDp = 300, heightDp = 200)
@Composable
private fun BadgePreview() {
    Badge(
        name = "Mario Bodemann",
        backgroundRes = R.drawable.nametags_monster,
    )
}

@Composable
fun Badge(
    modifier: Modifier = Modifier,
    name: String,
    @DrawableRes backgroundRes: Int? = null
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier
                .fillMaxSize()
                .background(Color.White)
        )

        backgroundRes?.let { res ->
            Image(
                modifier = Modifier.fillMaxSize(),
                painter = painterResource(res),
                contentDescription = null
            )
        }

        Text(
            modifier = Modifier
                .padding(16.dp),
            textAlign = TextAlign.Center,
            text = name,
            color = Color.Black,
            autoSize = TextAutoSize.StepBased(),
            lineHeight = 1.2.em
        )
    }
}
