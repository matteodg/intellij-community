// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.test

import com.intellij.build.BuildViewSettingsProvider
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView

class MavenTestsExecutionConsole(
  consoleProperties: TestConsoleProperties,
  splitterProperty: String?,
) : SMTRunnerConsoleView(consoleProperties, splitterProperty), BuildViewSettingsProvider {
  val eventPublisher: SMTRunnerEventsListener =
    consoleProperties.project.messageBus.syncPublisher(SMTRunnerEventsListener.TEST_STATUS)

  override fun isExecutionViewHidden(): Boolean = true
}
