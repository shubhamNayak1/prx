package com.baseras.fieldpharma.data.repo

import com.baseras.fieldpharma.data.remote.Api
import com.baseras.fieldpharma.data.remote.TourPlanDto
import com.baseras.fieldpharma.data.remote.TourPlanReq
import java.time.LocalDate

class TourPlanRepository(private val api: Api) {

    suspend fun upcoming(): Result<List<TourPlanDto>> = runCatching {
        val today = LocalDate.now()
        val end = today.plusDays(14)
        api.tourPlans(from = today.toString(), to = end.toString()).plans
    }

    suspend fun savePlan(req: TourPlanReq): Result<TourPlanDto> = runCatching {
        api.createTourPlan(req).plan
    }
}
