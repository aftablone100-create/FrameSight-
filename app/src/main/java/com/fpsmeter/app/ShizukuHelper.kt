package com.fpsmeter.app

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku

object ShizukuHelper {

    private const val PERMISSION_REQUEST_CODE = 9001
    private const val APP_PACKAGE = "com.fpsmeter.app"

    private var userService: IUserService? = null
    private var bound = false

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(APP_PACKAGE, UserService::class.java.name))
            .daemon(false)
            .processNameSuffix("fpsmeter")
            .debuggable(false)
            .version(1)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            userService = if (service != null && service.isBinderAlive) IUserService.Stub.asInterface(service) else null
            bound = userService != null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            userService = null
            bound = false
        }
    }

    fun isShizukuAvailable(): Boolean {
        return try { Shizuku.pingBinder() } catch (e: Throwable) { false }
    }

    fun hasPermission(): Boolean {
        return try { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (e: Throwable) { false }
    }

    fun requestPermission(listener: (Boolean) -> Unit) {
        if (!isShizukuAvailable()) { listener(false); return }
        if (hasPermission()) { listener(true); return }

        val resultListener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode == PERMISSION_REQUEST_CODE) {
                    Shizuku.removeRequestPermissionResultListener(this)
                    listener(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
        }
        Shizuku.addRequestPermissionResultListener(resultListener)
        Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
    }

    fun bindService(context: Context) {
        if (bound) return
        if (!isShizukuAvailable() || !hasPermission()) return
        try {
            Shizuku.bindUserService(userServiceArgs, connection)
        } catch (e: Throwable) {
            bound = false
        }
    }

    fun unbindService() {
        if (!bound) return
        try {
            Shizuku.unbindUserService(userServiceArgs, connection, true)
        } catch (e: Throwable) { }
        bound = false
        userService = null
    }

    fun isBound(): Boolean = bound && userService != null

    fun execCommand(cmd: String): String? {
        return try { userService?.execCommand(cmd) } catch (e: Throwable) { null }
    }
}
