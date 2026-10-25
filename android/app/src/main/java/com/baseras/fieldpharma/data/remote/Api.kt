package com.baseras.fieldpharma.data.remote

import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PATCH
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface Api {
    @POST("/api/auth/login")
    suspend fun login(@Body req: LoginReq): LoginRes

    @POST("/api/auth/pin-login")
    suspend fun pinLogin(@Body req: PinLoginReq): LoginRes

    @POST("/api/auth/request-otp")
    suspend fun requestAuthOtp(@Body req: AuthOtpReq): AuthOtpRes

    @POST("/api/auth/verify-otp")
    suspend fun verifyAuthOtp(@Body req: AuthOtpVerifyReq): AuthOtpVerifyRes

    @POST("/api/auth/set-pin")
    suspend fun setPin(@Body req: SetPinReq): LoginRes

    @POST("/api/auth/signup-complete")
    suspend fun signupComplete(@Body req: SignupCompleteReq): LoginRes

    @GET("/api/auth/me")
    suspend fun me(): MeRes

    @POST("/api/attendance/punch-in")
    suspend fun punchIn(@Body req: PunchReq): AttendanceRes

    @POST("/api/attendance/punch-out")
    suspend fun punchOut(@Body req: PunchReq): AttendanceRes

    @GET("/api/attendance/today")
    suspend fun todayAttendance(): AttendanceSessionsRes

    @Multipart
    @POST("/api/uploads")
    suspend fun upload(@Part file: MultipartBody.Part): UploadRes

    @GET("/api/clients")
    suspend fun listClients(
        @Query("q") q: String? = null,
        @Query("pincode") pincode: String? = null,
        @Query("excludeIds") excludeIdsCsv: String? = null,
    ): ClientListRes

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

    @GET("/api/visits/joint-pending")
    suspend fun listPendingJointWork(): VisitListRes

    @PATCH("/api/visits/{id}/joint-review")
    suspend fun reviewJointWork(@Path("id") id: String, @Body req: JointReviewReq): VisitRes

    @POST("/api/expenses")
    suspend fun createExpense(@Body req: ExpenseReq): ExpenseRes

    @GET("/api/expenses")
    suspend fun listExpenses(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("status") status: String? = null,
    ): ExpenseListRes

    @PATCH("/api/expenses/{id}/review")
    suspend fun reviewExpense(@Path("id") id: String, @Body req: ExpenseReviewReq): ExpenseRes

    @GET("/api/expense-policies/me")
    suspend fun myExpensePolicy(): ExpensePolicyRes

    @GET("/api/expense-policies")
    suspend fun listExpensePolicies(): ExpensePolicyListRes

    @PUT("/api/expense-policies")
    suspend fun upsertExpensePolicy(@Body req: ExpensePolicyUpsertReq): ExpensePolicyRes

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

    // ───────── Billing ─────────
    @GET("/api/billing/status")
    suspend fun billingStatus(): BillingStatusRes

    // ───────── Dashboard — role-aware summary ─────────
    @GET("/api/dashboard/role-summary")
    suspend fun roleSummary(): RoleSummaryRes

    // ───────── Company — admin profile ─────────
    @GET("/api/company")
    suspend fun getCompany(): CompanyRes

    @PATCH("/api/company")
    suspend fun updateCompany(@Body req: CompanyUpdateReq): CompanyRes

    // ───────── Users — admin / manager side ─────────
    @GET("/api/users")
    suspend fun listUsers(): UserListRes

    @POST("/api/users")
    suspend fun createUser(@Body req: CreateUserReq): CreateUserRes

    // ───────── Billing — admin-side ─────────
    @GET("/api/billing/invoices")
    suspend fun billingInvoices(): BillingInvoicesRes

    @POST("/api/billing/upgrade")
    suspend fun billingUpgrade(@Body req: BillingUpgradeReq): BillingUpgradeRes
}

@Serializable data class CompanyRes(val company: CompanyDto)
@Serializable
data class CompanyDto(
    val id: String,
    val name: String,
    val gstin: String? = null,
    val billingEmail: String? = null,
    val billingPhone: String? = null,
)
@Serializable
data class CompanyUpdateReq(
    val name: String? = null,
    val gstin: String? = null,
    val billingEmail: String? = null,
    val billingPhone: String? = null,
)

@Serializable data class UserListRes(val users: List<UserRow>)
@Serializable
data class UserRow(
    val id: String, val name: String, val email: String,
    val phone: String? = null,
    val role: String, val grade: String? = null,
    val employeeCode: String? = null,
    val isActive: Boolean = true,
    val managerId: String? = null,
    val manager: ManagerRef? = null,
)
@Serializable data class ManagerRef(val id: String, val name: String)

@Serializable
data class CreateUserReq(
    val name: String,
    val email: String,
    val password: String,
    val phone: String? = null,
    val role: String, // ADMIN | SENIOR_MANAGER | REGIONAL_MANAGER | FIELD_PERSON
    val grade: String? = null,
    val employeeCode: String? = null,
    val managerId: String? = null,
)
@Serializable data class CreateUserRes(val user: UserRow)

@Serializable data class BillingInvoicesRes(val invoices: List<InvoiceDto>)
@Serializable
data class InvoiceDto(
    val id: String,
    val number: String,
    val cycle: String,
    val periodStart: String,
    val periodEnd: String,
    val activeRepCount: Int,
    val subtotalPaise: Long,
    val gstPaise: Long,
    val totalPaise: Long,
    val status: String,
    val dueAt: String,
    val paidAt: String? = null,
    val pdfUrl: String? = null,
)

@Serializable data class BillingUpgradeReq(val cycle: String)
@Serializable data class BillingUpgradeRes(
    val invoice: InvoiceSummary,
    val paymentLink: String,
)
@Serializable data class InvoiceSummary(
    val id: String,
    val number: String,
    val totalPaise: Long,
    val activeRepCount: Int,
    val discountPct: Int,
    val cycle: String,
)

@Serializable
data class RoleSummaryRes(
    val role: String,
    val kpis: RoleKpis,
    val team: List<TeamRollup> = emptyList(),
)

@Serializable
data class RoleKpis(
    val totalUsers: Int = 0,
    val totalReps: Int = 0,
    val activeToday: Int = 0,
    val callsToday: Int = 0,
    val callsPlannedToday: Int = 0,
    val monthVisits: Int = 0,
    val monthPlanned: Int = 0,
    val attainmentPct: Int = 0,
    val pendingTourPlans: Int = 0,
    val pendingExpensesCount: Int = 0,
    val pendingExpensesAmount: Double = 0.0,
    val monthSpendInr: Double = 0.0,
    val pendingClients: Int = 0,
    val billableUsers: Int = 0,
)

@Serializable
data class TeamRollup(
    val id: String,
    val name: String,
    val role: String,
    val grade: String? = null,
    val activeToday: Int,
    val totalReps: Int,
    val callsToday: Int,
    val callsPlanned: Int,
    val monthVisits: Int,
    val monthPlanned: Int,
    val attainmentPct: Int,
    val status: String, // ActiveToday | NotPunched | OffField | Flagged
)

@Serializable
data class BillingStatusRes(val subscription: SubscriptionDto)

@Serializable
data class SubscriptionDto(
    val status: String, // TRIAL | ACTIVE | PAST_DUE | LOCKED | CANCELLED
    val cycle: String? = null,
    val trialEndsAt: String? = null,
    val currentPeriodStart: String? = null,
    val currentPeriodEnd: String? = null,
    val pricePerRepPaise: Int = 0,
    val trialRepCap: Int? = null,
)

@Serializable data class LoginReq(val email: String, val password: String)
@Serializable data class LoginRes(val token: String, val user: UserDto)
@Serializable data class MeRes(val user: UserDto)

@Serializable data class PinLoginReq(val phone: String, val pin: String)
@Serializable data class AuthOtpReq(val phone: String, val purpose: String = "LOGIN")
@Serializable
data class AuthOtpRes(
    val challengeId: String,
    val expiresAt: String,
    val channel: String,                 // "whatsapp" | "sms" | "dev"
    val devCode: String? = null,
)
@Serializable data class AuthOtpVerifyReq(val phone: String, val otp: String, val purpose: String = "LOGIN")
@Serializable
data class AuthOtpVerifyRes(
    val exists: Boolean,
    val hasPin: Boolean,
    val setupToken: String,
    val user: AuthOtpVerifyUser? = null,
)
@Serializable data class AuthOtpVerifyUser(val id: String, val name: String, val role: String)
@Serializable data class SetPinReq(val setupToken: String, val pin: String, val confirmPin: String)
@Serializable
data class SignupCompleteReq(
    val setupToken: String,
    val companyName: String,
    val gstin: String? = null,
    val adminName: String,
    val adminEmail: String,
    val pin: String,
)

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
@Serializable data class AttendanceSessionsRes(val sessions: List<AttendanceDto> = emptyList())

@Serializable
data class AttendanceDto(
    val id: String, val userId: String, val date: String,
    val punchInAt: String,
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
    val userId: String? = null,   // Manager creating for a report; omitted = self
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
    val notes: String? = null,
    val isJointWork: Boolean? = null,
    val jointWithUserId: String? = null,
)

@Serializable
data class VisitDto(
    val id: String, val clientId: String, val checkInAt: String,
    val checkOutAt: String? = null, val notes: String? = null,
    val isJointWork: Boolean? = null,
    val jointWithUserId: String? = null,
    val jointWorkApproved: Boolean? = null,
    val client: ClientDto? = null,
    val user: VisitUser? = null,
)

@Serializable data class VisitUser(val id: String, val name: String, val employeeCode: String? = null)
@Serializable data class VisitRes(val visit: VisitDto)
@Serializable data class VisitListRes(val visits: List<VisitDto>)
@Serializable data class JointReviewReq(val approved: Boolean)

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
    val user: ExpenseSubmitter? = null,
)

@Serializable data class ExpenseSubmitter(val id: String, val name: String, val employeeCode: String? = null)

@Serializable data class ExpenseRes(val expense: ExpenseDto)
@Serializable data class ExpenseListRes(val expenses: List<ExpenseDto>)
@Serializable data class ExpenseReviewReq(val status: String, val remarks: String? = null)

@Serializable
data class ExpensePolicyDto(
    val id: String, val grade: String,
    val taRatePerKm: Double, val daFlatRate: Double,
)

@Serializable data class ExpensePolicyRes(val policy: ExpensePolicyDto?)
@Serializable data class ExpensePolicyListRes(val policies: List<ExpensePolicyDto>)
@Serializable
data class ExpensePolicyUpsertReq(
    val grade: String,
    val taRatePerKm: Double,
    val daFlatRate: Double,
)

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
