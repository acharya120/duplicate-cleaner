package com.duplicatecleaner.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.duplicatecleaner.data.documents.DocumentScanner
import com.duplicatecleaner.data.hashing.DuplicateGroupingEngine
import com.duplicatecleaner.data.hashing.FileHashCalculator
import com.duplicatecleaner.data.hashing.PerceptualHashCalculator
import com.duplicatecleaner.data.local.DuplicateDao
import com.duplicatecleaner.data.local.membershipsOf
import com.duplicatecleaner.data.local.toEntity
import com.duplicatecleaner.data.media.MediaStoreScanner
import com.duplicatecleaner.domain.model.DuplicateFile
import com.duplicatecleaner.domain.model.FileCategory
import com.duplicatecleaner.domain.model.ScanEvent
import com.duplicatecleaner.domain.model.ScanProgress
import com.duplicatecleaner.domain.model.ScanResults
import com.duplicatecleaner.domain.model.ScanSettings
import com.duplicatecleaner.domain.model.ScanState
import com.duplicatecleaner.domain.repository.DuplicateRepository
import com.duplicatecleaner.domain.repository.ScanRepository
import com.duplicatecleaner.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScanRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaScanner: MediaStoreScanner,
    private val documentScanner: DocumentScanner,
    private val hasher: FileHashCalculator,
    private val perceptual: PerceptualHashCalculator,
    private val engine: DuplicateGroupingEngine,
    private val dao: DuplicateDao,
    private val duplicates: DuplicateRepository,
    private val settingsRepo: SettingsRepository,
) : ScanRepository {

    private val resolver: ContentResolver get() = context.contentResolver

    override fun scan(settings: ScanSettings): Flow<ScanEvent> = channelFlow {
        val skipped = AtomicInteger(0)
        var progress = ScanProgress(operation = "Preparing")
        suspend fun state(s: ScanState, p: ScanProgress = progress) {
            progress = p
            send(ScanEvent.StateChanged(s, p))
        }

        state(ScanState.Preparing)
        val cache = try { dao.getAll().associateBy { it.uri } } catch (e: Exception) { emptyMap() }

        // ---- 1. discovery ----
        val discovered = ArrayList<DuplicateFile>()
        suspend fun discover(category: FileCategory, s: ScanState, label: String, block: suspend ((Int) -> Unit) -> List<DuplicateFile>) {
            val base = discovered.size
            state(s, progress.copy(category = category, operation = label))
            val found = withContext(Dispatchers.IO) {
                block { n -> trySend(ScanEvent.Progress(progress.copy(filesDiscovered = base + n))) }
            }
            discovered += found
            progress = progress.copy(filesDiscovered = discovered.size)
        }
        if (settings.scanPhotos) discover(FileCategory.PHOTO, ScanState.ScanningPhotos, "Reading photo library") { cb -> mediaScanner.scan(FileCategory.PHOTO, cb) }
        if (settings.scanVideos) discover(FileCategory.VIDEO, ScanState.ScanningVideos, "Reading video library") { cb -> mediaScanner.scan(FileCategory.VIDEO, cb) }
        var inaccessible = emptyList<String>()
        if (settings.scanDocuments && settings.documentFolderUris.isNotEmpty()) {
            discover(FileCategory.DOCUMENT, ScanState.ScanningDocuments, "Reading selected folders") { cb ->
                val r = documentScanner.scan(settings.documentFolderUris, cb)
                inaccessible = r.inaccessibleFolders
                r.files
            }
        }
        ensureActive()

        // ---- 2. hashing ----
        val eligible = engine.eligible(discovered)
        val exactNeeded = engine.sizeCandidates(eligible).map { it.uri }.toHashSet()
        val photosForPerceptual = if (settings.findSimilarPhotos) eligible.filter { it.category == FileCategory.PHOTO } else emptyList()
        val perceptualNeeded = photosForPerceptual.map { it.uri }.toHashSet()
        val workTotal = (exactNeeded.size + perceptualNeeded.size).coerceAtLeast(1)
        val done = AtomicInteger(0)
        val lastEmit = AtomicLong(0)

        state(ScanState.CalculatingHashes, progress.copy(operation = "Comparing file contents", filesProcessed = 0, percent = 0))
        val updated = java.util.concurrent.ConcurrentHashMap<String, DuplicateFile>()

        // Live duplicate stats from SHA-256 results as they arrive (exact duplicates only).
        val hashCounts = HashMap<String, Int>()
        var liveGroups = 0
        var livePotential = 0
        var liveBytes = 0L
        fun recordHash(f: DuplicateFile) {
            val sha = f.sha256 ?: return
            synchronized(hashCounts) {
                val key = "${f.category}:${f.sizeBytes}:$sha"
                val n = (hashCounts[key] ?: 0) + 1
                hashCounts[key] = n
                if (n == 2) liveGroups++
                if (n >= 2) { livePotential++; liveBytes += f.sizeBytes }
            }
        }

        fun emitProgress(force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (!force && now - lastEmit.get() < 120) return
            lastEmit.set(now)
            val processed = done.get()
            val (g, pot, bytes) = synchronized(hashCounts) { Triple(liveGroups, livePotential, liveBytes) }
            trySend(ScanEvent.Progress(progress.copy(
                filesProcessed = processed,
                groupsFound = g, potentialDuplicates = pot, recoverableBytes = bytes,
                percent = (processed * 100 / workTotal).coerceIn(0, 99),
            )))
        }

        suspend fun runPool(items: List<DuplicateFile>, parallelism: Int, dispatcher: CoroutineDispatcher, onResult: (DuplicateFile) -> Unit = {}, work: suspend (DuplicateFile) -> DuplicateFile) {
            if (items.isEmpty()) return
            val next = AtomicInteger(0)
            coroutineScope {
                repeat(parallelism.coerceAtMost(items.size)) {
                    launch(dispatcher) {
                        while (true) {
                            ensureActive()
                            val i = next.getAndIncrement()
                            if (i >= items.size) break
                            val result = try { work(items[i]) } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                            catch (e: Exception) { skipped.incrementAndGet(); items[i] }
                            updated[result.uri] = result
                            onResult(result)
                            done.incrementAndGet()
                            emitProgress()
                        }
                    }
                }
            }
        }

        // 2a. SHA-256 for size-matched files (streamed, bounded parallelism); unchanged files reuse cached hashes.
        val shaItems = eligible.filter { it.uri in exactNeeded }
        runPool(shaItems, SHA_PARALLELISM, Dispatchers.IO, onResult = ::recordHash) { f ->
            val cached = cache[f.uri]
            if (cached?.sha256 != null && cached.sizeBytes == f.sizeBytes && cached.lastModified == f.lastModified) {
                f.copy(sha256 = cached.sha256)
            } else {
                val h = hasher.sha256 { openStream(f.uri) }
                if (h == null) { skipped.incrementAndGet(); f } else f.copy(sha256 = h)
            }
        }

        // 2b. Perceptual hashes (CPU-bound, small decode).
        val photoItems = photosForPerceptual.map { updated[it.uri] ?: it }
        runPool(photoItems, PHASH_PARALLELISM, Dispatchers.Default) { f ->
            val cached = cache[f.uri]
            if (cached?.perceptualHash != null && cached.sizeBytes == f.sizeBytes && cached.lastModified == f.lastModified) {
                f.copy(perceptualHash = cached.perceptualHash, width = cached.width.takeIf { it > 0 } ?: f.width, height = cached.height.takeIf { it > 0 } ?: f.height)
            } else {
                val info = perceptual.compute { openStream(f.uri) }
                if (info == null) { skipped.incrementAndGet(); f }
                else f.copy(perceptualHash = info.hash, width = info.width, height = info.height)
            }
        }
        ensureActive()

        // ---- 3. grouping ----
        state(ScanState.GroupingDuplicates, progress.copy(operation = "Grouping duplicates", percent = 99))
        val finalFiles = eligible.map { updated[it.uri] ?: it }
        val groups = withContext(Dispatchers.Default) {
            val exact = engine.groupExact(finalFiles)
            val similar = if (settings.findSimilarPhotos) {
                engine.groupSimilar(finalFiles.filter { it.category == FileCategory.PHOTO }, settings.similarityThreshold)
            } else emptyList()
            exact + similar
        }
        val completedAt = System.currentTimeMillis()
        val results = ScanResults(groups, completedAt)

        // ---- 4. persist (metadata only) ----
        try {
            val memberships = membershipsOf(groups)
            val entities = finalFiles.filter { it.sha256 != null || it.perceptualHash != null }
                .map { it.toEntity(memberships[it.uri].orEmpty()) }
            dao.sync(entities, finalFiles.map { it.uri }.toSet())
            settingsRepo.setLastScanAt(completedAt)
        } catch (e: Exception) {
            // Persistence is an optimisation; results are still delivered.
        }
        duplicates.publish(results)
        send(ScanEvent.Finished(results, skipped.get()))
    }.flowOn(Dispatchers.Default)

    private fun openStream(uri: String) = try {
        resolver.openInputStream(Uri.parse(uri))
    } catch (e: FileNotFoundException) {
        null
    }

    private companion object {
        const val SHA_PARALLELISM = 3
        const val PHASH_PARALLELISM = 2
    }
}
