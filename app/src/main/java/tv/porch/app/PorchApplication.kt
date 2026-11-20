package tv.porch.app

import android.app.Application
import java.io.File
import coil.ImageLoader
import coil.ImageLoaderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tv.porch.app.data.BackupGuides
import tv.porch.app.data.ChannelFolds
import tv.porch.app.data.GuideFlags
import tv.porch.app.data.FollowStore
import tv.porch.app.data.ReminderStore
import tv.porch.app.data.HelpFlag
import tv.porch.app.data.SimpleModeStore
import tv.porch.app.data.HomeSnapshot
import tv.porch.app.data.LibraryRepository
import tv.porch.app.data.MovieRepository
import tv.porch.app.data.PorchDatabase
import tv.porch.app.data.ProblemStore
import tv.porch.app.data.PosterRatings
import tv.porch.app.data.ShowPosters
import tv.porch.app.data.ShowRepository
import tv.porch.app.data.SportSchedule
import tv.porch.app.data.SportScores
import tv.porch.app.data.SyncController
import tv.porch.app.data.SyncStatus
import tv.porch.app.data.UrlConnectionFetcher
import tv.porch.app.domain.LastChannel
import tv.porch.app.data.GuideRefreshJob
import tv.porch.app.launcher.LauncherRowJob
import tv.porch.app.launcher.LauncherRowSync
import tv.porch.app.player.PlaybackBus
import tv.porch.app.ui.components.LogoLoader

class PorchApplication : Application(), ImageLoaderFactory {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        LauncherRowJob.schedule(this)
        GuideRefreshJob.schedule(this)
    }

    /** Coil asks for this once; every channel logo shares the one loader. */
    override fun newImageLoader(): ImageLoader = LogoLoader.create(this)
}

class AppGraph(application: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val database: PorchDatabase = PorchDatabase.create(application)
    val backupGuides: BackupGuides = BackupGuides(application)
    val repository: LibraryRepository = LibraryRepository(database, UrlConnectionFetcher(), application.cacheDir, backupGuides)
    /** Movies for an Xtream source. Nothing runs until the Movies tab asks. */
    val movies: MovieRepository by lazy {
        MovieRepository(database, UrlConnectionFetcher(), application.cacheDir, application.getSharedPreferences("porch", android.content.Context.MODE_PRIVATE))
    }
    /** Series for an Xtream source. Nothing runs until the Series tab asks. */
    val shows: ShowRepository by lazy {
        ShowRepository(database, UrlConnectionFetcher(), application.cacheDir, application.getSharedPreferences("porch", android.content.Context.MODE_PRIVATE))
    }
    /** The sport schedule from ESPN. Nothing runs until the Sport tab asks. */
    val sport: SportSchedule by lazy { SportSchedule(UrlConnectionFetcher(), File(application.filesDir, "sport_schedule.json")) }
    /** Sport results and ladders from ESPN, kept apart from the schedule. Nothing runs until the Sport tab asks. */
    val scores: SportScores by lazy { SportScores(UrlConnectionFetcher(), File(application.filesDir, "sport_results.json"), File(application.filesDir, "sport_ladders.json")) }
    val playback: PlaybackBus = PlaybackBus()
    /** The film the Film route plays, held by token like [playback]. */
    val filmPlayback: tv.porch.app.player.FilmPlayback = tv.porch.app.player.FilmPlayback()
    /** The episode the Episode route plays, held by token like [filmPlayback]. */
    val episodePlayback: tv.porch.app.player.EpisodePlayback = tv.porch.app.player.EpisodePlayback()
    val sync: SyncController = SyncController(repository, scope, watching = { playback.watching }, problems = ProblemStore(application))
    val help: HelpFlag = HelpFlag(application)
    /** Simple mode (Dad's TV): favourites and the guide only, locked with a code. */
    val simpleMode: SimpleModeStore = SimpleModeStore(application)
    val guideFlags: GuideFlags = GuideFlags(application)
    /** Programmes the viewer asked to be reminded about. */
    val reminders: ReminderStore = ReminderStore(application)
    /** Names and titles the viewer follows, for Home's Following list. */
    val follows: FollowStore = FollowStore(application)
    val folds: ChannelFolds = ChannelFolds(repository, guideFlags)
    val posters: ShowPosters = ShowPosters(application)
    val ratings: PosterRatings = PosterRatings(application)
    /** What Home last showed, drawn at once on the next start. */
    val homeSnapshot: HomeSnapshot = HomeSnapshot(File(application.filesDir, "home_snapshot.json"), CoroutineScope(SupervisorJob() + Dispatchers.IO))
    /** The channel before the one playing, for the player's "Back to" row. */
    val lastChannel: LastChannel = LastChannel()

    /** The "Porch" row on the launcher. */
    val launcherRow: LauncherRowSync = LauncherRowSync(application, repository, folds)
    private val launcherScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // A guide refresh that has just finished may have changed what is on.
        launcherScope.launch {
            var wasRunning = false
            sync.status.collect { status ->
                val running = status.values.any { it is SyncStatus.Running }
                if (wasRunning && !running) launcherRow.update()
                wasRunning = running
            }
        }
    }

    /** Brings the launcher row up to date in the background. */
    fun updateLauncherRow() {
        launcherScope.launch { launcherRow.update() }
    }
}
