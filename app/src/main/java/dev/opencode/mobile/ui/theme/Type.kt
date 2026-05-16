package dev.opencode.mobile.ui.theme

import androidx.compose.material.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.opencode.mobile.R

private val TitilliumWeb = FontFamily(
    Font(R.font.titilliumweb_regular, weight = FontWeight.Normal),
    Font(R.font.titilliumweb_semibold, weight = FontWeight.SemiBold),
    Font(R.font.titilliumweb_bold, weight = FontWeight.Bold),
)

private val OcrB = FontFamily(
    Font(R.font.ocrb_regular, weight = FontWeight.Normal),
)

val AppTypography = Typography(
    h1 = TextStyle(fontFamily = TitilliumWeb, fontWeight = FontWeight.Bold, fontSize = 96.sp),
    h2 = TextStyle(fontFamily = TitilliumWeb, fontWeight = FontWeight.Bold, fontSize = 60.sp),
    h3 = TextStyle(fontFamily = TitilliumWeb, fontWeight = FontWeight.SemiBold, fontSize = 48.sp),
    h4 = TextStyle(fontFamily = TitilliumWeb, fontWeight = FontWeight.SemiBold, fontSize = 34.sp),
    h5 = TextStyle(fontFamily = TitilliumWeb, fontWeight = FontWeight.Normal, fontSize = 24.sp),
    h6 = TextStyle(fontFamily = TitilliumWeb, fontWeight = FontWeight.Normal, fontSize = 20.sp),
    caption = TextStyle(fontFamily = OcrB, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    overline = TextStyle(fontFamily = OcrB, fontWeight = FontWeight.Normal, fontSize = 10.sp),
)
