package eu.kanade.tachiyomi.ui.reader2.loader

import android.support.annotation.CallSuper
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader2.ReaderPage
import rx.Observable

abstract class PageLoader {

    var isRecycled = false
        private set

    @CallSuper
    open fun recycle() {
        isRecycled = true
    }

    abstract fun getPages(): Observable<List<Page>>

    abstract fun getPage(page: ReaderPage): Observable<Int>

    open fun retryPage(page: ReaderPage) {}

}
