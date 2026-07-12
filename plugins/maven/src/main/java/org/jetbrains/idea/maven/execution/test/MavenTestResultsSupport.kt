// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.test

import com.intellij.build.BuildDescriptor
import com.intellij.build.BuildViewSettingsProviderAdapter
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.events.StartBuildEvent
import com.intellij.build.events.impl.StartBuildEventImpl
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.JavaTestLocator
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm
import com.intellij.execution.testframework.sm.runner.ui.SMRootTestProxyFormatter
import com.intellij.execution.testframework.sm.runner.ui.TestTreeRenderer
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.util.ObjectUtils
import org.jetbrains.idea.maven.execution.MavenRunConfiguration
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext
import org.jetbrains.idea.maven.project.MavenConsoleFilterProvider
import org.jetbrains.idea.maven.utils.MavenUtil
import java.util.function.Function

object MavenTestResultsSupport {
  @JvmStatic
  fun configureBuildDescriptor(descriptor: DefaultBuildDescriptor) {
    descriptor.setActivateToolWindowWhenAdded(false)
    descriptor.setActivateToolWindowWhenFailed(false)
    descriptor.isAutoFocusContent = false
  }

  @JvmStatic
  fun createStartBuildEventSupplier(
    descriptor: BuildDescriptor,
    console: MavenTestsExecutionConsole,
  ): Function<MavenParsingContext, StartBuildEvent> {
    val viewSettingsProvider = BuildViewSettingsProviderAdapter(console)
    return Function {
      StartBuildEventImpl(descriptor, "").withBuildViewSettingsProvider(viewSettingsProvider)
    }
  }

  @JvmStatic
  fun createConsole(environment: ExecutionEnvironment, configuration: MavenRunConfiguration): MavenTestsExecutionConsole {
    val consoleProperties = MavenTestConsoleProperties(configuration, environment.executor)
    val testFrameworkName = MavenUtil.SYSTEM_ID.readableName
    val splitterPropertyName = SMTestRunnerConnectionUtil.getSplitterPropertyName(testFrameworkName)
    val console = MavenTestsExecutionConsole(consoleProperties, splitterPropertyName)
    SMTestRunnerConnectionUtil.initConsoleView(console, testFrameworkName)
    MavenConsoleFilterProvider().getDefaultFilters(configuration.project).forEach(console::addMessageFilter)
    configureEmptySuiteText(console)

    val testsRootNode = console.resultsViewer.testsRootNode
    testsRootNode.setExecutionId(environment.executionId)
    testsRootNode.setSuiteStarted()
    console.eventPublisher.onTestingStarted(testsRootNode)
    return console
  }

  @JvmStatic
  fun createResultsListener(
    console: MavenTestsExecutionConsole,
    configuration: MavenRunConfiguration,
    parsingContextProvider: () -> MavenParsingContext,
    startedAt: Long,
  ): ProcessListener {
    return object : ProcessListener {
      override fun processTerminated(event: ProcessEvent) {
        invokeAndWaitIfNeeded {
          val parsingContext = parsingContextProvider()
          val reportResults = MavenTestReportCollector.collect(configuration, parsingContext, startedAt)
          val presenter = MavenTestResultsPresenter(console)
          presenter.present(reportResults, event.exitCode)
        }
      }
    }
  }

  private fun configureEmptySuiteText(console: MavenTestsExecutionConsole) {
    val resultsViewer = console.resultsViewer
    val testTreeView = resultsViewer.treeView ?: return
    val originalRenderer = ObjectUtils.tryCast(testTreeView.cellRenderer, TestTreeRenderer::class.java) ?: return
    originalRenderer.setAdditionalRootFormatter(object : SMRootTestProxyFormatter {
      override fun format(testProxy: SMTestProxy.SMRootTestProxy, renderer: TestTreeRenderer) {
        if (!testProxy.isInProgress && testProxy.isEmptySuite) {
          renderer.clear()
          renderer.append("No tests were found")
        }
      }
    })
  }
}

private class MavenTestResultsPresenter(
  private val console: MavenTestsExecutionConsole,
) {
  private val resultsViewer: SMTestRunnerResultsForm = console.resultsViewer
  private val rootNode: SMTestProxy.SMRootTestProxy = resultsViewer.testsRootNode

  fun present(moduleResults: List<MavenTestModuleResult>, exitCode: Int) {
    if (moduleResults.isEmpty()) {
      if (exitCode == 0) {
        rootNode.setFinished()
      }
      else {
        rootNode.setTestFailed("", null, false)
      }
      resultsViewer.onBeforeTestingFinished(rootNode)
      resultsViewer.onTestingFinished(rootNode)
      return
    }

    val withModuleNodes = moduleResults.size > 1
    val rootStatus = aggregateStatus(moduleResults.map { it.status })
    val rootDuration = moduleResults.sumOf { it.durationMillis }
    moduleResults.forEach { moduleResult ->
      if (withModuleNodes) {
        val moduleProxy = createSuiteProxy(moduleResult.displayName, null)
        startSuite(rootNode, moduleProxy)
        moduleResult.suites.forEach { suite -> presentSuite(moduleProxy, suite) }
        finishSuite(moduleProxy, moduleResult.status, moduleResult.durationMillis)
      }
      else {
        moduleResult.suites.forEach { suite -> presentSuite(rootNode, suite) }
      }
    }

    if (rootDuration > 0) {
      rootNode.setDuration(rootDuration)
    }
    when (rootStatus) {
      MavenTestStatus.PASSED -> rootNode.setFinished()
      MavenTestStatus.SKIPPED -> rootNode.setTestIgnored(null, null)
      MavenTestStatus.FAILED, MavenTestStatus.ERROR -> rootNode.setTestFailed("", null, rootStatus == MavenTestStatus.ERROR)
    }
    resultsViewer.onBeforeTestingFinished(rootNode)
    resultsViewer.onTestingFinished(rootNode)
  }

  private fun presentSuite(parent: SMTestProxy, suite: MavenTestSuiteResult) {
    val suiteProxy = createSuiteProxy(suite.displayName, suite.className)
    startSuite(parent, suiteProxy)

    suite.testCases.forEach { testCase ->
      val testProxy = SMTestProxy(
        testCase.name,
        false,
        JavaTestLocator.createLocationUrl(JavaTestLocator.TEST_PROTOCOL, testCase.className, testCase.name),
      )
      testProxy.setLocator(JavaTestLocator.INSTANCE)
      suiteProxy.addChild(testProxy)
      testProxy.setStarted()
      resultsViewer.onTestStarted(testProxy)
      console.eventPublisher.onTestStarted(testProxy)
      testCase.durationMillis?.let(testProxy::setDuration)
      when (testCase.status) {
        MavenTestStatus.PASSED -> {
          testProxy.setFinished()
          resultsViewer.onTestFinished(testProxy)
          console.eventPublisher.onTestFinished(testProxy)
        }
        MavenTestStatus.SKIPPED -> {
          testProxy.setTestIgnored(null, null)
          resultsViewer.onTestIgnored(testProxy)
          console.eventPublisher.onTestIgnored(testProxy)
          resultsViewer.onTestFinished(testProxy)
          console.eventPublisher.onTestFinished(testProxy)
        }
        MavenTestStatus.FAILED, MavenTestStatus.ERROR -> {
          val failure = testCase.failure
          if (failure?.isComparisonFailure == true) {
            testProxy.setTestComparisonFailed(
              failure.message,
              failure.details,
              failure.actualText!!,
              failure.expectedText!!,
              failure.actualFile,
              failure.expectedFile,
              true,
            )
          }
          else {
            testProxy.setTestFailed(failure?.message, failure?.details, testCase.status == MavenTestStatus.ERROR)
          }
          resultsViewer.onTestFailed(testProxy)
          console.eventPublisher.onTestFailed(testProxy)
          resultsViewer.onTestFinished(testProxy)
          console.eventPublisher.onTestFinished(testProxy)
        }
      }
    }

    finishSuite(suiteProxy, suite.status, suite.durationMillis ?: 0L)
  }

  private fun createSuiteProxy(displayName: String, className: String?): SMTestProxy {
    val locationUrl = className?.let { JavaTestLocator.createLocationUrl(JavaTestLocator.SUITE_PROTOCOL, it) }
    return SMTestProxy(displayName, true, locationUrl).also {
      it.setLocator(JavaTestLocator.INSTANCE)
    }
  }

  private fun startSuite(parent: SMTestProxy, suiteProxy: SMTestProxy) {
    parent.addChild(suiteProxy)
    suiteProxy.setStarted()
    resultsViewer.onSuiteStarted(suiteProxy)
    console.eventPublisher.onSuiteStarted(suiteProxy)
  }

  private fun finishSuite(suiteProxy: SMTestProxy, status: MavenTestStatus, durationMillis: Long) {
    if (durationMillis > 0) {
      suiteProxy.setDuration(durationMillis)
    }
    when (status) {
      MavenTestStatus.PASSED -> suiteProxy.setFinished()
      MavenTestStatus.SKIPPED -> suiteProxy.setTestIgnored(null, null)
      MavenTestStatus.FAILED, MavenTestStatus.ERROR -> suiteProxy.setTestFailed("", null, status == MavenTestStatus.ERROR)
    }
    resultsViewer.onSuiteFinished(suiteProxy)
    console.eventPublisher.onSuiteFinished(suiteProxy)
  }
}
