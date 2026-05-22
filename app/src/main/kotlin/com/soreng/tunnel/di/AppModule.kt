package com.soreng.tunnel.di

import android.content.Context
import androidx.room.Room
import com.soreng.tunnel.storage.*
import com.soreng.tunnel.vpn.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun db(@ApplicationContext c: Context): AppDatabase =
        Room.databaseBuilder(c, AppDatabase::class.java, "soren_db")
            .fallbackToDestructiveMigration().build()

    @Provides @Singleton fun configDao(db: AppDatabase):    ConfigDao        = db.configDao()
    @Provides @Singleton fun subDao(db: AppDatabase):       SubscriptionDao  = db.subDao()
    @Provides @Singleton fun statsDao(db: AppDatabase):     SessionStatsDao  = db.statsDao()

    @Provides @Singleton fun socketProtector():             SocketProtector      = SocketProtector()
    @Provides @Singleton fun connVerifier():                ConnectivityVerifier = ConnectivityVerifier()

    @Provides @Singleton
    fun healthChecker(p: SocketProtector): HealthChecker = HealthChecker(p)

    @Provides @Singleton
    fun wakeLockManager(@ApplicationContext c: Context): WakeLockManager = WakeLockManager(c)

    @Provides @Singleton
    fun processGuard(@ApplicationContext c: Context): ProcessGuard = ProcessGuard(c)

    @Provides @Singleton
    fun reconnectManager(): ReconnectManager = ReconnectManager()

    @Provides @Singleton
    fun watchdog(
        ps: com.soreng.tunnel.psiphon.PsiphonManager,
        xr: com.soreng.tunnel.xray.XrayManager,
        t2: com.soreng.tunnel.tunnel.Tun2SocksManager,
        hc: HealthChecker,
        pr: AppPreferences
    ): WatchdogSupervisor = WatchdogSupervisor(ps, xr, t2, hc, pr)
}
