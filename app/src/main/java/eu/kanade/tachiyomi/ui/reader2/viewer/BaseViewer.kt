package eu.kanade.tachiyomi.ui.reader2.viewer

import android.view.View
import eu.kanade.tachiyomi.ui.reader2.ReaderActivity
import eu.kanade.tachiyomi.ui.reader2.ReaderPage
import eu.kanade.tachiyomi.ui.reader2.ViewerChapters

abstract class BaseViewer(val activity: ReaderActivity) {

    abstract fun getView(): View

    open fun destroy() {}

    abstract fun setChapters(chapters: ViewerChapters)

    abstract fun moveToPage(page: ReaderPage)

    abstract fun moveLeft()

    abstract fun moveRight()

    abstract fun moveUp()

    abstract fun moveDown()

    abstract fun moveToNextChapter()

    abstract fun moveToPrevChapter()
}
