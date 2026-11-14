package com.backmeupplz.meshtasticplus

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File

/** Every node that has shared a position, on OpenStreetMap. Tapping one opens its profile. */
@Composable
fun MapTab(padding: PaddingValues, now: Long, onProfile: (Long) -> Unit) {
    val placed = Mesh.nodes.values.filter { it.lat != null && it.lon != null && it.num !in Mesh.blocked }
    if (placed.isEmpty()) {
        return EmptyState(padding, Icons.Outlined.Map, stringResource(R.string.no_positions), stringResource(R.string.no_positions_body))
    }
    val dark = isSystemInDarkTheme()
    AndroidView(
        factory = { ctx ->
            Configuration.getInstance().apply {
                userAgentValue = ctx.packageName // OSM's tile policy blocks osmdroid's default user agent
                osmdroidBasePath = File(ctx.cacheDir, "osmdroid") // app cache, so no storage permission
                osmdroidTileCache = File(osmdroidBasePath, "tiles")
            }
            MapView(ctx).apply {
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                isTilesScaledToDpi = true
                // "© OpenStreetMap contributors", required by the tile license
                overlays.add(CopyrightOverlay(ctx).apply { if (dark) setTextColor(android.graphics.Color.LTGRAY) })
                if (dark) overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(NIGHT))
                addOnFirstLayoutListener { _, _, _, _, _ ->
                    zoomToBoundingBox(BoundingBox.fromGeoPoints(placed.map { GeoPoint(it.lat!!, it.lon!!) }), false, 96)
                    if (zoomLevelDouble > 15.0) controller.setZoom(15.0) // one node (or all in one spot): not street level
                }
            }
        },
        update = { map ->
            map.overlays.removeAll { it is Marker }
            placed.forEach { n ->
                map.overlays.add(Marker(map).apply {
                    position = GeoPoint(n.lat!!, n.lon!!)
                    icon = pin(map.context, n)
                    alpha = if (n.num == Mesh.myNum || n.online(now)) 1f else 0.5f
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setOnMarkerClickListener { _, _ -> onProfile(n.num); true }
                })
            }
            map.invalidate()
        },
        onRelease = { it.onDetach() },
        modifier = Modifier.fillMaxSize().padding(padding),
    )
}

/** Dark tiles: invert the brightness, then turn hues back 180° so water stays blue and forests green, a bit muted. */
private val NIGHT = ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f)).apply {
    postConcat(ColorMatrix(floatArrayOf( // hue rotation by 180° (luma weights 0.213/0.715/0.072)
        -0.574f, 1.43f, 0.144f, 0f, 0f,
        0.426f, 0.43f, 0.144f, 0f, 0f,
        0.426f, 1.43f, -0.856f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )))
    postConcat(ColorMatrix().apply { setSaturation(0.6f) })
}

/** A round badge in the node's color with its short name, like the official apps' map. */
private fun pin(ctx: Context, n: Node): BitmapDrawable {
    val d = ctx.resources.displayMetrics.density
    val size = (40 * d).toInt()
    val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(b)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val r = size / 2f
    p.color = android.graphics.Color.WHITE
    c.drawCircle(r, r, r, p)
    p.color = nodeColor(n.num).toArgb()
    c.drawCircle(r, r, r - 2.5f * d, p)
    p.color = android.graphics.Color.WHITE
    p.typeface = Typeface.DEFAULT_BOLD
    p.textAlign = Paint.Align.CENTER
    p.textSize = 11 * d
    c.drawText(n.short.ifEmpty { initials(n.name) }, r, r - (p.descent() + p.ascent()) / 2, p)
    return BitmapDrawable(ctx.resources, b)
}
