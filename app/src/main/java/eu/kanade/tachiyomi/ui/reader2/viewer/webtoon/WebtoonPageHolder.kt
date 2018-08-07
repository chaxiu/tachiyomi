package eu.kanade.tachiyomi.ui.reader2.viewer.webtoon

import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.base.holder.BaseViewHolder
import eu.kanade.tachiyomi.ui.reader2.ReaderActivity
import eu.kanade.tachiyomi.ui.reader2.model.ReaderPage
import kotlinx.android.synthetic.main.reader_webtoon_item.*
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

/**
 * Holder for webtoon reader for a single page of a chapter.
 * All the elements from the layout file "reader_webtoon_item" are available in this class.
 *
 * @param view the inflated view for this holder.
 * @param adapter the adapter handling this holder.
 * @constructor creates a new webtoon holder.
 */
class WebtoonPageHolder(
        private val view: View,
        private val adapter: WebtoonAdapter
) : BaseViewHolder(view) {

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

    /**
     * Layout of decode error.
     */
    private var decodeErrorLayout: View? = null

    init {
        val config = adapter.viewer.config

        with(image_view) {
            setMaxTileSize(readerActivity.maxBitmapSize)
            setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE)
            setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_FIT_WIDTH)
            setMinimumDpi(90)
            setMinimumTileDpi(180)
            setBitmapDecoderClass(config.bitmapDecoder)
            setRegionDecoderClass(config.regionDecoder)
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

        progress_container.layoutParams = FrameLayout.LayoutParams(
                MATCH_PARENT, webtoonReader.screenHeight)

//        view.setOnTouchListener(adapter.touchListener)
//        retry_button.setOnTouchListener { _, event ->
//            if (event.action == MotionEvent.ACTION_UP) {
//                readerActivity.presenter.retryPage(page)
//            }
//            true
//        }
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
        decodeErrorLayout?.let {
            (view as ViewGroup).removeView(it)
            decodeErrorLayout = null
        }
        image_view.recycle()
        image_view.visibility = View.GONE
        progress_container.visibility = View.VISIBLE
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
            .subscribe { progress ->
                progress_text.text = if (progress > 0) {
                    view.context.getString(R.string.download_progress, progress)
                } else {
                    view.context.getString(R.string.downloading)
                }
            }

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
        webtoonReader.subscriptions.add(subscription)
    }

    /**
     * Removes a subscription from the list of subscriptions.
     */
    private fun removeSubscription(subscription: Subscription?) {
        subscription?.let { webtoonReader.subscriptions.remove(it) }
    }

    /**
     * Unsubscribes from the status subscription.
     */
    private fun unsubscribeStatus() {
        page?.setStatusSubject(null)
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

    /**
     * Called when the page is queued.
     */
    private fun setQueued() {
        progress_container.visibility = View.VISIBLE
        progress_text.visibility = View.INVISIBLE
        retry_container.visibility = View.GONE
        decodeErrorLayout?.let {
            (view as ViewGroup).removeView(it)
            decodeErrorLayout = null
        }
    }

    /**
     * Called when the page is loading.
     */
    private fun setLoading() {
        progress_container.visibility = View.VISIBLE
        progress_text.visibility = View.VISIBLE
        progress_text.setText(R.string.downloading)
    }

    /**
     * Called when the page is downloading
     */
    private fun setDownloading() {
        progress_container.visibility = View.VISIBLE
        progress_text.visibility = View.VISIBLE
    }

    /**
     * Called when the page is ready.
     */
    private fun setImage() {
        val stream = page?.stream ?: return

        progress_text.visibility = View.INVISIBLE
        image_view.visibility = View.VISIBLE
        image_view.setImage(ImageSource.provider(stream))
    }

    /**
     * Called when the page has an error.
     */
    private fun setError() {
        progress_container.visibility = View.GONE
        retry_container.visibility = View.VISIBLE
    }

    /**
     * Called when the image is decoded and going to be displayed.
     */
    private fun onImageDecoded() {
        progress_container.visibility = View.GONE
    }

    /**
     * Called when the image fails to decode.
     */
    private fun onImageDecodeError() {
//        progress_container.visibility = View.GONE
//
//        val page = page ?: return
//        if (decodeErrorLayout != null || !webtoonReader.isAdded) return
//
//        val layout = (view as ViewGroup).inflate(R.layout.reader_page_decode_error)
//        PageDecodeErrorLayout(layout, page, readerActivity.readerTheme, {
//            if (webtoonReader.isAdded) {
//                readerActivity.presenter.retryPage(page)
//            }
//        })
//        decodeErrorLayout = layout
//        view.addView(layout)
    }

    /**
     * Property to get the reader activity.
     */
    private val readerActivity: ReaderActivity
        get() = adapter.viewer.activity

    /**
     * Property to get the webtoon reader.
     */
    private val webtoonReader: WebtoonViewer
        get() = adapter.viewer
}
