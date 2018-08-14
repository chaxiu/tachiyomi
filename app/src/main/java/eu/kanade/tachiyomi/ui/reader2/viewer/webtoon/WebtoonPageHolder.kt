package eu.kanade.tachiyomi.ui.reader2.viewer.webtoon

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.support.v7.widget.AppCompatButton
import android.support.v7.widget.AppCompatImageView
import android.view.Gravity
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.glide.GlideApp
import eu.kanade.tachiyomi.data.glide.GlideInputStream
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.base.holder.BaseViewHolder
import eu.kanade.tachiyomi.ui.reader2.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader2.viewer.ReaderProgressBar
import eu.kanade.tachiyomi.util.ImageUtil
import eu.kanade.tachiyomi.util.dpToPx
import eu.kanade.tachiyomi.util.gone
import eu.kanade.tachiyomi.util.visible
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.util.concurrent.TimeUnit

/**
 * Holder for webtoon reader for a single page of a chapter.
 *
 * @param frame the root view for this holder.
 * @param viewer the webtoon viewer.
 * @constructor creates a new webtoon holder.
 */
class WebtoonPageHolder(
        private val frame: FrameLayout,
        private val viewer: WebtoonViewer
) : BaseViewHolder(frame) {

    private val context get() = frame.context

    private val progressBar = createProgressBar()

    private lateinit var progressContainer: ViewGroup

    private var subsamplingImageView: SubsamplingScaleImageView? = null

    private var imageView: ImageView? = null

    private var retryContainer: ViewGroup? = null

    private var decodeErrorLayout: ViewGroup? = null

    private val parentHeight
        get() = viewer.recycler.height

    /**
     * Page of a chapter.
     */
    private var page: ReaderPage? = null

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
        frame.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    }

    /**
     * Method called from [WebtoonAdapter.onBindViewHolder]. It updates the data for this
     * holder with the given page.
     *
     * @param page the page to bind.
     */
    fun bind(page: ReaderPage) {
        this.page = page
        observeStatus()
    }

    /**
     * Called when the view is recycled and added to the view pool.
     */
    fun recycle() {
        unsubscribeStatus()
        unsubscribeProgress()
        unsubscribeReadImageHeader()

        removeDecodeErrorLayout()
        subsamplingImageView?.recycle()
        subsamplingImageView?.gone()
        imageView?.let { GlideApp.with(frame).clear(it) }
        imageView?.gone()
        progressBar.setProgress(0)
    }

    /**
     * Observes the status of the page and notify the changes.
     *
     * @see processStatus
     */
    private fun observeStatus() {
        unsubscribeStatus()

        val page = page ?: return
        val loader = page.chapter2.pageLoader ?: return
        statusSubscription = loader.getPage(page)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { processStatus(it) }

        addSubscription(statusSubscription)
    }

    /**
     * Observes the progress of the page and updates view.
     */
    private fun observeProgress() {
        unsubscribeProgress()

        val page = page ?: return

        progressSubscription = Observable.interval(100, TimeUnit.MILLISECONDS)
            .map { page.progress }
            .distinctUntilChanged()
            .onBackpressureLatest()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { value -> progressBar.setProgress(value) }

        addSubscription(progressSubscription)
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
     * Adds a subscription to a list of subscriptions that will automatically unsubscribe when the
     * activity or the reader is destroyed.
     */
    private fun addSubscription(subscription: Subscription?) {
        viewer.subscriptions.add(subscription)
    }

    /**
     * Removes a subscription from the list of subscriptions.
     */
    private fun removeSubscription(subscription: Subscription?) {
        subscription?.let { viewer.subscriptions.remove(it) }
    }

    /**
     * Unsubscribes from the status subscription.
     */
    private fun unsubscribeStatus() {
        removeSubscription(statusSubscription)
        statusSubscription = null
    }

    /**
     * Unsubscribes from the progress subscription.
     */
    private fun unsubscribeProgress() {
        removeSubscription(progressSubscription)
        progressSubscription = null
    }

    private fun unsubscribeReadImageHeader() {
        removeSubscription(readImageHeaderSubscription)
        readImageHeaderSubscription = null
    }

    /**
     * Called when the page is queued.
     */
    private fun setQueued() {
        progressContainer.visible()
        progressBar.visible()
        retryContainer?.gone()
        removeDecodeErrorLayout()
    }

    /**
     * Called when the page is loading.
     */
    private fun setLoading() {
        progressContainer.visible()
        progressBar.visible()
        retryContainer?.gone()
        removeDecodeErrorLayout()
    }

    /**
     * Called when the page is downloading
     */
    private fun setDownloading() {
        progressContainer.visible()
        progressBar.visible()
        retryContainer?.gone()
        removeDecodeErrorLayout()
    }

    /**
     * Called when the page is ready.
     */
    private fun setImage() {
        progressContainer.visible()
        progressBar.visible()
        progressBar.completeAndFadeOut()
        retryContainer?.gone()
        removeDecodeErrorLayout()

        unsubscribeReadImageHeader()
        val streamFn = page?.stream ?: return

        readImageHeaderSubscription = Observable
            .fromCallable { ImageUtil.isAnimatedImage(streamFn) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { isAnimatedImage ->
                if (!isAnimatedImage) {
                    val subsamplingView = initSubsamplingImageView()
                    subsamplingView.visible()
                    subsamplingView.setImage(ImageSource.provider(streamFn))
                } else {
                    val imageView = initImageView()
                    imageView.visible()
                    imageView.setImage(GlideInputStream(streamFn))
                }
            }

        addSubscription(readImageHeaderSubscription)
    }

    /**
     * Called when the page has an error.
     */
    private fun setError() {
        progressContainer.gone()
        initRetryLayout().visible()
    }

    /**
     * Called when the image is decoded and going to be displayed.
     */
    private fun onImageDecoded() {
        progressContainer.gone()
    }

    /**
     * Called when the image fails to decode.
     */
    private fun onImageDecodeError() {
        progressContainer.gone()
        initDecodeErrorLayout().visible()
    }

    /**
     * Creates a new progress bar.
     */
    @SuppressLint("PrivateResource")
    private fun createProgressBar(): ReaderProgressBar {
        progressContainer = FrameLayout(context)
        frame.addView(progressContainer, MATCH_PARENT, parentHeight)

        val progress = ReaderProgressBar(context).apply {
            val size = 48.dpToPx
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, parentHeight/4, 0, 0)
            }
        }
        progressContainer.addView(progress)
        return progress
    }

    /**
     * Initializes a subsampling scale view.
     */
    private fun initSubsamplingImageView(): SubsamplingScaleImageView {
        if (subsamplingImageView != null) return subsamplingImageView!!

        val config = viewer.config

        subsamplingImageView = WebtoonImageView(context).apply {
            setMaxTileSize(viewer.activity.maxBitmapSize)
            setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
            setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH)
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
        frame.addView(subsamplingImageView, MATCH_PARENT, MATCH_PARENT)
        return subsamplingImageView!!
    }

    /**
     * Initializes an image view, used for GIFs.
     */
    private fun initImageView(): ImageView {
        if (imageView != null) return imageView!!

        imageView = AppCompatImageView(context).apply {
            adjustViewBounds = true
        }
        frame.addView(imageView, MATCH_PARENT, MATCH_PARENT)
        return imageView!!
    }

    /**
     * Initializes a button to retry pages.
     */
    private fun initRetryLayout(): ViewGroup {
        if (retryContainer != null) return retryContainer!!

        retryContainer = FrameLayout(context)
        frame.addView(retryContainer, MATCH_PARENT, parentHeight)

        AppCompatButton(context).apply {
            layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, parentHeight/4, 0, 0)
            }
            setText(R.string.action_retry)
            setOnClickListener {
                page?.let { it.chapter2.pageLoader?.retryPage(it) }
            }

            retryContainer!!.addView(this)
        }
        return retryContainer!!
    }

    /**
     * Initializes a decode error layout.
     */
    private fun initDecodeErrorLayout(): ViewGroup {
        if (decodeErrorLayout != null) return decodeErrorLayout!!

        val margins = 8.dpToPx

        val decodeLayout = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, parentHeight).apply {
                setMargins(0, parentHeight/6, 0, 0)
            }
            gravity = Gravity.CENTER_HORIZONTAL
            orientation = LinearLayout.VERTICAL
        }
        decodeErrorLayout = decodeLayout

        TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                setMargins(0, margins, 0, margins)
            }
            gravity = Gravity.CENTER
            setText(R.string.decode_image_error)

            decodeLayout.addView(this)
        }

        AppCompatButton(context).apply {
            layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                setMargins(0, margins, 0, margins)
            }
            setText(R.string.action_retry)
            setOnClickListener {
                page?.let { it.chapter2.pageLoader?.retryPage(it) }
            }

            decodeLayout.addView(this)
        }

        val imageUrl = page?.imageUrl
        if (imageUrl.orEmpty().startsWith("http")) {
            AppCompatButton(context).apply {
                layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                    setMargins(0, margins, 0, margins)
                }
                setText(R.string.action_open_in_browser)
                setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(imageUrl))
                    context.startActivity(intent)
                }

                decodeLayout.addView(this)
            }
        }

        frame.addView(decodeLayout)
        return decodeLayout
    }

    private fun removeDecodeErrorLayout() {
        val layout = decodeErrorLayout
        if (layout != null) {
            frame.removeView(layout)
            decodeErrorLayout = null
        }
    }

    private fun ImageView.setImage(stream: GlideInputStream) {
        GlideApp.with(this)
            .load(stream)
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>?,
                        isFirstResource: Boolean
                ): Boolean {
                    onImageDecodeError()
                    return false
                }

                override fun onResourceReady(
                        resource: Drawable?,
                        model: Any?,
                        target: Target<Drawable>?,
                        dataSource: DataSource?,
                        isFirstResource: Boolean
                ): Boolean {
                    onImageDecoded()
                    return false
                }
            })
            .into(this)
    }

}
