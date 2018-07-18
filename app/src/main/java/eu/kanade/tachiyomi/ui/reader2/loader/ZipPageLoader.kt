package eu.kanade.tachiyomi.ui.reader2.loader

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader2.ReaderPage
import eu.kanade.tachiyomi.util.DiskUtil
import net.greypanther.natsort.CaseInsensitiveSimpleNaturalComparator
import rx.Observable
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class ZipPageLoader(file: File) : PageLoader() {

    val zip = ZipFile(file)

    override fun recycle() {
        super.recycle()
        zip.close()
    }

    override fun getPages(): Observable<List<Page>> {
        return Observable.fromCallable { loadPages() }
    }

    private fun loadPages(): List<Page> {
        val comparator = CaseInsensitiveSimpleNaturalComparator.getInstance<String>()

        return zip.entries().toList()
            .filter { !it.isDirectory && DiskUtil.isImage(it.name) { zip.getInputStream(it) } }
            .sortedWith(Comparator<ZipEntry> { f1, f2 -> comparator.compare(f1.name, f2.name) })
            .mapIndexed { i, entry ->
                val streamFn = { zip.getInputStream(entry) }
                Page(i, stream = streamFn).apply { status = Page.READY }
            }
    }

    override fun getPage(page: ReaderPage): Observable<Int> {
        return Observable.just(if (isRecycled) {
            Page.ERROR
        } else {
            Page.READY
        })
    }
}
