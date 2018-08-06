package eu.kanade.tachiyomi.ui.reader2.loader

import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.LocalSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader2.model.ReaderChapter
import rx.Completable
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import timber.log.Timber

class ChapterLoader(
        private val downloadManager: DownloadManager,
        private val manga: Manga,
        private val source: Source
) {

    fun loadChapter(chapter: ReaderChapter): Completable {
        if (chapter.state is ReaderChapter.State.Loaded) {
            return Completable.complete()
        }

        return Observable.just(chapter)
            .observeOn(Schedulers.io())
            .flatMap {
                Timber.w("Loading pages for ${chapter.chapter.name}")
                val loader = getPageLoader(it)
                chapter.pageLoader = loader

                loader.getPages().doOnNext { pages ->
                    pages.forEach { it.chapter2 = chapter }
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext { pages ->
                if (pages.isEmpty()) {
                    throw Exception("Page list is empty")
                }

                chapter.state = ReaderChapter.State.Loaded(pages)

                // If the chapter is partially read, set the starting page to the last the user read
                // otherwise use the requested page.
                if (!chapter.chapter.read) {
                    chapter.requestedPage = chapter.chapter.last_page_read
                }

                // Now that the number of pages is known, fix the requested page if the last one
                // was requested.
                // TODO probably not needed anymore
//                if (chapter.requestedPage == -1) {
//                    chapter.requestedPage = pages.lastIndex
//                }
            }
            .toCompletable()
            .doOnError { chapter.state = ReaderChapter.State.Error(it) }
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
