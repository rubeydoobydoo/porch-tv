package tv.porch.app.data

import androidx.room.migration.AutoMigrationSpec
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SchemaGuardTest {
    @Test
    fun everyVersionHasASchemaAndAMigrationPath() {
        val version = PorchDatabase.VERSION
        // Room's @Database annotation is not kept at run time, so ask the class Room generates from it.
        val impl = Class.forName("tv.porch.app.data.PorchDatabase_Impl")
            .getDeclaredConstructor().newInstance() as PorchDatabase
        val specs = impl.getRequiredAutoMigrationSpecClasses().associateWith { spec ->
            spec.java.getDeclaredConstructor().newInstance() as AutoMigrationSpec
        }
        val autoMigrations = impl.createAutoMigrations(specs)

        val folder = listOf(
            "app/schemas/tv.porch.app.data.PorchDatabase",
            "schemas/tv.porch.app.data.PorchDatabase",
        ).map { File(it) }.firstOrNull { it.isDirectory }
        assertNotNull("No schema folder found (looked in app/schemas and schemas)", folder)
        val schemaVersions = folder!!.listFiles().orEmpty()
            .filter { it.extension == "json" }
            .mapNotNull { it.nameWithoutExtension.toIntOrNull() }
            .sorted()
        assertEquals(
            "Schema files must be exactly 1..$version; bump the version, run ./gradlew kspDebugKotlin, and commit the new JSON",
            (1..version).toList(),
            schemaVersions,
        )

        for (k in 1 until version) {
            val auto = autoMigrations.any { it.startVersion == k && it.endVersion == k + 1 }
            val manual = PorchDatabase.manualMigrations.any { it.startVersion == k && it.endVersion == k + 1 }
            assertTrue(
                "Version $k to ${k + 1} has no migration; add an AutoMigration or a Migration in PorchDatabase.manualMigrations",
                auto || manual,
            )
        }
    }
}
