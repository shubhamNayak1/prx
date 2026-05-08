package com.baseras.fieldpharma.data.repo

import com.baseras.fieldpharma.data.local.ClientDao
import com.baseras.fieldpharma.data.local.ClientEntity
import com.baseras.fieldpharma.data.remote.Api
import com.baseras.fieldpharma.data.remote.ClientCreateReq
import com.baseras.fieldpharma.data.remote.ClientDto
import kotlinx.coroutines.flow.Flow

class ClientRepository(
    private val api: Api,
    private val dao: ClientDao,
) {
    fun observeAll(): Flow<List<ClientEntity>> = dao.observeAll()
    fun search(q: String): Flow<List<ClientEntity>> = dao.search(q)
    suspend fun byId(id: String): ClientEntity? = dao.get(id)

    /** Pull from server, replace cache. */
    suspend fun refresh(): Result<Unit> = runCatching {
        val res = api.listClients()
        dao.upsertAll(res.clients.map { it.toEntity() })
    }

    /** Create on server then cache locally. Throws on network error (no offline create yet). */
    suspend fun create(req: ClientCreateReq): ClientEntity {
        val res = api.createClient(req)
        val entity = res.client.toEntity()
        dao.upsert(entity)
        return entity
    }

    private fun ClientDto.toEntity() = ClientEntity(
        id = id, name = name, type = type, speciality = speciality,
        address = address, city = city, pincode = pincode, phone = phone,
        latitude = latitude, longitude = longitude,
    )
}
