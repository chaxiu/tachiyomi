package eu.kanade.tachiyomi.ui.reader2.loader

import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader2.ReaderChapter
import eu.kanade.tachiyomi.ui.reader2.ReaderPage
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers

class ChapterLoader(
        private val downloadManager: DownloadManager,
        private val manga: Manga,
        private val source: Source
) {

    fun loadChapter(chapter: ReaderChapter): Observable<ReaderChapter> {
        return Observable.just(chapter)
            .observeOn(AndroidSchedulers.mainThread())
            .filter { it.state !is ReaderChapter.State.Loaded }
            .doOnNext { it.state = ReaderChapter.State.Loading }
            .observeOn(Schedulers.io())
            .flatMap {
                val loader = getPageLoader(it)
                chapter.pageLoader = loader

                loader.getPages().map { pages ->
                    pages.map { ReaderPage.from(it, chapter) }
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { pages ->
                if (pages.isEmpty()) {
                    throw Exception("Page list is empty")
                }

                chapter.state = ReaderChapter.State.Loaded(pages)

                // Now that the number of pages is known, fix the requested page if the last one
                // was requested.
                if (chapter.requestedPage == -1) {
                    chapter.requestedPage = pages.lastIndex
                }
            }
            .doOnError { chapter.state = ReaderChapter.State.Error(it) }
            .map { chapter }
            .onErrorReturn { chapter }
    }

    private fun getPageLoader(chapter: ReaderChapter): PageLoader {
        val isDownloaded = downloadManager.isChapterDownloaded(chapter.chapter, manga, true)
        return when {
            isDownloaded -> DownloadPageLoader(chapter, manga, source, downloadManager)
            source is HttpSource -> HttpPageLoader(chapter, source)
            source is LocalSource -> source.getFormat(chapter.chapter).let { format ->
                when (format) {
                    is LocalSource.Format.Directory -> DirectoryPageLoader(format.file)
                    is LocalSource.Format.Zip -> ZipPageLoader(format.file)
                    is LocalSource.Format.Rar -> RarPageLoader(format.file)
                    is LocalSource.Format.Epub -> EpubPageLoader(format.file)
                }
            }
            else -> error("Loader not implemented")
        }
    }

}
