package eu.kanade.tachiyomi.ui.reader2

import eu.kanade.tachiyomi.source.model.Page
import java.io.InputStream

class ReaderPage(
        val chapter2: ReaderChapter,
        index: Int,
        url: String = "",
        imageUrl: String? = null,
        stream: (() -> InputStream)? = null
) : Page(index, url, imageUrl, null, stream) {

    companion object {
        fun from(page: Page, chapter: ReaderChapter): ReaderPage {
            return ReaderPage(chapter, page.index, page.url, page.imageUrl, page.stream)
        }
    }
}
