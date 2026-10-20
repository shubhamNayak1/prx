package com.baseras.fieldpharma.data.repo

import com.baseras.fieldpharma.data.remote.Api
import com.baseras.fieldpharma.data.remote.RoleSummaryRes

class DashboardRepository(private val api: Api) {
    suspend fun roleSummary(): Result<RoleSummaryRes> = runCatching { api.roleSummary() }
}
