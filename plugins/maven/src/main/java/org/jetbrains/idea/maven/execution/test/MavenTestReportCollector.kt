// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.test

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.idea.maven.execution.MavenRunConfiguration
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isDirectory
import kotlin.streams.asSequence

object MavenTestReportCollector {
  private val LOG = logger<MavenTestReportCollector>()
  private val REPORT_DIRECTORIES = listOf("target/surefire-reports", "target/failsafe-reports")

  @JvmStatic
  fun collect(
    configuration: MavenRunConfiguration,
    parsingContext: MavenParsingContext,
    startedAt: Long,
  ): List<MavenTestModuleResult> {
    return findCandidateProjects(configuration, parsingContext).mapNotNull { project ->
      val suites = collectProjectReports(project, startedAt)
      if (suites.isEmpty()) {
        null
      }
      else {
        MavenTestModuleResult(
          displayName = project.mavenId.artifactId,
          path = project.directory,
          suites = suites,
        )
      }
    }
  }

  private fun findCandidateProjects(configuration: MavenRunConfiguration, parsingContext: MavenParsingContext): List<MavenProject> {
    val manager = MavenProjectsManager.getInstance(configuration.project)
    val result = LinkedHashMap<String, MavenProject>()
    val allProjects = manager.projects

    parsingContext.startedProjects.forEach { key ->
      allProjects.firstOrNull { projectKey(it.mavenId) == key }?.let { result.putIfAbsent(it.directory, it) }
    }

    runCatching { parsingContext.projectsInReactor }.getOrNull().orEmpty().forEach { projectId ->
      val mavenId = toMavenId(projectId) ?: return@forEach
      manager.findProject(mavenId)?.let { result.putIfAbsent(it.directory, it) }
    }

    if (result.isNotEmpty()) {
      return result.values.sortedBy { it.directory }
    }

    val pomFileName = configuration.runnerParameters.pomFileName ?: "pom.xml"
    val projectPath = Paths.get(configuration.runnerParameters.workingDirPath).resolve(pomFileName)
    val virtualPom = LocalFileSystem.getInstance().findFileByNioFile(projectPath) ?: return emptyList()
    return listOfNotNull(manager.findProject(virtualPom))
  }

  private fun collectProjectReports(project: MavenProject, startedAt: Long): List<MavenTestSuiteResult> {
    val reportDirectories = REPORT_DIRECTORIES.map { Paths.get(project.directory, it) }.filter { it.isDirectory() }
    return reportDirectories.asSequence()
      .flatMap { reportDirectory -> collectReportFiles(reportDirectory, startedAt).asSequence() }
      .flatMap { reportFile -> MavenTestReportParser.parse(reportFile).asSequence() }
      .toList()
  }

  private fun collectReportFiles(reportDirectory: Path, startedAt: Long): List<Path> {
    if (!reportDirectory.isDirectory()) {
      return emptyList()
    }

    return try {
      Files.list(reportDirectory).use { files ->
        files.asSequence()
          .filter { file -> Files.isRegularFile(file) }
          .filter { file -> file.fileName.toString().startsWith("TEST-") && file.fileName.toString().endsWith(".xml") }
          .filter { file -> Files.getLastModifiedTime(file).toMillis() >= startedAt }
          .sortedBy { it.fileName.toString() }
          .toList()
      }
    }
    catch (e: Exception) {
      LOG.warn("Failed to collect Maven test reports from $reportDirectory", e)
      emptyList()
    }
  }

  private fun projectKey(mavenId: MavenId): String {
    return "${mavenId.groupId}:${mavenId.artifactId}"
  }

  private fun toMavenId(projectId: String): MavenId? {
    val components = projectId.split(':')
    if (components.size < 3) {
      return null
    }
    return MavenId(components[0], components[1], components[2])
  }
}
