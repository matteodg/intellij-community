// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.test

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MavenTestRunConfigurationUtilTest {
  @Test
  fun `recognizes supported Maven test goals`() {
    assertTrue(MavenTestRunConfigurationUtil.hasTestGoals(listOf("clean", "test")))
    assertTrue(MavenTestRunConfigurationUtil.hasTestGoals(listOf("verify")))
    assertTrue(MavenTestRunConfigurationUtil.hasTestGoals(listOf("install")))
    assertTrue(MavenTestRunConfigurationUtil.hasTestGoals(listOf("package")))
    assertTrue(MavenTestRunConfigurationUtil.hasTestGoals(listOf("surefire:test")))
    assertTrue(MavenTestRunConfigurationUtil.hasTestGoals(listOf("failsafe:integration-test")))
    assertTrue(MavenTestRunConfigurationUtil.hasTestGoals(listOf("failsafe:verify")))
  }

  @Test
  fun `ignores non test Maven goals`() {
    assertFalse(MavenTestRunConfigurationUtil.hasTestGoals(listOf("clean", "compile")))
  }
}
