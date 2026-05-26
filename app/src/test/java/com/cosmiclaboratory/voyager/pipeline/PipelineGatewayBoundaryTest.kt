package com.cosmiclaboratory.voyager.pipeline

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Narrow guard for the storage seam established by H2 (the `PipelineGateway`).
 *
 * The two files that define the seam — [PipelineGateway] and [PipelineConsumer] —
 * MUST NOT import Room DAOs or storage entities. That's the whole point of the
 * gateway: the pipeline asks for typed projections, the impl in `data/` does the
 * mapping. If a future change reaches back into `storage.database.*` from
 * either of these files, this test fails fast in CI.
 *
 * (Broader H6 — clean the rest of `pipeline/` of DAO imports — also requires
 * refactoring `Segmenter` and `PlaceLinkingService`, tracked as follow-up.)
 */
class PipelineGatewayBoundaryTest {

    @Test
    fun `PipelineConsumer does not import storage DAOs or entities`() {
        assertNoStorageImports(sourceFile("pipeline/PipelineConsumer.kt"))
    }

    @Test
    fun `PipelineGateway interface does not import storage DAOs or entities`() {
        assertNoStorageImports(sourceFile("pipeline/PipelineGateway.kt"))
    }

    private fun assertNoStorageImports(file: File) {
        val violations = file.readLines()
            .map { it.trim() }
            .filter {
                it.startsWith("import com.cosmiclaboratory.voyager.storage.database.dao.") ||
                    it.startsWith("import com.cosmiclaboratory.voyager.storage.database.entity.")
            }
        assertEquals(
            "Pipeline-gateway boundary broken in ${file.name}: re-route this through PipelineGateway.",
            emptyList<String>(), violations
        )
    }

    private fun sourceFile(relative: String): File {
        // Tests run with module dir as CWD (Gradle's default); main sources live under src/main/java.
        val root = File("src/main/java/com/cosmiclaboratory/voyager")
        val f = File(root, relative)
        check(f.exists()) { "Source not found at ${f.absolutePath} (CWD=${File("").absolutePath})" }
        return f
    }
}
