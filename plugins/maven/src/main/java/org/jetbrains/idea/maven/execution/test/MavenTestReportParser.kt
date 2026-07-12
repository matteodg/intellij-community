// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.test

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.StringUtil.notNullize
import org.jdom.Element
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.math.roundToLong

object MavenTestReportParser {
  private val LOG = logger<MavenTestReportParser>()

  @JvmStatic
  fun parse(reportFile: Path): List<MavenTestSuiteResult> {
    return runCatching {
      val root = JDOMUtil.load(reportFile)
      when (root.name) {
        "testsuite" -> listOfNotNull(parseSuite(root, reportFile))
        "testsuites" -> root.getChildren("testsuite").mapNotNull { parseSuite(it, reportFile) }
        else -> emptyList()
      }
    }.getOrElse {
      LOG.warn("Failed to parse Maven test report: $reportFile", it)
      emptyList()
    }
  }

  private fun parseSuite(suiteElement: Element, reportFile: Path): MavenTestSuiteResult? {
    val testCases = suiteElement.getChildren("testcase").mapNotNull { parseTestCase(it, suiteElement) }
    if (testCases.isEmpty()) {
      return null
    }

    val className = findClassName(suiteElement, testCases, reportFile)
    return MavenTestSuiteResult(
      displayName = StringUtil.getShortName(className),
      className = className,
      durationMillis = parseDurationMillis(suiteElement.getAttributeValue("time")),
      testCases = testCases,
    )
  }

  private fun parseTestCase(testCaseElement: Element, suiteElement: Element): MavenTestCaseResult {
    val className = notNullize(testCaseElement.getAttributeValue("classname"), suiteElement.getAttributeValue("name"))
    val name = notNullize(testCaseElement.getAttributeValue("name"), className)
    val skippedElement = testCaseElement.getChild("skipped")
    if (skippedElement != null) {
      return MavenTestCaseResult(name, className, parseDurationMillis(testCaseElement.getAttributeValue("time")), MavenTestStatus.SKIPPED)
    }

    val errorElement = testCaseElement.getChild("error")
    if (errorElement != null) {
      return MavenTestCaseResult(
        name = name,
        className = className,
        durationMillis = parseDurationMillis(testCaseElement.getAttributeValue("time")),
        status = MavenTestStatus.ERROR,
        failure = parseFailure(
          firstNonEmpty(errorElement.getAttributeValue("message"), errorElement.getAttributeValue("type")),
          normalizeDetails(errorElement.text),
          isError = true,
        ),
      )
    }

    val failureElement = testCaseElement.getChild("failure")
    if (failureElement != null) {
      return MavenTestCaseResult(
        name = name,
        className = className,
        durationMillis = parseDurationMillis(testCaseElement.getAttributeValue("time")),
        status = MavenTestStatus.FAILED,
        failure = parseFailure(
          firstNonEmpty(failureElement.getAttributeValue("message"), failureElement.getAttributeValue("type")),
          normalizeDetails(failureElement.text),
          isError = false,
        ),
      )
    }

    return MavenTestCaseResult(name, className, parseDurationMillis(testCaseElement.getAttributeValue("time")), MavenTestStatus.PASSED)
  }

  private fun findClassName(suiteElement: Element, testCases: List<MavenTestCaseResult>, reportFile: Path): String {
    val firstClassName = testCases.firstOrNull()?.className
    if (!firstClassName.isNullOrBlank()) {
      return firstClassName
    }

    val suiteName = suiteElement.getAttributeValue("name")
    if (!suiteName.isNullOrBlank()) {
      return suiteName
    }

    return reportFile.name.removePrefix("TEST-").removeSuffix(".xml")
  }

  private fun parseFailure(message: String?, details: String?, isError: Boolean): MavenTestFailure {
    return MavenTestFailureParser.parse(message, details, isError)
  }

  private fun normalizeDetails(details: String?): String? {
    if (details.isNullOrBlank()) {
      return null
    }
    return details.trim()
  }

  private fun firstNonEmpty(first: String?, second: String?): String? {
    return when {
      !first.isNullOrBlank() -> first
      !second.isNullOrBlank() -> second
      else -> null
    }
  }

  private fun parseDurationMillis(value: String?): Long? {
    val seconds = value?.toDoubleOrNull() ?: return null
    return (seconds * 1000).roundToLong()
  }
}
