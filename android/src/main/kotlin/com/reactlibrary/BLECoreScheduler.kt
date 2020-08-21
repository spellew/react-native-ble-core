package com.reactlibrary

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.reactlibrary.BLECoreModule.Companion.TAG


class BLECoreScheduler: BroadcastReceiver() {

    private val fifteenMinuteInterval = (15 * 60 * 1000).toLong()
    private var _context: Context? = null
    private val jobId = 44

    companion object {
        var _module: BLECoreModule? = null
        var count = 1
    }

    fun setContext(context: Context): BLECoreScheduler {
        _context = context
        return this
    }

    fun setBLEModule(module: BLECoreModule?): BLECoreScheduler {
        _module = module
        return this
    }

    fun start() {
        _context?.let { context ->
            Log.i(TAG, "Did the damn thing")
            scheduleWakeUpIntent(context)
        }
    }

    fun stop() {
        _context?.let { context ->
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler?
            _module?.let { module ->
                if (!module.scannerIsInitialized() && !module.advertiserIsInitialized()) {
                    jobScheduler?.cancel(jobId)
                }
            }
        }
    }

    private fun scheduleWakeUpIntent(context: Context) {
        val wakeUpIntent = Intent("WAKE")
        wakeUpIntent.setClass(context, BLECoreScheduler::class.java)

        val pendingIntent = PendingIntent.getBroadcast(context, 0, wakeUpIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager?
        alarmManager?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + fifteenMinuteInterval, pendingIntent)
        Log.i(TAG, "scheduleWakeUpIntent")
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.i(TAG, "Context: ${context.toString()}")
        if (context == null) return
        Log.i(TAG, "Context received (intent's action): ${intent?.action}")
        when (intent?.action) {
            "WAKE" -> {
                Log.i(TAG, "WAKE intent!")

                val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler?
                val job = JobInfo.Builder(jobId, ComponentName(context, Service::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setMinimumLatency(0L)
                    .setOverrideDeadline(fifteenMinuteInterval)
                    .build()

                jobScheduler?.schedule(job)
                _module?.let {
                    if (it.scannerIsInitialized() || it.advertiserIsInitialized()) {
                        scheduleWakeUpIntent(context)
                    }
                }
            }
        }
    }

    class Service: JobService() {

//      TODO: Check battery usage stats, we never call jobFinished?
//      TODO: Could it be that recreating the scanner and stuffs causes multiple requests to be sent, look into if there's a proper way to remove the existing scanner and advertiser?
//       TODO: Try recreating them on the frontend with JS by calling startScanning and startAdvertising and see if that causes the same error
        override fun onStartJob(params: JobParameters?): Boolean {
            Log.i(TAG, "about to start job #${count}")
            count += 1

            Log.i(TAG, "is Module null?: ${_module == null}")
            _module?.let { module ->
                var startedJob = false
                if (module.scannerIsInitialized()) {
                    Log.i(TAG, "about to start doze scanning")
                    module._startScanning(module.getScanningUUIDs())
                    startedJob = true
                }

                if (module.advertiserIsInitialized()) {
                    Log.i(TAG, "about to start doze advertising")
                    module._startAdvertising(module.getAdvertisingServices())
                    startedJob = true
                }

                if (!startedJob) {
                    jobFinished(params, false)
                }
            }

            return true
        }

        override fun onStopJob(params: JobParameters?): Boolean {
            Log.i(TAG, "about to stop job")
            return true
        }
    }
}
