package tv.porch.app.launcher

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tv.porch.app.PorchApplication

/** Every 30 minutes, with or without the app open, brings the launcher row up to date. It reads Room only: no player, no refresh. */
class LauncherRowJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        // A sleeping TV does nothing: the row catches up on the next run with the screen on, on leaving the app, or after a refresh.
        if (getSystemService(PowerManager::class.java)?.isInteractive == false) return false
        val graph = (application as PorchApplication).graph
        running = scope.launch {
            try {
                graph.launcherRow.update()
            } finally {
                // onStopJob already told the system when it cancelled this.
                if (isActive) jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return false
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val JOB_ID = 7801
        private const val EVERY_MS = 30L * 60L * 1000L

        /** Idempotent: a job already waiting under this id is left alone. */
        fun schedule(context: Context) {
            try {
                val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
                if (scheduler.getPendingJob(JOB_ID) != null) return
                scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, LauncherRowJob::class.java)).setPeriodic(EVERY_MS).setPersisted(true).build())
            } catch (e: Exception) {
                Log.w("Porch", "Launcher row job not scheduled: ${e.javaClass.simpleName}")
            }
        }
    }
}
