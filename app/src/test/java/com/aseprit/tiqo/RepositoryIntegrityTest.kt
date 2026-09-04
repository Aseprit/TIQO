package com.aseprit.tiqo

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class RepositoryIntegrityTest {
    @Test fun gitignoreNeverIgnoresFeatureModelsSourceDirectory() {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        val root = generateSequence(File(workingDirectory)) { it.parentFile }
            .firstOrNull { File(it, ".gitignore").isFile }
        assertNotNull("Nie znaleziono repozytorium", root)
        val rules = File(root, ".gitignore").readLines()
            .map { it.substringBefore('#').trim() }
            .filter(String::isNotBlank)

        assertFalse("Ogólna reguła models/ ignoruje source code", rules.contains("models/"))
        assertFalse(rules.contains("**/models/"))
        assertFalse(rules.contains("app/src/main/java/com/aseprit/tiqo/feature/models/"))
    }
}
