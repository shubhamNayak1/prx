package com.baseras.fieldpharma

import android.app.Application
import com.baseras.fieldpharma.auth.AuthStore
import com.baseras.fieldpharma.data.local.AppDatabase
import com.baseras.fieldpharma.data.remote.ApiClient
import com.baseras.fieldpharma.data.repo.AttendanceRepository
import com.baseras.fieldpharma.data.repo.AuthRepository
import com.baseras.fieldpharma.data.repo.ClientRepository
import com.baseras.fieldpharma.data.repo.EdetailRepository
import com.baseras.fieldpharma.data.repo.ExpenseRepository
import com.baseras.fieldpharma.data.repo.RcpaRepository
import com.baseras.fieldpharma.data.repo.SampleRepository
import com.baseras.fieldpharma.data.repo.TourPlanRepository
import com.baseras.fieldpharma.data.repo.VisitRepository
import com.baseras.fieldpharma.location.LocationProvider
import com.baseras.fieldpharma.sync.SyncTrigger
import com.baseras.fieldpharma.sync.scheduleSync

class FieldPharmaApp : Application() {
    lateinit var authStore: AuthStore
    lateinit var db: AppDatabase
    lateinit var api: com.baseras.fieldpharma.data.remote.Api
    lateinit var authRepo: AuthRepository
    lateinit var attendanceRepo: AttendanceRepository
    lateinit var clientRepo: ClientRepository
    lateinit var tourPlanRepo: TourPlanRepository
    lateinit var visitRepo: VisitRepository
    lateinit var expenseRepo: ExpenseRepository
    lateinit var sampleRepo: SampleRepository
    lateinit var edetailRepo: EdetailRepository
    lateinit var rcpaRepo: RcpaRepository
    lateinit var locationProvider: LocationProvider

    val apiBaseUrl: String get() = BuildConfig.API_URL

    override fun onCreate() {
        super.onCreate()
        instance = this
        SyncTrigger.init(this)
        authStore = AuthStore(this)
        db = AppDatabase.create(this)
        val (apiInstance, _) = ApiClient.create(BuildConfig.API_URL, authStore)
        api = apiInstance
        authRepo = AuthRepository(api, authStore)
        attendanceRepo = AttendanceRepository(api, db.attendanceDao(), db.pendingSyncDao())
        clientRepo = ClientRepository(api, db.clientDao())
        tourPlanRepo = TourPlanRepository(api)
        visitRepo = VisitRepository(api, db.visitDao())
        expenseRepo = ExpenseRepository(api, db.pendingSyncDao())
        sampleRepo = SampleRepository(api, db.pendingSyncDao())
        edetailRepo = EdetailRepository(api, db.deckDao(), db.slideDao(), db.pendingSyncDao())
        rcpaRepo = RcpaRepository(api, db.pendingSyncDao())
        locationProvider = LocationProvider(this)

        scheduleSync(this)
    }

    companion object {
        lateinit var instance: FieldPharmaApp
            private set
    }
}
