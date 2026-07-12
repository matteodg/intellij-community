// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.test

import com.intellij.execution.Executor
import com.intellij.execution.Location
import com.intellij.execution.testframework.JavaAwareTestConsoleProperties
import com.intellij.execution.testframework.JavaTestLocator
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerTestTreeView
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerTestTreeViewProvider
import com.intellij.pom.Navigatable
import org.jetbrains.idea.maven.execution.MavenRunConfiguration
import org.jetbrains.idea.maven.utils.MavenUtil
import javax.swing.tree.TreeSelectionModel

class MavenTestConsoleProperties(
  configuration: MavenRunConfiguration,
  executor: Executor,
) : SMTRunnerConsoleProperties(configuration, MavenUtil.SYSTEM_ID.readableName, executor), SMTRunnerTestTreeViewProvider {
  override fun getSelectionMode(): Int = TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION

  override fun getErrorNavigatable(location: Location<*>, stacktrace: String): Navigatable? {
    return JavaAwareTestConsoleProperties.getStackTraceErrorNavigatable(location, stacktrace)
  }

  override fun getTestLocator(): SMTestLocator {
    return JavaTestLocator.INSTANCE
  }

  override fun isEditable(): Boolean = true

  override fun createSMTRunnerTestTreeView(): SMTRunnerTestTreeView {
    return SMTRunnerTestTreeView()
  }
}
