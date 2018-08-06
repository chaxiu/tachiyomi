package eu.kanade.tachiyomi.ui.reader2.viewer

import android.os.Build
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.WebtoonLayoutManager
import android.util.DisplayMetrics
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import eu.kanade.tachiyomi.ui.reader2.ReaderActivity
import eu.kanade.tachiyomi.ui.reader2.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader2.model.ViewerChapters
import rx.subscriptions.CompositeSubscription
import timber.log.Timber

class WebtoonViewer(activity: ReaderActivity) : BaseViewer(activity) {

    private var scrollDistance = 0

    private val adapter = WebtoonAdapter(this)

    private val layoutManager = WebtoonLayoutManager(activity).apply {
        val screenHeight = activity.resources.displayMetrics.heightPixels
        extraLayoutSpace = screenHeight / 2
        scrollDistance = screenHeight * 3 / 4
    }

    private var currentPage: Any? = null

    private val frame = WebtoonFrame(activity)

    private val recycler = WebtoonRecyclerView(activity)

    val subscriptions = CompositeSubscription()

    val screenHeight by lazy {
        val display = activity.windowManager.defaultDisplay

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            val metrics = DisplayMetrics()
            display.getRealMetrics(metrics)
            metrics.heightPixels
        } else {
            val field = Display::class.java.getMethod("getRawHeight")
            field.invoke(display) as Int
        }
    }

    init {
        recycler.visibility = View.GONE // Don't let the recycler layout yet
        recycler.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        recycler.itemAnimator = null
        recycler.layoutManager = layoutManager
        recycler.adapter = adapter
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
                val index = layoutManager.findLastEndVisibleItemPosition()
                val item = adapter.items.getOrNull(index)
                if (item != null && currentPage != item) {
                    currentPage = item
                    when (item) {
                        is ReaderPage -> onPageSelected(item)
                        is ChapterTransition -> onTransitionSelected(item)
                    }
                }

                if (dy < 0) {
                    val firstIndex = layoutManager.findFirstVisibleItemPosition()
                    val firstItem = adapter.items.getOrNull(firstIndex)
                    if (firstItem is ChapterTransition.Prev) {
                        activity.requestPreloadPreviousChapter()
                    }
                }
            }
        })
        recycler.tapListener = { event ->
            val positionX = event.x
            when {
                positionX < recycler.width * 0.33 -> moveUp()
                positionX > recycler.width * 0.66 -> moveDown()
                else -> activity.toggleMenu()
            }
        }
        recycler.longTapListener = { event ->
            val child = recycler.findChildViewUnder(event.x, event.y)
            val position = recycler.getChildAdapterPosition(child)
            val item = adapter.items.getOrNull(position)
            if (item is ReaderPage) {
                activity.onLongTap(item)
            }
        }

        frame.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        frame.addView(recycler)
    }

    override fun getView(): View {
        return frame
    }

    override fun destroy() {
        super.destroy()
        subscriptions.unsubscribe()
    }

    private fun onPageSelected(page: ReaderPage) {
        val pages = page.chapter2.pages!! // Won't be null because it's the loaded chapter
        Timber.w("onPageSelected: ${page.number}/${pages.size}")
        activity.onPageSelected(page)

        if (page === pages.last()) {
            Timber.w("Request preload next chapter because we're at the last page")
            activity.requestPreloadNextChapter()
        }
    }

    private fun onTransitionSelected(transition: ChapterTransition) {
        Timber.w("onTransitionSelected: $transition")
        if (transition is ChapterTransition.Prev) {
            Timber.w("Request preload previous chapter because we're on the transition")
            activity.requestPreloadPreviousChapter()
        }
    }

    override fun setChapters(chapters: ViewerChapters) {
        Timber.w("setChapters")
        adapter.setChapters(chapters)

        if (recycler.visibility == View.GONE) {
            Timber.w("Recycler first layout")
            val pages = chapters.currChapter.pages ?: return
            moveToPage(pages[chapters.currChapter.requestedPage])
            recycler.visibility = View.VISIBLE
        }
    }

    override fun moveToPage(page: ReaderPage) {
        Timber.w("moveToPage")
        val position = adapter.items.indexOf(page)
        if (position != -1) {
            recycler.scrollToPosition(position)
        } else {
            Timber.w("Page $page not found in adapter")
        }
    }

    override fun moveLeft() {
        recycler.smoothScrollBy(0, -scrollDistance)
    }

    override fun moveRight() {
        recycler.smoothScrollBy(0, scrollDistance)
    }

    override fun moveUp() {
        moveLeft()
    }

    override fun moveDown() {
        moveRight()
    }

    override fun moveToNextChapter() {
        // TODO
    }

    override fun moveToPrevChapter() {
        // TODO
    }

}
