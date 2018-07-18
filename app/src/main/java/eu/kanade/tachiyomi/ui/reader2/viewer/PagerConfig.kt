package eu.kanade.tachiyomi.ui.reader2.viewer

import com.davemorrissey.labs.subscaleview.decoder.*
import com.f2prateek.rx.preferences.Preference
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.ui.reader.viewer.base.BaseReader
import eu.kanade.tachiyomi.util.addTo
import rx.subscriptions.CompositeSubscription
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class PagerConfig(preferences: PreferencesHelper = Injekt.get()) {

    private val subscriptions = CompositeSubscription()

    var imagePropertyChangedListener: (() -> Unit)? = null

    var tappingEnabled = true
        private set

    var usePageTransitions = false
        private set

    var imageScaleType = 1
        private set

    var imageZoomType = 1
        private set

    var imageCropBorders = false
        private set

    var doubleTapAnimDuration = 500
        private set

    var bitmapDecoder: Class<out ImageDecoder> = IImageDecoder::class.java
        private set

    var regionDecoder: Class<out ImageRegionDecoder> = IImageRegionDecoder::class.java
        private set

    init {
        preferences.readWithTapping()
            .register({ tappingEnabled = it })

        preferences.pageTransitions()
            .register({ usePageTransitions = it })

        preferences.imageScaleType()
            .register({ imageScaleType = it }, { imagePropertyChangedListener?.invoke() })

        preferences.zoomStart()
            .register({ imageZoomType = it }, { imagePropertyChangedListener?.invoke() })

        preferences.cropBorders()
            .register({ imageCropBorders = it }, { imagePropertyChangedListener?.invoke() })

        preferences.doubleTapAnimSpeed()
            .register({ doubleTapAnimDuration = it })

        preferences.imageDecoder()
            .register({ decoderFromPreference(it) })
    }

    fun unsubscribe() {
        subscriptions.unsubscribe()
    }

    private fun <T> Preference<T>.register(
            valueAssignment: (T) -> Unit,
            onChanged: (T) -> Unit = {}
    ) {
        asObservable()
            .doOnNext(valueAssignment)
            .skip(1)
            .distinctUntilChanged()
            .doOnNext(onChanged)
            .subscribe()
            .addTo(subscriptions)
    }

    private fun decoderFromPreference(value: Int) {
        when (value) {
            BaseReader.IMAGE_DECODER -> {
                bitmapDecoder = IImageDecoder::class.java
                regionDecoder = IImageRegionDecoder::class.java
            }
            BaseReader.SKIA_DECODER -> {
                bitmapDecoder = SkiaImageDecoder::class.java
                regionDecoder = SkiaImageRegionDecoder::class.java
            }
        }
    }

}
