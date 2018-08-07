package eu.kanade.tachiyomi.ui.reader2.viewer.pager

import android.content.Context
import android.support.v4.view.ViewPager
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View

open class Pager(context: Context) : ViewPager(context) {

    var tapListener: ((MotionEvent) -> Unit)? = null
    var longTapListener: ((MotionEvent) -> Unit)? = null

    private var isTapListenerEnabled = true

    private val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
            tapListener?.invoke(event)
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            longTapListener?.invoke(e)
        }
    }

    private val gestureDetector = GestureDetector(context, gestureListener)

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val handled = super.dispatchTouchEvent(ev)
        if (isTapListenerEnabled) {
            gestureDetector.onTouchEvent(ev)
        }
        return handled
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return try {
            super.onInterceptTouchEvent(ev)
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        return try {
            super.onTouchEvent(ev)
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    override fun executeKeyEvent(event: KeyEvent): Boolean {
        // Disable viewpager's default key event handling
        return false
    }

    fun setTapListenerEnabled(enabled: Boolean) {
        isTapListenerEnabled = enabled
    }

}

class VerticalPager(context: Context) : Pager(context) {

    init {
        // The majority of the magic happens here
        setPageTransformer(false,
                VerticalPageTransformer())
        overScrollMode = OVER_SCROLL_NEVER

        val cls = ViewPager::class.java
        try {
            val distanceField = cls.getDeclaredField("mFlingDistance")
            distanceField.isAccessible = true
            distanceField.setInt(this, distanceField.getInt(this) / 40)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Swaps the X and Y coordinates of your touch event.
     */
    private fun swapXY(ev: MotionEvent): MotionEvent {
        val width = width.toFloat()
        val height = height.toFloat()

        val y = ev.y
        val x = ev.x

        val newX = y / height * width
        val newY = x / width * height

        ev.setLocation(newX, newY)

        return ev
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val intercepted = super.onInterceptTouchEvent(swapXY(ev))
        swapXY(ev) // return touch coordinates to original reference frame for any child views
        return intercepted
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        return super.onTouchEvent(swapXY(ev))
    }

    private class VerticalPageTransformer : ViewPager.PageTransformer {
        override fun transformPage(view: View, position: Float) {
            val pageWidth = view.width
            val pageHeight = view.height
            if (position < -1) {
                view.alpha = 0f
            } else if (position <= 1) {
                view.alpha = 1f
                view.translationX = pageWidth * -position
                val yPosition = position * pageHeight
                view.translationY = yPosition
            } else {
                view.alpha = 0f
            }
        }

    }
}
