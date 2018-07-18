package eu.kanade.tachiyomi.ui.reader2.loader

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader2.ReaderPage
import eu.kanade.tachiyomi.util.DiskUtil
import net.greypanther.natsort.CaseInsensitiveSimpleNaturalComparator
import rx.Observable
import java.io.File
import java.io.FileInputStream

class DirectoryPageLoader(val file: File) : PageLoader() {

    override fun getPages(): Observable<List<Page>> {
        return Observable.fromCallable { loadPages() }
    }

    private fun loadPages(): List<Page> {
        val comparator = CaseInsensitiveSimpleNaturalComparator.getInstance<String>()

        return file.listFiles()
            .filter { !it.isDirectory && DiskUtil.isImage(it.name) { FileInputStream(it) } }
            .sortedWith(Comparator<File> { f1, f2 -> comparator.compare(f1.name, f2.name) })
            .mapIndexed { i, file ->
                val streamFn = { FileInputStream(file) }
                Page(i, stream = streamFn).apply { status = Page.READY }
            }
    }

    override fun getPage(page: ReaderPage): Observable<Int> {
        return Observable.just(Page.READY) // TODO maybe check if file still exists?
    }

}
