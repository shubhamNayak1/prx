package com.baseras.fieldpharma.data.remote

import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

interface Api {
    @POST("/api/auth/login")
    suspend fun login(@Body req: LoginReq): LoginRes

    @GET("/api/auth/me")
    suspend fun me(): MeRes

    @POST("/api/attendance/punch-in")
    suspend fun punchIn(@Body req: PunchReq): AttendanceRes

    @POST("/api/attendance/punch-out")
    suspend fun punchOut(@Body req: PunchReq): AttendanceRes

    @GET("/api/attendance/today")
    suspend fun todayAttendance(): AttendanceRes

    @Multipart
    @POST("/api/uploads")
    suspend fun upload(@Part file: MultipartBody.Part): UploadRes

    @GET("/api/clients")
    suspend fun listClients(@Query("q") q: String? = null): ClientListRes

    @POST("/api/clients")
    suspend fun createClient(@Body req: ClientCreateReq): ClientRes

    @GET("/api/tour-plans")
    suspend fun tourPlans(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): TourPlanListRes

    @POST("/api/tour-plans")
    suspend fun createTourPlan(@Body req: TourPlanReq): TourPlanRes

    @PATCH("/api/tour-plans/{id}/review")
    suspend fun reviewTourPlan(@Path("id") id: String, @Body req: TourPlanReviewReq): TourPlanRes

    @POST("/api/visits")
    suspend fun saveVisit(@Body req: VisitReq): VisitRes

    @GET("/api/visits")
    suspend fun listVisits(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): VisitListRes

    @POST("/api/expenses")
    suspend fun createExpense(@Body req: ExpenseReq): ExpenseRes

    @GET("/api/expenses")
    suspend fun listExpenses(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): ExpenseListRes

    @GET("/api/expense-policies/me")
    suspend fun myExpensePolicy(): ExpensePolicyRes

    @GET("/api/samples/balance")
    suspend fun sampleBalance(): SampleBalanceRes

    @POST("/api/samples/distributions")
    suspend fun distributeSample(@Body req: SampleDistReq): SampleDistRes

    // ───────── E-detailing ─────────
    @GET("/api/edetail/decks")
    suspend fun listDecks(): DeckListRes

    @POST("/api/edetail/views")
    suspend fun trackEdetailViews(@Body req: EdetailViewReq): EdetailViewRes

    // ───────── RCPA ─────────
    @POST("/api/rcpa")
    suspend fun createRcpa(@Body req: RcpaReq): RcpaRes

    @GET("/api/rcpa")
    suspend fun listRcpa(): RcpaListRes
}

@Serializable data class LoginReq(val email: String, val password: String)
@Serializable data class LoginRes(val token: String, val user: UserDto)
@Serializable data class MeRes(val user: UserDto)

@Serializable
data class UserDto(
    val id: String, val email: String, val name: String, val role: String,
    val companyId: String? = null, val grade: String? = null,
    val employeeCode: String? = null, val managerId: String? = null,
)

@Serializable
data class PunchReq(
    val date: String, val at: String,
    val lat: Double? = null, val lng: Double? = null,
    val photo: String? = null,
)

@Serializable data class AttendanceRes(val attendance: AttendanceDto?)

@Serializable
data class AttendanceDto(
    val id: String, val userId: String, val date: String,
    val punchInAt: String? = null,
    val punchInLat: Double? = null, val punchInLng: Double? = null,
    val punchOutAt: String? = null,
    val punchOutLat: Double? = null, val punchOutLng: Double? = null,
)

@Serializable data class UploadRes(val url: String, val size: Long)

@Serializable
data class ClientDto(
    val id: String, val name: String, val type: String,
    val speciality: String? = null, val address: String? = null,
    val city: String? = null, val pincode: String? = null,
    val phone: String? = null, val email: String? = null,
    val latitude: Double? = null, val longitude: Double? = null,
)

@Serializable data class ClientListRes(val clients: List<ClientDto>)
@Serializable data class ClientRes(val client: ClientDto)

@Serializable
data class ClientCreateReq(
    val name: String, val type: String,
    val speciality: String? = null, val address: String? = null,
    val city: String? = null, val pincode: String? = null,
    val phone: String? = null, val email: String? = null,
    val latitude: Double? = null, val longitude: Double? = null,
)

@Serializable
data class TourPlanDto(
    val id: String, val date: String, val status: String,
    val notes: String? = null,
    val entries: List<TourPlanEntryDto> = emptyList(),
    val user: UserDto? = null,
)

@Serializable
data class TourPlanEntryDto(
    val id: String,
    val clientId: String? = null, val area: String? = null,
    val notes: String? = null, val client: ClientDto? = null,
)

@Serializable data class TourPlanListRes(val plans: List<TourPlanDto>)
@Serializable data class TourPlanRes(val plan: TourPlanDto)

@Serializable
data class TourPlanReq(
    val date: String, val notes: String? = null,
    val entries: List<TourPlanEntryReq>,
)

@Serializable
data class TourPlanEntryReq(
    val clientId: String? = null, val area: String? = null, val notes: String? = null,
)

@Serializable data class TourPlanReviewReq(val status: String)

@Serializable
data class VisitReq(
    val clientId: String, val checkInAt: String,
    val checkInLat: Double? = null, val checkInLng: Double? = null,
    val checkOutAt: String? = null,
    val checkOutLat: Double? = null, val checkOutLng: Double? = null,
    val productsDiscussed: List<String>? = null,
    val notes: String? = null, val isJointWork: Boolean? = null,
)

@Serializable
data class VisitDto(
    val id: String, val clientId: String, val checkInAt: String,
    val checkOutAt: String? = null, val notes: String? = null,
    val client: ClientDto? = null,
)

@Serializable data class VisitRes(val visit: VisitDto)
@Serializable data class VisitListRes(val visits: List<VisitDto>)

@Serializable
data class ExpenseReq(
    val date: String, val type: String, val amount: Double,
    val category: String? = null,
    val fromLocation: String? = null, val toLocation: String? = null,
    val distanceKm: Double? = null, val modeOfTravel: String? = null,
    val billPhoto: String? = null, val remarks: String? = null,
    val actionLat: Double? = null, val actionLng: Double? = null,
)

@Serializable
data class ExpenseDto(
    val id: String, val date: String, val type: String,
    val amount: Double, val status: String,
    val category: String? = null,
    val fromLocation: String? = null, val toLocation: String? = null,
    val distanceKm: Double? = null,
    val billPhoto: String? = null, val remarks: String? = null,
)

@Serializable data class ExpenseRes(val expense: ExpenseDto)
@Serializable data class ExpenseListRes(val expenses: List<ExpenseDto>)

@Serializable
data class ExpensePolicyDto(
    val id: String, val grade: String,
    val taRatePerKm: Double, val daFlatRate: Double,
)

@Serializable data class ExpensePolicyRes(val policy: ExpensePolicyDto?)

@Serializable
data class SampleProductDto(
    val id: String, val name: String, val unitType: String,
    val isGift: Boolean = false,
)

@Serializable
data class SampleBalanceDto(
    val issueId: String, val product: SampleProductDto,
    val issued: Int, val distributed: Int, val remaining: Int,
    val issuedAt: String,
)

@Serializable data class SampleBalanceRes(val balance: List<SampleBalanceDto>)

@Serializable
data class SampleDistReq(
    val sampleIssueId: String, val visitId: String? = null, val quantity: Int,
    val actionLat: Double? = null, val actionLng: Double? = null,
)

@Serializable
data class SampleDistDto(val id: String, val sampleIssueId: String, val quantity: Int)

@Serializable data class SampleDistRes(val distribution: SampleDistDto)

// ───────── E-detailing ─────────

@Serializable
data class DeckDto(
    val id: String, val name: String, val product: String? = null,
    val slides: List<SlideDto> = emptyList(),
)

@Serializable
data class SlideDto(
    val id: String, val order: Int, val title: String? = null, val imageUrl: String,
)

@Serializable data class DeckListRes(val decks: List<DeckDto>)

@Serializable
data class EdetailViewReq(
    val visitId: String? = null,
    val slides: Map<String, Int>,  // slideId -> seconds viewed
)

@Serializable data class EdetailViewRes(val ok: Boolean)

// ───────── RCPA ─────────

@Serializable
data class RcpaReq(
    val clientId: String, val date: String,
    val ourBrand: String, val ourQuantity: Int,
    val competitorBrand: String, val competitorQuantity: Int,
    val remarks: String? = null,
    val actionLat: Double? = null, val actionLng: Double? = null,
)

@Serializable
data class RcpaDto(
    val id: String, val date: String,
    val ourBrand: String, val ourQuantity: Int,
    val competitorBrand: String, val competitorQuantity: Int,
    val remarks: String? = null,
    val client: ClientLite? = null,
)

@Serializable data class ClientLite(val id: String, val name: String)

@Serializable data class RcpaRes(val entry: RcpaDto)
@Serializable data class RcpaListRes(val entries: List<RcpaDto>)
