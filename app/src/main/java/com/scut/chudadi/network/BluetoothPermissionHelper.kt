package com.scut.chudadi.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** 蓝牙运行时权限适配器，集中处理 Android 12 及以上的权限差异。 */
object BluetoothPermissionHelper {
    /** 真正执行连接/读取已配对设备时必须已经拥有的权限。 */
    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            emptyArray()
        }
    }

    /** 主动向用户申请时包含扫描权限，便于列出已配对设备和取消扫描。 */
    fun requestPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            emptyArray()
        }
    }

    /** 检查当前是否满足连接蓝牙房间的最低权限要求。 */
    fun hasRequiredPermissions(context: Context): Boolean {
        return requiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /** 扫描权限单独判断，因为部分流程只需要连接权限即可继续。 */
    fun hasScanPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
    }
}
