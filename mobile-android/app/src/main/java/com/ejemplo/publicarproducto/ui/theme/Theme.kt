package com.ejemplo.publicarproducto.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Tema visual de PulgaTec (rediseño UI/UX).
 * Toda la app usa MaterialTheme.colorScheme / shapes / typography:
 * ninguna pantalla debe volver a hardcodear colores hexadecimales.
 */
private val EsquemaPulgaTec = lightColorScheme(
    primary = RojoPulgaTec,
    onPrimary = Color.White,
    primaryContainer = RojoPulgaTecSuave,
    onPrimaryContainer = RojoPulgaTecOscuro,
    secondary = RojoPulgaTecOscuro,
    onSecondary = Color.White,
    secondaryContainer = RojoPulgaTecSuave,
    onSecondaryContainer = RojoPulgaTecOscuro,
    background = FondoNeutro,
    onBackground = TextoPrincipal,
    surface = GrisTarjeta,
    onSurface = TextoPrincipal,
    surfaceVariant = GrisTarjeta,
    onSurfaceVariant = GrisTexto,
    outline = GrisBorde,
    outlineVariant = GrisBorde,
    error = RojoError,
    onError = Color.White,
    errorContainer = RojoErrorFondo,
    onErrorContainer = RojoTextoOscuro
)

private val TipografiaPulgaTec = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold)
    )
}

private val FormasPulgaTec = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp)
)

@Composable
fun PulgaTecTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EsquemaPulgaTec,
        typography = TipografiaPulgaTec,
        shapes = FormasPulgaTec,
        content = content
    )
}
