package eu.kanade.tachiyomi.ui.reader2

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.ui.base.activity.BaseRxActivity
import eu.kanade.tachiyomi.ui.reader2.viewer.*
import eu.kanade.tachiyomi.util.GLUtil
import eu.kanade.tachiyomi.util.visibleIf
import eu.kanade.tachiyomi.widget.SimpleSeekBarListener
import kotlinx.android.synthetic.main.reader_activity2.*
import nucleus.factory.RequiresPresenter

@RequiresPresenter(ReaderPresenter::class)
class ReaderActivity : BaseRxActivity<ReaderPresenter>() {

    private var viewer: BaseViewer? = null

    var maxBitmapSize = 0
        private set

    companion object {
        fun newIntent(context: Context, manga: Manga, chapter: Chapter): Intent {
            val intent = Intent(context, ReaderActivity::class.java)
            intent.putExtra("manga", manga)
            intent.putExtra("chapter", chapter.id)
            return intent
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.reader_activity2)

        if (presenter.needsInit()) {
            val manga = intent.extras.getSerializable("manga") as? Manga
            val chapter = intent.extras.getLong("chapter", -1)

            if (manga == null || chapter == -1L) {
                finish()
                return
            }

            presenter.init(manga, chapter)
        }

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        maxBitmapSize = GLUtil.getMaxTextureSize()

        page_seekbar.setOnSeekBarChangeListener(object : SimpleSeekBarListener() {
            override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) {
                val viewer = viewer
                if (viewer != null && fromUser) {
                    val pageIndex = if (viewer is R2LPagerViewer) seekBar.max - value else value
                    moveToPageIndex(pageIndex)
                }
            }
        })
        left_chapter.setOnClickListener {
            if (viewer != null) {
                if (viewer is R2LPagerViewer)
                    moveToNextChapter()
                else
                    moveToPrevChapter()
            }
        }
        right_chapter.setOnClickListener {
            if (viewer != null) {
                if (viewer is R2LPagerViewer)
                    moveToPrevChapter()
                else
                    moveToNextChapter()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewer?.destroy()
    }

    /**
     * Methods called from presenter or this activity.
     */

    fun setManga(manga: Manga) {
        val mangaViewer = if (manga.viewer == 0) presenter.getDefaultViewer() else manga.viewer

        val currViewer = when (mangaViewer) {
            2 -> R2LPagerViewer(this)
            3 -> VerticalPagerViewer(this)
            4 -> WebtoonViewer(this)
            else -> L2RPagerViewer(this)
        }

        viewer = currViewer
        viewer_container.addView(currViewer.getView())

        toolbar.title = manga.title
    }

    fun setChapters(viewerChapters: ViewerChapters) {
        viewer?.setChapters(viewerChapters)
        toolbar.subtitle = viewerChapters.currChapter.chapter.name
    }

    fun moveToPageIndex(index: Int) {
        val currentChapter = presenter.getCurrentChapter() ?: return
        val page = currentChapter.pages?.getOrNull(index) ?: return
        moveToPage(page)
    }

    fun moveToPage(page: ReaderPage) {
        viewer?.moveToPage(page)
    }

    private fun moveToNextChapter() {
        viewer?.moveToNextChapter()
    }

    private fun moveToPrevChapter() {
        viewer?.moveToPrevChapter()
    }

    /**
     * Methods called from the viewer.
     */

    @SuppressLint("SetTextI18n")
    fun onPageSelected(page: ReaderPage) {
        presenter.onPageSelected(page)
        val pages = page.chapter2.pages ?: return

        // Set bottom page number
        page_number.text = "${page.number}/${pages.size}"

        // Set seekbar page number
        if (viewer !is R2LPagerViewer) {
            left_page_text.text = "${page.number}"
            right_page_text.text = "${pages.size}"
        } else {
            right_page_text.text = "${page.number}"
            left_page_text.text = "${pages.size}"
        }

        // Set seekbar progress
        page_seekbar.max = pages.lastIndex
        page_seekbar.progress = if (viewer !is R2LPagerViewer) {
            page.index
        } else {
            pages.lastIndex - page.index
        }
    }

    fun requestPreloadNextChapter() {
        presenter.preloadNextChapter()
    }

    fun requestPreloadPreviousChapter() {
        presenter.preloadPreviousChapter()
    }

    fun toggleMenu() {
        reader_menu.visibleIf { reader_menu.visibility != View.VISIBLE }
    }

}
