package eu.kanade.tachiyomi.ui.reader2.viewer

import android.view.KeyEvent
import android.view.View
import eu.kanade.tachiyomi.ui.reader2.ReaderActivity
import eu.kanade.tachiyomi.ui.reader2.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader2.model.ViewerChapters

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

    open fun handleKeyEvent(event: KeyEvent): Boolean {
        return false
    }

}
