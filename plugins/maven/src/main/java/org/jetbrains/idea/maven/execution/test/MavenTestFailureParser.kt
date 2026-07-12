// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.test

import com.intellij.openapi.util.text.StringUtil
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * Enriches Surefire failure/error elements with expected/actual data for comparison UI,
 * mirroring [org.jetbrains.plugins.gradle.execution.test.runner.events.GradleAssertionTestEventConverter].
 */
internal object MavenTestFailureParser {

  fun parse(message: String?, details: String?, isError: Boolean): MavenTestFailure {
    for (candidate in collectAssertionMessageCandidates(message, details)) {
      val comparisonResult = MavenAssertionMessageParser.parse(candidate) ?: continue
      val localizedMessage = comparisonResult.message ?: message
      val expected = resolveAssertionValue(comparisonResult.expected)
      val actual = resolveAssertionValue(comparisonResult.actual)
      return MavenTestFailure(
        message = localizedMessage,
        details = details,
        isError = isError,
        expectedText = expected.text,
        actualText = actual.text,
        expectedFile = expected.path,
        actualFile = actual.path,
      )
    }
    return MavenTestFailure(message = message, details = details, isError = isError)
  }

  private fun collectAssertionMessageCandidates(message: String?, details: String?): List<String> {
    val candidates = LinkedHashSet<String>()
    if (!message.isNullOrBlank()) {
      candidates += message
    }
    extractAssertionMessageFromDetails(details)?.let { candidates += it }
    extractAssertionBlockFromDetails(details)?.let { candidates += it }
    return candidates.toList()
  }

  private fun extractAssertionMessageFromDetails(details: String?): String? {
    if (details.isNullOrBlank()) {
      return null
    }
    val normalizedDetails = StringUtil.convertLineSeparators(details)
    val firstLine = normalizedDetails.lineSequence().first().trim()
    val separatorIndex = firstLine.indexOf(": ")
    if (separatorIndex >= 0) {
      return firstLine.substring(separatorIndex + 2).trim().takeIf { it.isNotEmpty() }
    }
    return firstLine
  }

  private fun extractAssertionBlockFromDetails(details: String?): String? {
    if (details.isNullOrBlank()) {
      return null
    }
    val normalizedDetails = StringUtil.convertLineSeparators(details)
    val stackTraceStart = normalizedDetails.indexOf("\n\tat ")
    val body = if (stackTraceStart >= 0) normalizedDetails.substring(0, stackTraceStart) else normalizedDetails
    val lines = body.lines().map { it.trimEnd() }
    val assertionStartIndex = lines.indexOfFirst { line ->
      line.startsWith("expected:") || line.startsWith("Expecting actual:")
    }
    if (assertionStartIndex >= 0) {
      return lines.subList(assertionStartIndex, lines.size).joinToString("\n").trim()
    }

    val separatorIndex = body.indexOf(": ")
    val assertionBlock = if (separatorIndex >= 0) {
      body.substring(separatorIndex + 2).trim()
    }
    else {
      body.trim()
    }
    return assertionBlock.takeIf { it.isNotEmpty() }
  }

  private fun resolveAssertionValue(assertionText: String): AssertionValue {
    val filePath = MavenAssertionValueParser.parse(assertionText)
    if (filePath != null) {
      val text = runCatching { Path.of(filePath).readText() }.getOrNull()
      if (text != null) {
        return AssertionValue(text, filePath)
      }
    }
    return AssertionValue(assertionText, null)
  }

  private class AssertionValue(
    val text: String,
    val path: String?,
  )
}
