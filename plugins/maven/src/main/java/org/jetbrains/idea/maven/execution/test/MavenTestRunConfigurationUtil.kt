// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.test

import org.jetbrains.idea.maven.execution.MavenRunConfiguration
import java.util.Locale

object MavenTestRunConfigurationUtil {
  /**
   * Default lifecycle phases that run the `test` phase (Surefire) when reached.
   * See https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html
   */
  private val LIFECYCLE_PHASES_RUNNING_TEST = setOf(
    "test",
    "prepare-package",
    "package",
    "pre-integration-test",
    "integration-test",
    "post-integration-test",
    "verify",
    "install",
    "deploy",
  )

  private val PLUGIN_TEST_GOALS = setOf(
    "surefire:test",
    "failsafe:integration-test",
    "failsafe:verify",
  )

  @JvmStatic
  fun isTestRun(configuration: MavenRunConfiguration): Boolean {
    return hasTestGoals(configuration.runnerParameters.goals)
  }

  @JvmStatic
  fun hasTestGoals(goals: Collection<String>): Boolean {
    return goals.any(::isTestGoal)
  }

  @JvmStatic
  fun isTestGoal(goal: String): Boolean {
    val normalizedGoal = goal.trim().lowercase(Locale.ROOT)
    return normalizedGoal in LIFECYCLE_PHASES_RUNNING_TEST || normalizedGoal in PLUGIN_TEST_GOALS
  }
}
