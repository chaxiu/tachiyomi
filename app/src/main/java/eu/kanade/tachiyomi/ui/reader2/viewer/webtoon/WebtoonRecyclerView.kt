package eu.kanade.tachiyomi.ui.reader2.viewer.webtoon

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout

const val ANIMATOR_DURATION_TIME = 200
const val DEFAULT_RATE = 1f
const val MAX_SCALE_RATE = 2f

open class WebtoonRecyclerView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0
) : RecyclerView(context, attrs, defStyle) {

    private var isZooming = false
    private var atLastPosition = false
    private var atFirstPosition = false
    private var halfWidth = 0
    private var halfHeight = 0
    private var firstVisibleItemPosition = 0
    private var lastVisibleItemPosition = 0
    private var currentScale = DEFAULT_RATE

    private var scrollPointerId = 0
    private var downX = 0
    private var downY = 0
    private val viewConfig = ViewConfiguration.get(context)
    private val touchSlop = viewConfig.scaledTouchSlop
    private var isZoomDragging = false
    private var isDoubleTapping = false
    private var isQuickScaling = false

    private val listener = GestureListener()
    private val detector = GestureDetector(context, listener)

    var tapListener: ((MotionEvent) -> Unit)? = null
    var longTapListener: ((MotionEvent) -> Unit)? = null

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        halfWidth = MeasureSpec.getSize(widthSpec) / 2
        halfHeight = MeasureSpec.getSize(heightSpec) / 2
        super.onMeasure(widthSpec, heightSpec)
    }

    inner class GestureListener : GestureDetector.SimpleOnGestureListener() {

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            tapListener?.invoke(e)
            return false
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            isDoubleTapping = true
            return false
        }

        fun onDoubleTapConfirmed(e: MotionEvent) {
            if (!isZooming) {
                if (scaleX == DEFAULT_RATE) {
                    zoom(DEFAULT_RATE,
                            MAX_SCALE_RATE, 0f,
                            (halfWidth - e.x) * (MAX_SCALE_RATE - 1), 0f,
                            (halfHeight - e.y) * (MAX_SCALE_RATE - 1))
                } else {
                    zoom(currentScale, DEFAULT_RATE, x, 0f, y, 0f)
                }
            }
        }

        override fun onLongPress(e: MotionEvent) {
            longTapListener?.invoke(e)
        }

    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val action = e.actionMasked
        val actionIndex = e.actionIndex

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                scrollPointerId = e.getPointerId(0)
                downX = (e.x + 0.5f).toInt()
                downY = (e.y + 0.5f).toInt()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                scrollPointerId = e.getPointerId(actionIndex)
                downX = (e.getX(actionIndex) + 0.5f).toInt()
                downY = (e.getY(actionIndex) + 0.5f).toInt()
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDoubleTapping && isQuickScaling) {
                    return true
                }

                val index = e.findPointerIndex(scrollPointerId)
                if (index < 0) {
                    return false
                }

                val x = (e.getX(index) + 0.5f).toInt()
                val y = (e.getY(index) + 0.5f).toInt()
                var dx = x - downX
                var dy = if (atFirstPosition || atLastPosition) y - downY else 0

                if (!isZoomDragging && currentScale > 1f) {
                    var startScroll = false

                    if (Math.abs(dx) > touchSlop) {
                        if (dx < 0) {
                            dx += touchSlop
                        } else {
                            dx -= touchSlop
                        }
                        startScroll = true
                    }
                    if (Math.abs(dy) > touchSlop) {
                        if (dy < 0) {
                            dy += touchSlop
                        } else {
                            dy -= touchSlop
                        }
                        startScroll = true
                    }

                    if (startScroll) {
                        isZoomDragging = true
                    }
                }

                if (isZoomDragging) {
                    zoomScrollBy(dx, dy)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isDoubleTapping && !isQuickScaling) {
                    listener.onDoubleTapConfirmed(e)
                }
                isZoomDragging = false
                isDoubleTapping = false
                isQuickScaling = false
            }
            MotionEvent.ACTION_CANCEL -> {
                isZoomDragging = false
                isDoubleTapping = false
                isQuickScaling = false
            }
        }

        detector.onTouchEvent(e)
        return super.onTouchEvent(e)
    }

    override fun onScrolled(dx: Int, dy: Int) {
        super.onScrolled(dx, dy)
        val layoutManager = layoutManager
        lastVisibleItemPosition =
                (layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
        firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    override fun onScrollStateChanged(state: Int) {
        super.onScrollStateChanged(state)
        val layoutManager = layoutManager
        val visibleItemCount = layoutManager.childCount
        val totalItemCount = layoutManager.itemCount
        atLastPosition = visibleItemCount > 0 && lastVisibleItemPosition == totalItemCount - 1
        atFirstPosition = firstVisibleItemPosition == 0
    }

    private fun getPositionX(positionX: Float): Float {
        val maxPositionX = halfWidth * (currentScale - 1)
        return positionX.coerceIn(-maxPositionX, maxPositionX)
    }

    private fun getPositionY(positionY: Float): Float {
        val maxPositionY = halfHeight * (currentScale - 1)
        return positionY.coerceIn(-maxPositionY, maxPositionY)
    }

    private fun zoom(
            fromRate: Float,
            toRate: Float,
            fromX: Float,
            toX: Float,
            fromY: Float,
            toY: Float
    ) {
        isZooming = true
        val animatorSet = AnimatorSet()
        val translationXAnimator = ValueAnimator.ofFloat(fromX, toX)
        translationXAnimator.addUpdateListener { animation -> x = animation.animatedValue as Float }

        val translationYAnimator = ValueAnimator.ofFloat(fromY, toY)
        translationYAnimator.addUpdateListener { animation -> y = animation.animatedValue as Float }

        val scaleAnimator = ValueAnimator.ofFloat(fromRate, toRate)
        scaleAnimator.addUpdateListener { animation ->
            setScaleRate(animation.animatedValue as Float)
        }
        animatorSet.playTogether(translationXAnimator, translationYAnimator, scaleAnimator)
        animatorSet.duration = ANIMATOR_DURATION_TIME.toLong()
        animatorSet.interpolator = DecelerateInterpolator()
        animatorSet.start()
        animatorSet.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {

            }

            override fun onAnimationEnd(animation: Animator) {
                isZooming = false
                currentScale = toRate
            }

            override fun onAnimationCancel(animation: Animator) {

            }

            override fun onAnimationRepeat(animation: Animator) {

            }
        })
    }

    fun zoomFling(velocityX: Int, velocityY: Int): Boolean {
        if (currentScale <= 1f) return false

        val distanceTimeFactor = 0.4f
        var newX: Float? = null
        var newY: Float? = null

        if (velocityX != 0) {
            val dx = (distanceTimeFactor * velocityX / 2)
            newX = getPositionX(x + dx)
        }
        if (velocityY != 0 && (atFirstPosition || atLastPosition)) {
            val dy = (distanceTimeFactor * velocityY / 2)
            newY = getPositionY(y + dy)
        }

        animate()
            .apply {
                newX?.let { x(it) }
                newY?.let { y(it) }
            }
            .setInterpolator(DecelerateInterpolator())
            .setDuration(400)
            .start()

        return true
    }

    private fun zoomScrollBy(dx: Int, dy: Int) {
        if (dx != 0) {
            x = getPositionX(x + dx)
        }
        if (dy != 0) {
            y = getPositionY(y + dy)
        }
    }

    private fun setScaleRate(rate: Float) {
        scaleX = rate
        scaleY = rate
    }

    fun onScale(scale: Float) {
        currentScale = scale
        setScaleRate(scale)

        if (scale != DEFAULT_RATE) {
            x = getPositionX(x)
            y = getPositionY(y)
        } else {
            x = 0f
            y = 0f
        }
    }

    fun onScaleBegin() {
        if (isDoubleTapping) {
            isQuickScaling = true
        }
    }

    fun onScaleEnd(scale: Float) {
        if (scaleX < DEFAULT_RATE) {
            zoom(scale, DEFAULT_RATE, x, 0f, y, 0f)
        }
    }

}

class WebtoonFrame(context: Context) : FrameLayout(context) {

    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())

    private val flingDetector = GestureDetector(context, FlingListener())

    private val recycler: WebtoonRecyclerView?
        get() = getChildAt(0) as? WebtoonRecyclerView

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        flingDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {

        private var currentScale = 1f

        override fun onScaleBegin(detector: ScaleGestureDetector?): Boolean {
            recycler?.onScaleBegin()
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor

            currentScale *= scaleFactor
            currentScale = currentScale.coerceIn(
                    DEFAULT_RATE,
                    MAX_SCALE_RATE)

            recycler?.onScale(currentScale)

            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            recycler?.onScaleEnd(currentScale)
        }
    }

    inner class FlingListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent?): Boolean {
            return true
        }

        override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent?,
                velocityX: Float,
                velocityY: Float
        ): Boolean {
            return recycler?.zoomFling(velocityX.toInt(), velocityY.toInt()) ?: false
        }
    }

}
