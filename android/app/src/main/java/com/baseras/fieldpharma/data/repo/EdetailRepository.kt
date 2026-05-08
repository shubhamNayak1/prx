package com.baseras.fieldpharma.data.repo

import com.baseras.fieldpharma.data.local.DeckDao
import com.baseras.fieldpharma.data.local.DeckEntity
import com.baseras.fieldpharma.data.local.PendingSyncDao
import com.baseras.fieldpharma.data.local.PendingSyncEntity
import com.baseras.fieldpharma.data.local.SlideDao
import com.baseras.fieldpharma.data.local.SlideEntity
import com.baseras.fieldpharma.data.remote.Api
import com.baseras.fieldpharma.data.remote.EdetailViewReq
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class EdetailRepository(
    private val api: Api,
    private val deckDao: DeckDao,
    private val slideDao: SlideDao,
    private val pendingDao: PendingSyncDao,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun observeDecks(): Flow<List<DeckEntity>> = deckDao.observeAll()

    suspend fun slidesFor(deckId: String): List<SlideEntity> = slideDao.byDeck(deckId)

    /** Refresh decks + slides from server, persist to Room. */
    suspend fun refresh(): Result<Unit> = runCatching {
        val res = api.listDecks()
        deckDao.upsertAll(res.decks.map {
            DeckEntity(id = it.id, name = it.name, product = it.product)
        })
        for (deck in res.decks) {
            slideDao.deleteByDeck(deck.id)
            slideDao.upsertAll(deck.slides.map { s ->
                SlideEntity(id = s.id, deckId = deck.id, order = s.order, title = s.title, imageUrl = s.imageUrl)
            })
        }
    }

    /** Track viewed slides. Tries online; queues if offline. */
    suspend fun trackViews(visitId: String?, slideViewSeconds: Map<String, Int>) {
        if (slideViewSeconds.isEmpty()) return
        val req = EdetailViewReq(visitId = visitId, slides = slideViewSeconds)
        try {
            api.trackEdetailViews(req)
        } catch (t: Throwable) {
            pendingDao.add(
                PendingSyncEntity(
                    type = "EDETAIL_VIEW",
                    payloadJson = json.encodeToString(req),
                )
            )
        }
    }

    suspend fun syncPending(): Boolean {
        val items = pendingDao.next().filter { it.type == "EDETAIL_VIEW" }
        var allOk = true
        for (item in items) {
            try {
                api.trackEdetailViews(json.decodeFromString(item.payloadJson))
                pendingDao.delete(item.id)
            } catch (t: Throwable) {
                pendingDao.bumpAttempts(item.id)
                allOk = false
            }
        }
        return allOk
    }
}
