package tv.porch.app.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SourceEntity::class,
        ChannelEntity::class,
        ProgrammeEntity::class,
        HiddenGroupEntity::class,
        ChosenGroupEntity::class,
        FilmCategoryEntity::class,
        FilmEntity::class,
        ResumePointEntity::class,
        ShowCategoryEntity::class,
        ShowEntity::class,
        ShowProgressEntity::class,
        FilmListingEntity::class,
        ShowListingEntity::class,
    ],
    version = PorchDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        // Adds programmes.iconUrl, nullable, with ALTER TABLE only. Nothing already saved is touched.
        AutoMigration(from = 4, to = 5),
        // Adds the Movies tables (film_categories, films, resume_points). New tables only.
        AutoMigration(from = 5, to = 6),
        // Adds the Series tables (show_categories, shows, show_progress). New tables only.
        AutoMigration(from = 6, to = 7),
        // Adds film_listings and show_listings (a title in every category the provider lists it under).
        // New tables only; ListingsFromTitles fills them from each title's stored category.
        AutoMigration(from = 7, to = 8, spec = PorchDatabase.ListingsFromTitles::class),
    ],
)
abstract class PorchDatabase : RoomDatabase() {
    abstract fun sources(): SourceDao
    abstract fun channels(): ChannelDao
    abstract fun programmes(): ProgrammeDao
    abstract fun hiddenGroups(): HiddenGroupDao
    abstract fun chosenGroups(): ChosenGroupDao
    abstract fun filmCategories(): FilmCategoryDao
    abstract fun films(): FilmDao
    abstract fun resumePoints(): ResumePointDao
    abstract fun showCategories(): ShowCategoryDao
    abstract fun shows(): ShowDao
    abstract fun showProgress(): ShowProgressDao

    /**
     * Version 8: every stored film and show gets one listing under the category it was stored with, the same
     * chip it showed in before, so nothing disappears and pruning keeps working. Nothing else is touched.
     */
    class ListingsFromTitles : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT OR IGNORE INTO film_listings (sourceId, categoryId, streamId, listedAt) " +
                    "SELECT sourceId, categoryId, streamId, listedAt FROM films",
            )
            db.execSQL(
                "INSERT OR IGNORE INTO show_listings (sourceId, categoryId, seriesId, listedAt) " +
                    "SELECT sourceId, categoryId, seriesId, listedAt FROM shows",
            )
        }
    }

    companion object {
        const val VERSION = 8

        // Changes an AutoMigration cannot express (renames, data moves) go here.
        val manualMigrations: Array<Migration> = emptyArray()

        fun create(context: Context): PorchDatabase {
            return Room.databaseBuilder(context.applicationContext, PorchDatabase::class.java, "porch.db")
                .addMigrations(*manualMigrations)
                // A downgrade is a developer installing an older build, so wiping there is fine.
                // An upgrade must never wipe: a missing migration crashes at open and leaves the file alone.
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
        }
    }
}
