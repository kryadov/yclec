package com.kayar.jclec

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.type.TypeFactory
import com.fasterxml.jackson.core.type.TypeReference
import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.DefaultRepositorySystemSession
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.ArtifactRequest
import org.eclipse.aether.resolution.ArtifactResolutionException
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URL
import java.util.jar.JarFile

// Data classes for JSON structure
data class Component(
    val component: String = "",
    val maven: String? = null,
    val vulnerableClasses: List<String> = emptyList()
)

class MavenVerifier {
    private val logger = LoggerFactory.getLogger(MavenVerifier::class.java)
    private val repositorySystem: RepositorySystem
    private val session: DefaultRepositorySystemSession
    private val remoteRepos: List<RemoteRepository>

    init {
        // Set up Maven repository system
        val locator = MavenRepositorySystemUtils.newServiceLocator()
        locator.addService(RepositoryConnectorFactory::class.java, BasicRepositoryConnectorFactory::class.java)
        locator.addService(TransporterFactory::class.java, HttpTransporterFactory::class.java)
        repositorySystem = locator.getService(RepositorySystem::class.java)

        // Set up repository session
        session = MavenRepositorySystemUtils.newSession()
        val localRepo = LocalRepository(System.getProperty("user.home") + "/.m2/repository")
        session.localRepositoryManager = repositorySystem.newLocalRepositoryManager(session, localRepo)

        // Set up remote repositories
        remoteRepos = listOf(
            RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/").build(),
            RemoteRepository.Builder("jcenter", "default", "https://jcenter.bintray.com").build()
        )
    }

    fun verifyClassInArtifact(coordinates: String, className: String): Boolean {
        try {
            // Parse Maven coordinates
            val parts = coordinates.split(":")
            if (parts.size < 2) {
                logger.error("Invalid Maven coordinates: $coordinates")
                return false
            }

            val groupId = parts[0]
            val artifactId = parts[1]
            val version = if (parts.size > 2) parts[2] else "LATEST"

            // Create artifact
            val artifact = DefaultArtifact(groupId, artifactId, "jar", version)

            // Create request
            val request = ArtifactRequest()
            request.artifact = artifact
            request.repositories = remoteRepos

            // Resolve artifact
            val result = repositorySystem.resolveArtifact(session, request)
            val file = result.artifact.file

            // Check if class exists in the JAR
            return checkClassInJar(file, className)
        } catch (e: ArtifactResolutionException) {
            logger.error("Failed to resolve artifact: ${e.message}")
            return false
        } catch (e: Exception) {
            logger.error("Error verifying class: ${e.message}")
            return false
        }
    }

    private fun checkClassInJar(file: File, className: String): Boolean {
        try {
            JarFile(file).use { jar ->
                val classPath = className.replace('.', '/') + ".class"
                return jar.getJarEntry(classPath) != null
            }
        } catch (e: Exception) {
            logger.error("Error checking class in JAR: ${e.message}")
            return false
        }
    }

    fun searchForClass(className: String): List<String> {
        val results = mutableListOf<String>()

        try {
            // Use Maven Central Search API
            val searchUrl = URL("https://search.maven.org/solrsearch/select?q=fc:\"$className\"&rows=20&wt=json")
            val connection = searchUrl.openConnection()
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")

            val response = connection.getInputStream().bufferedReader().use { it.readText() }

            // Parse JSON response (simplified for brevity)
            // In a real implementation, you would parse the JSON properly
            val regex = "\"id\":\"([^\"]+)\"".toRegex()
            val matches = regex.findAll(response)

            matches.forEach { match ->
                val id = match.groupValues[1]
                results.add(id)
            }
        } catch (e: Exception) {
            logger.error("Error searching for class: ${e.message}")
        }

        return results
    }
}

fun main() {
    val logger = LoggerFactory.getLogger("Main")
    logger.info("Starting vulnerability verification app")

    try {
        // Read and parse JSON file
        val mapper = ObjectMapper().registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build())
        val inputStream = object {}.javaClass.getResourceAsStream("/jc_dataset.json")
            ?: throw IllegalArgumentException("Could not find jc_dataset.json in resources")

        val typeReference = object : TypeReference<List<Component>>() {}
        val components: List<Component> = mapper.readValue(inputStream, typeReference)
        logger.info("Loaded ${components.size} components from JSON")

        // Create Maven verifier
        val verifier = MavenVerifier()

        // Process each component
        components.forEach { component ->
            println("\nVerifying component: ${component.component}")

            component.vulnerableClasses.forEach { className ->
                println("  Checking class: $className")

                // If Maven coordinates are provided, try to verify directly
                if (!component.maven.isNullOrBlank()) {
                    val exists = verifier.verifyClassInArtifact(component.maven, className)
                    if (exists) {
                        println("  ✓ Class found in ${component.maven}")
                    } else {
                        println("  ✗ Class not found in ${component.maven}")

                        // If direct verification fails, try searching
                        println("  Searching for class in Maven Central...")
                        val searchResults = verifier.searchForClass(className)

                        if (searchResults.isNotEmpty()) {
                            println("  Found in the following artifacts:")
                            searchResults.forEach { artifact ->
                                println("    - $artifact")
                            }
                        } else {
                            println("  Not found in Maven Central search")
                        }
                    }
                } else {
                    // If no Maven coordinates, try searching
                    println("  No Maven coordinates provided, searching...")
                    val searchResults = verifier.searchForClass(className)

                    if (searchResults.isNotEmpty()) {
                        println("  Found in the following artifacts:")
                        searchResults.forEach { artifact ->
                            println("    - $artifact")
                        }
                    } else {
                        println("  Not found in Maven Central search")
                    }
                }
            }
        }
    } catch (e: Exception) {
        logger.error("Error: ${e.message}", e)
    }
}
