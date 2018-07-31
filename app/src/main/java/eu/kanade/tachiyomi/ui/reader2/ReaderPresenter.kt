package eu.kanade.tachiyomi.ui.reader2

import android.os.Bundle
import com.jakewharton.rxrelay.BehaviorRelay
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.ui.reader2.loader.ChapterLoader
import rx.Completable
import rx.Observable
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

    private var chapterId = -1L

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

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        if (savedState != null) {
            chapterId = savedState.getLong(::chapterId.name, -1)
        }
    }

    override fun onSave(state: Bundle) {
        super.onSave(state)
        val currentChapter = viewerChaptersRelay.value?.currChapter
        if (currentChapter != null) {
            currentChapter.requestedPage = currentChapter.chapter.last_page_read
            state.putLong(::chapterId.name, currentChapter.chapter.id!!)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val currentChapters = viewerChaptersRelay.value
        if (currentChapters != null) {
            currentChapters.unref()
            saveChapterProgress(currentChapters.currChapter)
        }
    }

    fun needsInit(): Boolean {
        return manga == null
    }

    fun init(manga: Manga, initialChapterId: Long) {
        if (!needsInit()) return

        this.manga = manga
        if (chapterId == -1L) chapterId = initialChapterId

        val source = sourceManager.getOrStub(manga.source)
        loader = ChapterLoader(downloadManager, manga, source)

        Observable.just(manga).subscribeLatestCache(ReaderActivity::setManga)
        viewerChaptersRelay.subscribeLatestCache(ReaderActivity::setChapters)

        Completable.fromCallable { load(chapterList.first { chapterId == it.chapter.id }) }
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

        activeChapterSubscription?.unsubscribe()
        activeChapterSubscription = getLoadObservable(loader, chapter)
            .subscribeFirst({ view, _ ->
                view.moveToPageIndex(0)
            })
    }

    fun onPageSelected(page: ReaderPage) {
        val currentChapters = viewerChaptersRelay.value ?: return

        val selectedChapter = page.chapter2

        // Save last page read and mark as read if needed
        selectedChapter.chapter.last_page_read = page.index
        if (selectedChapter.pages?.lastIndex == page.index) {
            selectedChapter.chapter.read = true
        }

        if (selectedChapter != currentChapters.currChapter) {
            Timber.w("Setting ${selectedChapter.chapter.url} as active")
            onChapterChanged(currentChapters.currChapter, selectedChapter)
            load(selectedChapter)
        }
    }

    private fun onChapterChanged(fromChapter: ReaderChapter, toChapter: ReaderChapter) {
        saveChapterProgress(fromChapter)
    }

    private fun saveChapterProgress(chapter: ReaderChapter) {
        db.updateChapterProgress(chapter.chapter).asRxCompletable()
            .onErrorComplete()
            .subscribeOn(Schedulers.io())
            .subscribe()
    }

    /**
     * Called when the application is going to background
     */
    fun saveCurrentProgress() {
        val currentChapters = viewerChaptersRelay.value ?: return
        saveChapterProgress(currentChapters.currChapter)
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
