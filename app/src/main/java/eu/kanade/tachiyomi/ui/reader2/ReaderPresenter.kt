package eu.kanade.tachiyomi.ui.reader2

import com.jakewharton.rxrelay.BehaviorRelay
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.ui.reader2.loader.ChapterLoader
import rx.Observable
import rx.Single
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ReaderPresenter(
        private val db: DatabaseHelper = Injekt.get(),
        private val sourceManager: SourceManager = Injekt.get(),
        private val downloadManager: DownloadManager = Injekt.get(),
        private val preferences: PreferencesHelper = Injekt.get()
) : BasePresenter<ReaderActivity>() {

    private var manga: Manga? = null

    private var initialChapterId: Long? = null

    private var loader: ChapterLoader? = null

    private var activeChapterSubscription: Subscription? = null

    private val viewerChaptersRelay = BehaviorRelay.create<ViewerChapters>()

    /**
     * Chapter list for the active manga. It's retrieved lazily and should be accessed for the first
     * time in a background thread to avoid blocking the UI.
     */
    private val chapterList by lazy {
        val manga = manga!!
        val dbChapters = db.getChapters(manga).executeAsBlocking().map(::ReaderChapter)

        val sortFunction: (Chapter, Chapter) -> Int = when (manga.sorting) {
            Manga.SORTING_SOURCE -> { c1, c2 -> c2.source_order.compareTo(c1.source_order) }
            Manga.SORTING_NUMBER -> { c1, c2 -> c1.chapter_number.compareTo(c2.chapter_number) }
            else -> throw NotImplementedError("Unknown sorting method")
        }

        dbChapters.sortedWith(Comparator { c1, c2 -> sortFunction(c1.chapter, c2.chapter) })
    }

    override fun onDestroy() {
        super.onDestroy()
        viewerChaptersRelay.value?.unref()
    }

    fun needsInit(): Boolean {
        return manga == null || initialChapterId == null
    }

    fun init(manga: Manga, chapterId: Long) {
        if (this.manga?.id == manga.id && initialChapterId == chapterId) return

        this.manga = manga
        this.initialChapterId = chapterId
        val source = sourceManager.getOrStub(manga.source)
        loader = ChapterLoader(downloadManager, manga, source)

        Observable.just(manga).subscribeLatestCache(ReaderActivity::setManga)
        viewerChaptersRelay.subscribeLatestCache(ReaderActivity::setChapters)

        Single.fromCallable { load(chapterList.first { chapterId == it.chapter.id }) }
            .toCompletable()
            .onErrorComplete()
            .subscribeOn(Schedulers.io())
            .subscribe()
            .also(::add)
    }

    private fun preload(chapter: ReaderChapter) {
        Timber.w("Preloading ${chapter.chapter.url}")

        val loader = loader ?: return

        loader.loadChapter(chapter)
            .observeOn(AndroidSchedulers.mainThread())
            // Update current chapters whenever a chapter is preloaded
            .doOnNext { viewerChaptersRelay.value?.let(viewerChaptersRelay::call) }
            .subscribe()
            .also(::add)
    }

    private fun getLoadObservable(
            loader: ChapterLoader,
            chapter: ReaderChapter
    ): Observable<ViewerChapters> {
        return loader.loadChapter(chapter)
            .switchIfEmpty(Observable.just(chapter))
            .map {
                val chapterPos = chapterList.indexOf(chapter)

                ViewerChapters(chapter,
                        chapterList.getOrNull(chapterPos - 1),
                        chapterList.getOrNull(chapterPos + 1))
            }
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { newChapters ->
                val oldChapters = viewerChaptersRelay.value

                // Add new references first to avoid unnecessary recycling
                newChapters.ref()
                oldChapters?.unref()

                viewerChaptersRelay.call(newChapters)
            }
    }

    private fun load(chapter: ReaderChapter) {
        Timber.w("Loading ${chapter.chapter.url}")

        val loader = loader ?: return

        activeChapterSubscription?.unsubscribe()
        activeChapterSubscription = getLoadObservable(loader, chapter)
            .subscribe()
            .also(::add)
    }

    private fun loadAdjacent(chapter: ReaderChapter) {
        Timber.w("Loading adjacent ${chapter.chapter.url}")

        val loader = loader ?: return
        var initialPageSet = false

        activeChapterSubscription?.unsubscribe()
        activeChapterSubscription = getLoadObservable(loader, chapter)
            .subscribeFirst({ view, chapters ->
                view.moveToPageIndex(0)
            })
    }

    fun onPageSelected(page: ReaderPage) {
        val currentChapters = viewerChaptersRelay.value ?: return

        if (page.chapter2 != currentChapters.currChapter) {
            Timber.w("Setting ${page.chapter2.chapter.url} as active")
            load(page.chapter2)
        }
    }

    fun preloadNextChapter() {
        val nextChapter = viewerChaptersRelay.value?.nextChapter ?: return
        preload(nextChapter)
    }

    fun preloadPreviousChapter() {
        val prevChapter = viewerChaptersRelay.value?.prevChapter ?: return
        preload(prevChapter)
    }

    fun loadNextChapter(): Boolean {
        val nextChapter = viewerChaptersRelay.value?.nextChapter ?: return false
        loadAdjacent(nextChapter)
        return true
    }

    fun loadPreviousChapter(): Boolean {
        val prevChapter = viewerChaptersRelay.value?.prevChapter ?: return false
        loadAdjacent(prevChapter)
        return true
    }

    fun getDefaultViewer(): Int {
        return preferences.defaultViewer()
    }

    fun getCurrentChapter(): ReaderChapter? {
        return viewerChaptersRelay.value?.currChapter
    }

}
