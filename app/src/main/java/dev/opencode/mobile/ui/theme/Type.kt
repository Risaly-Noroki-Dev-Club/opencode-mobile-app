package dev.opencode.mobile.ui.theme

import androidx.compose.material.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import dev.opencode.mobile.R

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val TitilliumWeb = FontFamily(
    Font(GoogleFont("Titillium Web"), provider, weight = FontWeight.Normal),
    Font(GoogleFont("Titillium Web"), provider, weight = FontWeight.Medium),
    Font(GoogleFont("Titillium Web"), provider, weight = FontWeight.SemiBold),
    Font(GoogleFont("Titillium Web"), provider, weight = FontWeight.Bold),
)

private val NotoSansSC = FontFamily(
    Font(GoogleFont("Noto Sans SC"), provider, weight = FontWeight.Normal),
    Font(GoogleFont("Noto Sans SC"), provider, weight = FontWeight.Medium),
    Font(GoogleFont("Noto Sans SC"), provider, weight = FontWeight.Bold),
)

private val OcrB = FontFamily(
    Font(R.font.ocrb_regular, weight = FontWeight.Normal),
)

val AppTypography = Typography(
    defaultFontFamily = NotoSansSC,
    h1 = TextStyle(fontFamily = TitilliumWeb, fontWeight = FontWeight.Bold, fontSize = 96.sp),
    h2 = TextStyle(fontFamily = TitilliumWeb, fontWeight = FontWeight.Bold, fontSize = 60.sp),
    h3 = TextStyle(fontFamily = TitilliumWeb, fontWeight = FontWeight.SemiBold, fontSize = 48.sp),
    h4 = TextStyle(fontFamily = TitilliumWeb, fontWeight = FontWeight.SemiBold, fontSize = 34.sp),
    h5 = TextStyle(fontFamily = TitilliumWeb, fontWeight = FontWeight.Medium, fontSize = 24.sp),
    h6 = TextStyle(fontFamily = TitilliumWeb, fontWeight = FontWeight.Medium, fontSize = 20.sp),
    subtitle1 = TextStyle(fontFamily = NotoSansSC, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    subtitle2 = TextStyle(fontFamily = NotoSansSC, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    body1 = TextStyle(fontFamily = NotoSansSC, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    body2 = TextStyle(fontFamily = NotoSansSC, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    button = TextStyle(fontFamily = NotoSansSC, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    caption = TextStyle(fontFamily = OcrB, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    overline = TextStyle(fontFamily = OcrB, fontWeight = FontWeight.Normal, fontSize = 10.sp),
)
