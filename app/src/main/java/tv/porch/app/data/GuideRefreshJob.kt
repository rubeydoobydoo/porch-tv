package tv.porch.app.data

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tv.porch.app.PorchApplication

/** Updates an old guide with or without the app open, so nobody has to press Refresh. It never wakes the screen. */
class GuideRefreshJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val graph = (application as PorchApplication).graph
        running = scope.launch {
            try {
                Log.i("Porch", "guide job: ${graph.sync.refreshDue()} due")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.w("Porch", "Guide job failed: ${e.javaClass.simpleName}")
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
        private const val JOB_ID = 7802

        // Half the age limit, so a guide is never more than about 9 hours old.
        private const val EVERY_MS = 3L * 60L * 60L * 1000L

        /** Idempotent: a job already waiting under this id is left alone. */
        fun schedule(context: Context) {
            try {
                val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
                if (scheduler.getPendingJob(JOB_ID) != null) return
                scheduler.schedule(
                    JobInfo.Builder(JOB_ID, ComponentName(context, GuideRefreshJob::class.java))
                        .setPeriodic(EVERY_MS)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setPersisted(true)
                        .build(),
                )
            } catch (e: Exception) {
                Log.w("Porch", "Guide job not scheduled: ${e.javaClass.simpleName}")
            }
        }
    }
}
