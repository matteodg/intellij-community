// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.test

enum class MavenTestStatus {
  PASSED,
  FAILED,
  ERROR,
  SKIPPED,
}

data class MavenTestFailure(
  val message: String?,
  val details: String?,
  val isError: Boolean,
  val expectedText: String? = null,
  val actualText: String? = null,
  val expectedFile: String? = null,
  val actualFile: String? = null,
) {
  val isComparisonFailure: Boolean
    get() = expectedText != null && actualText != null
}

data class MavenTestCaseResult(
  val name: String,
  val className: String,
  val durationMillis: Long?,
  val status: MavenTestStatus,
  val failure: MavenTestFailure? = null,
)

data class MavenTestSuiteResult(
  val displayName: String,
  val className: String,
  val durationMillis: Long?,
  val testCases: List<MavenTestCaseResult>,
) {
  val status: MavenTestStatus
    get() = aggregateStatus(testCases.map { it.status })
}

data class MavenTestModuleResult(
  val displayName: String,
  val path: String,
  val suites: List<MavenTestSuiteResult>,
) {
  val durationMillis: Long
    get() = suites.sumOf { it.durationMillis ?: 0L }

  val status: MavenTestStatus
    get() = aggregateStatus(suites.map { it.status })
}

internal fun aggregateStatus(statuses: List<MavenTestStatus>): MavenTestStatus {
  return when {
    statuses.any { it == MavenTestStatus.ERROR } -> MavenTestStatus.ERROR
    statuses.any { it == MavenTestStatus.FAILED } -> MavenTestStatus.FAILED
    statuses.isNotEmpty() && statuses.all { it == MavenTestStatus.SKIPPED } -> MavenTestStatus.SKIPPED
    else -> MavenTestStatus.PASSED
  }
}
