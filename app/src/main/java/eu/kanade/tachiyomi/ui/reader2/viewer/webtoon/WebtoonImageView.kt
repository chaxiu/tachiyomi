package eu.kanade.tachiyomi.ui.reader2.viewer.webtoon

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView

class WebtoonImageView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : SubsamplingScaleImageView(context, attrs) {

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return false
    }

}
