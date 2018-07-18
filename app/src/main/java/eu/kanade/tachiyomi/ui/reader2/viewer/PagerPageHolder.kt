package eu.kanade.tachiyomi.ui.reader2.viewer

import android.annotation.SuppressLint
import android.graphics.PointF
import android.support.v7.widget.AppCompatButton
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.github.chrisbanes.photoview.PhotoView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.glide.GlideApp
import eu.kanade.tachiyomi.data.glide.GlideInputStream
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerReader
import eu.kanade.tachiyomi.ui.reader2.ReaderPage
import eu.kanade.tachiyomi.util.DiskUtil
import eu.kanade.tachiyomi.util.dpToPx
import eu.kanade.tachiyomi.util.gone
import eu.kanade.tachiyomi.util.visible
import eu.kanade.tachiyomi.widget.ViewPagerAdapter
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.util.concurrent.TimeUnit

@SuppressLint("ViewConstructor")
class PagerPageHolder(
        val viewer: PagerViewer,
        val page: ReaderPage
) : FrameLayout(viewer.activity), ViewPagerAdapter.PositionableView {

    override val item
        get() = page

    private val progressBar = createProgressBar()

    private var subsamplingImageView: SubsamplingScaleImageView? = null

    private var imageView: PhotoView? = null

    private var retryButton: AppCompatButton? = null

    /**
     * Subscription for status changes of the page.
     */
    private var statusSubscription: Subscription? = null

    /**
     * Subscription for progress changes of the page.
     */
    private var progressSubscription: Subscription? = null

    private var readImageHeaderSubscription: Subscription? = null

    init {
        addView(progressBar)
        observeStatus()
    }


    @SuppressLint("ClickableViewAccessibility")
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        unsubscribeProgress()
        unsubscribeStatus()
        unsubscribeReadImageHeader()
        subsamplingImageView?.setOnImageEventListener(null)
    }

    /**
     * Observes the status of the page and notify the changes.
     *
     * @see processStatus
     */
    private fun observeStatus() {
        statusSubscription?.unsubscribe()

        val loader = page.chapter2.pageLoader ?: return
        statusSubscription = loader.getPage(page)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { processStatus(it) }
    }

    /**
     * Observes the progress of the page and updates view.
     */
    private fun observeProgress() {
        progressSubscription?.unsubscribe()

        progressSubscription = Observable.interval(32, TimeUnit.MILLISECONDS) // 30fps
            .map { page.progress }
            .distinctUntilChanged()
            .onBackpressureLatest()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { value -> progressBar.setProgress(value) }
    }

    /**
     * Called when the status of the page changes.
     *
     * @param status the new status of the page.
     */
    private fun processStatus(status: Int) {
        when (status) {
            Page.QUEUE -> setQueued()
            Page.LOAD_PAGE -> setLoading()
            Page.DOWNLOAD_IMAGE -> {
                observeProgress()
                setDownloading()
            }
            Page.READY -> {
                setImage()
                unsubscribeProgress()
            }
            Page.ERROR -> {
                setError()
                unsubscribeProgress()
            }
        }
    }

    /**
     * Unsubscribes from the status subscription.
     */
    private fun unsubscribeStatus() {
        page.setStatusSubject(null)
        statusSubscription?.unsubscribe()
        statusSubscription = null
    }

    /**
     * Unsubscribes from the progress subscription.
     */
    private fun unsubscribeProgress() {
        progressSubscription?.unsubscribe()
        progressSubscription = null
    }

    private fun unsubscribeReadImageHeader() {
        readImageHeaderSubscription?.unsubscribe()
        readImageHeaderSubscription = null
    }

    /**
     * Called when the page is queued.
     */
    private fun setQueued() {
        progressBar.visible()
        retryButton?.gone()
//        decodeErrorLayout?.let {
//            removeView(it)
//            decodeErrorLayout = null
//        }
    }

    /**
     * Called when the page is loading.
     */
    private fun setLoading() {
        progressBar.visible()
    }

    /**
     * Called when the page is downloading.
     */
    private fun setDownloading() {
        progressBar.visible()
    }

    /**
     * Called when the page is ready.
     */
    private fun setImage() {
        progressBar.visible()
        progressBar.completeAndFadeOut()

        unsubscribeReadImageHeader()
        val streamFn = page.stream ?: return

        readImageHeaderSubscription = Observable
            .fromCallable { DiskUtil.isAnimatedImage(streamFn) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ isAnimatedImage ->
                if (!isAnimatedImage) {
                    initSubsamplingImageView().setImage(ImageSource.provider(streamFn))
                } else {
                    val iv = initImageView()
                    GlideApp.with(this)
                        .load(GlideInputStream(streamFn))
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .into(iv)
                }
            })
    }

    /**
     * Called when the page has an error.
     */
    private fun setError() {
        progressBar.gone()

        initRetryButton()
        retryButton?.visible()
    }

    /**
     * Called when the image is decoded and going to be displayed.
     */
    private fun onImageDecoded() {
        progressBar.gone()

        subsamplingImageView?.run {
            when (viewer.config.imageZoomType) {
                PagerReader.ALIGN_LEFT -> setScaleAndCenter(scale, PointF(0f, 0f))
                PagerReader.ALIGN_RIGHT -> setScaleAndCenter(scale, PointF(sWidth.toFloat(), 0f))
                PagerReader.ALIGN_CENTER -> setScaleAndCenter(scale, center.apply { y = 0f })
            }
        }
    }

    /**
     * Called when an image fails to decode.
     */
    private fun onImageDecodeError() {
        progressBar.gone()

//        if (decodeErrorLayout != null) return
//
//        val activity = reader.activity
//
//        val layout = inflate(R.layout.reader_page_decode_error)
//        PageDecodeErrorLayout(layout, page, activity.readerTheme, {
//            activity.presenter.retryPage(page)
//        })
//        decodeErrorLayout = layout
//        addView(layout)
    }

    private fun initSubsamplingImageView(): SubsamplingScaleImageView {
        if (subsamplingImageView != null) return subsamplingImageView!!

        val config = viewer.config

        subsamplingImageView = SubsamplingScaleImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            setMaxTileSize(viewer.activity.maxBitmapSize)
            setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_FIXED)
            setDoubleTapZoomDuration(config.doubleTapAnimDuration)
            setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
            setMinimumScaleType(config.imageScaleType)
            setMinimumDpi(90)
            setMinimumTileDpi(180)
            setRegionDecoderClass(config.regionDecoder)
            setBitmapDecoderClass(config.bitmapDecoder)
            setCropBorders(config.imageCropBorders)
            setOnImageEventListener(object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                override fun onReady() {
                    onImageDecoded()
                }

                override fun onImageLoadError(e: Exception) {
                    onImageDecodeError()
                }
            })
        }
        addView(subsamplingImageView)
        return subsamplingImageView!!
    }

    private fun initImageView(): ImageView {
        if (imageView != null) return imageView!!

        val config = viewer.config

        imageView = PhotoView(context).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            adjustViewBounds = true
            setZoomTransitionDuration(config.doubleTapAnimDuration)
            minimumScale = 1f
        }
        addView(imageView)
        return imageView!!
    }

    private fun initRetryButton(): AppCompatButton {
        if (retryButton != null) return retryButton!!

        retryButton = AppCompatButton(context).apply {
            layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            setOnClickListener {
                page.chapter2.pageLoader?.retryPage(page)
            }
            viewer.interceptPagerTapListenerOnClick(this)
            setText(R.string.action_retry)
        }
        addView(retryButton)
        return retryButton!!
    }

    @SuppressLint("PrivateResource")
    private fun createProgressBar(): ReaderProgressBar {
        return ReaderProgressBar(context, null).apply {

            val size = 48.dpToPx
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER
            }
        }
    }

}
