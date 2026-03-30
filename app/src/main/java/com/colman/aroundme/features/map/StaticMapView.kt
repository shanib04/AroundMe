package com.colman.aroundme.features.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.ContextCompat
import com.colman.aroundme.R
import com.colman.aroundme.data.model.Event
import com.colman.aroundme.utils.MapCoordinate
import kotlin.math.abs
import kotlin.math.max

class StaticMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onMapMoved: (() -> Unit)? = null
    var onMarkerTapped: ((String) -> Unit)? = null

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.map_background)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x22FFFFFF
        strokeWidth = resources.displayMetrics.density
    }
    private val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x1A0A173E
        strokeWidth = 14f * resources.displayMetrics.density
        style = Paint.Style.STROKE
    }
    private val roadAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x10FF6B6B
        strokeWidth = 7f * resources.displayMetrics.density
        style = Paint.Style.STROKE
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.map_text_dark)
        textSize = 12f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }
    private val markerTapRadius = 22f * resources.displayMetrics.density
    private val markerBounds = mutableMapOf<String, Pair<Float, Float>>()
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())

    private var events: List<Event> = emptyList()
    private var searchCenter: MapCoordinate = DEFAULT_CENTER
    private var selectedEventId: String? = null
    private var zoomScale = 1f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isPanning = false

    val currentCenter: MapCoordinate
        get() = searchCenter

    fun updateState(
        events: List<Event>,
        searchCenter: MapCoordinate,
        selectedEventId: String?
    ) {
        this.events = events
        this.searchCenter = searchCenter
        this.selectedEventId = selectedEventId
        invalidate()
    }

    fun animateToCenter(center: MapCoordinate) {
        searchCenter = center
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        drawGrid(canvas)
        drawRoads(canvas)
        drawCenterPulse(canvas)
        drawMarkers(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isPanning = false
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    if (abs(dx) > 3f || abs(dy) > 3f) {
                        isPanning = true
                        panBy(dx, dy)
                        lastTouchX = event.x
                        lastTouchY = event.y
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isPanning) {
                    findTappedMarker(event.x, event.y)?.let { eventId ->
                        onMarkerTapped?.invoke(eventId)
                        performClick()
                        return true
                    }
                } else {
                    onMapMoved?.invoke()
                }
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun panBy(dx: Float, dy: Float) {
        val longitudeDelta = (dx / width) * visibleLongitudeSpan()
        val latitudeDelta = (dy / height) * visibleLatitudeSpan()
        searchCenter = MapCoordinate(
            latitude = searchCenter.latitude - latitudeDelta,
            longitude = searchCenter.longitude - longitudeDelta
        )
        invalidate()
    }

    private fun drawGrid(canvas: Canvas) {
        val step = max(80f, width / 6f)
        var x = 0f
        while (x <= width.toFloat()) {
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            x += step
        }
        var y = 0f
        while (y <= height.toFloat()) {
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            y += step
        }
    }

    private fun drawRoads(canvas: Canvas) {
        val roadOne = RectF(width * 0.08f, height * 0.2f, width * 0.92f, height * 0.78f)
        val roadTwo = RectF(width * 0.2f, height * 0.08f, width * 0.78f, height * 0.92f)
        canvas.drawArc(roadOne, 205f, 130f, false, roadPaint)
        canvas.drawArc(roadTwo, 25f, 130f, false, roadPaint)
        canvas.drawArc(roadOne, 205f, 130f, false, roadAccentPaint)
        canvas.drawArc(roadTwo, 25f, 130f, false, roadAccentPaint)
    }

    private fun drawCenterPulse(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x22FF6B6B
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.map_primary)
        }
        canvas.drawCircle(centerX, centerY, 26f * resources.displayMetrics.density, pulsePaint)
        canvas.drawCircle(centerX, centerY, 8f * resources.displayMetrics.density, dotPaint)
    }

    private fun drawMarkers(canvas: Canvas) {
        markerBounds.clear()
        events.forEach { event ->
            val point = toScreenPoint(MapCoordinate(event.latitude, event.longitude))
            val selected = event.id == selectedEventId
            markerBounds[event.id] = point
            markerPaint.color = resolveCategoryColor(event.category)
            val radius = if (selected) 14f else 11f
            canvas.drawCircle(point.first, point.second, radius * resources.displayMetrics.density, markerPaint)
            canvas.drawCircle(point.first, point.second, radius * resources.displayMetrics.density, markerBorderPaint)
            if (selected) {
                canvas.drawText(event.title, point.first, point.second - 18f * resources.displayMetrics.density, labelPaint)
            }
        }
    }

    private fun toScreenPoint(position: MapCoordinate): Pair<Float, Float> {
        val longitudeSpan = visibleLongitudeSpan()
        val latitudeSpan = visibleLatitudeSpan()
        val left = searchCenter.longitude - longitudeSpan / 2
        val top = searchCenter.latitude + latitudeSpan / 2
        val xRatio = ((position.longitude - left) / longitudeSpan).toFloat()
        val yRatio = ((top - position.latitude) / latitudeSpan).toFloat()
        val x = xRatio.coerceIn(0.05f, 0.95f) * width
        val y = yRatio.coerceIn(0.1f, 0.9f) * height
        return x to y
    }

    private fun findTappedMarker(x: Float, y: Float): String? {
        return markerBounds.entries.firstOrNull { (_, point) ->
            val dx = x - point.first
            val dy = y - point.second
            (dx * dx) + (dy * dy) <= markerTapRadius * markerTapRadius
        }?.key
    }

    private fun visibleLatitudeSpan(): Double = BASE_LATITUDE_SPAN / zoomScale

    private fun visibleLongitudeSpan(): Double = BASE_LONGITUDE_SPAN / zoomScale

    private fun resolveCategoryColor(category: String): Int {
        return when (category) {
            "Food" -> 0xFFEE7C2B.toInt()
            "Music" -> 0xFFFF6B6B.toInt()
            "Art" -> 0xFF8E7CFF.toInt()
            "Beer" -> 0xFFFFC857.toInt()
            "Gaming" -> 0xFF3B82F6.toInt()
            else -> 0xFF20C997.toInt()
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoomScale = (zoomScale * detector.scaleFactor).coerceIn(0.8f, 3.5f)
            invalidate()
            return true
        }
    }

    companion object {
        private val DEFAULT_CENTER = MapCoordinate(31.7780, 35.2217)
        private const val BASE_LATITUDE_SPAN = 0.18
        private const val BASE_LONGITUDE_SPAN = 0.22
    }
}
