package com.cosmiclaboratory.voyager.pipeline

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Enforces the storage seam established by H2: **no file in `pipeline/` may
 * import a Room DAO or storage entity.**
 *
 * The pipeline talks to persistence only through [PipelineGateway]; its impl
 * lives in `data/` and is the one place Room is mapped. If a future change
 * reaches back into `storage.database.*` from anywhere in `pipeline/`, this
 * test fails in CI with a precise file list — the fix path is "route it
 * through PipelineGateway."
 */
class PipelineGatewayBoundaryTest {

    @Test
    fun `pipeline package does not import storage DAOs or entities`() {
        val root = File("src/main/java/com/cosmiclaboratory/voyager/pipeline")
        check(root.isDirectory) { "pipeline source dir missing at ${root.absolutePath} (CWD=${File("").absolutePath})" }

        val violations: List<String> = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines().asSequence()
                    .map { it.trim() }
                    .filter {
                        it.startsWith("import com.cosmiclaboratory.voyager.storage.database.dao.") ||
                            it.startsWith("import com.cosmiclaboratory.voyager.storage.database.entity.")
                    }
                    .map { "${file.relativeTo(root)}: $it" }
            }
            .toList()

        assertEquals(
            "pipeline/ boundary broken — re-route these through PipelineGateway:",
            emptyList<String>(), violations
        )
    }
}
