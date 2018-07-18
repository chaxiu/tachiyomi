package eu.kanade.tachiyomi.ui.reader2.loader

import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader2.ReaderChapter
import eu.kanade.tachiyomi.ui.reader2.ReaderPage
import rx.Observable

class DownloadPageLoader(
        private val chapter: ReaderChapter,
        private val manga: Manga,
        private val source: Source,
        private val downloadManager: DownloadManager
) : PageLoader() {

    override fun getPages(): Observable<List<Page>> {
        return downloadManager.buildPageList(source, manga, chapter.chapter)
    }

    override fun getPage(page: ReaderPage): Observable<Int> {
        return Observable.just(Page.READY) // TODO maybe check if file still exists?
    }

}
