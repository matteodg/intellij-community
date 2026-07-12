// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.writeText

class MavenTestReportParserTest {
  @Test
  fun `parses surefire report with mixed test results`() {
    val report = Files.createTempFile("TEST-ExampleTest", ".xml")
    report.writeText(
      """
      <testsuite name="com.example.ExampleTest" tests="4" failures="1" errors="1" skipped="1" time="0.321">
        <testcase classname="com.example.ExampleTest" name="passes" time="0.100"/>
        <testcase classname="com.example.ExampleTest" name="fails" time="0.050">
          <failure message="expected:&lt;1&gt; but was:&lt;2&gt;" type="junit.framework.AssertionFailedError">stack trace line</failure>
        </testcase>
        <testcase classname="com.example.ExampleTest" name="errors" time="0.070">
          <error message="boom" type="java.lang.IllegalStateException">error details</error>
        </testcase>
        <testcase classname="com.example.ExampleTest" name="skipped" time="0.000">
          <skipped/>
        </testcase>
      </testsuite>
      """.trimIndent(),
    )

    val suite = MavenTestReportParser.parse(report).single()

    assertEquals("ExampleTest", suite.displayName)
    assertEquals("com.example.ExampleTest", suite.className)
    assertEquals(321L, suite.durationMillis)
    assertEquals(MavenTestStatus.ERROR, suite.status)
    assertEquals(listOf(MavenTestStatus.PASSED, MavenTestStatus.FAILED, MavenTestStatus.ERROR, MavenTestStatus.SKIPPED),
                 suite.testCases.map { it.status })
    assertEquals("expected:<1> but was:<2>", suite.testCases[1].failure?.message)
    assertEquals("1", suite.testCases[1].failure?.expectedText)
    assertEquals("2", suite.testCases[1].failure?.actualText)
    assertEquals(true, suite.testCases[1].failure?.isComparisonFailure)
    assertEquals("boom", suite.testCases[2].failure?.message)
    assertNotNull(suite.testCases[2].failure?.details)
  }

  @Test
  fun `parses testsuites wrapper`() {
    val report = Files.createTempFile("TEST-Grouped", ".xml")
    report.writeText(
      """
      <testsuites>
        <testsuite name="com.example.FirstTest">
          <testcase classname="com.example.FirstTest" name="first"/>
        </testsuite>
        <testsuite name="com.example.SecondTest">
          <testcase classname="com.example.SecondTest" name="second"/>
        </testsuite>
      </testsuites>
      """.trimIndent(),
    )

    val suites = MavenTestReportParser.parse(report)

    assertEquals(listOf("FirstTest", "SecondTest"), suites.map { it.displayName })
  }

  @Test
  fun `parses junit 5 comparison failure from message attribute`() {
    val report = Files.createTempFile("TEST-Junit5Test", ".xml")
    report.writeText(
      """
      <testsuite name="com.example.Junit5Test" tests="1" failures="1" errors="0" skipped="0" time="0.010">
        <testcase classname="com.example.Junit5Test" name="fails" time="0.010">
          <failure message="assertion message ==&gt; expected: &lt;expected&gt; but was: &lt;actual&gt;" type="org.opentest4j.AssertionFailedError">stack trace</failure>
        </testcase>
      </testsuite>
      """.trimIndent(),
    )

    val failure = MavenTestReportParser.parse(report).single().testCases.single().failure

    assertEquals("assertion message", failure?.message)
    assertEquals("expected", failure?.expectedText)
    assertEquals("actual", failure?.actualText)
    assertEquals(true, failure?.isComparisonFailure)
  }

  @Test
  fun `parses comparison failure from stack trace first line`() {
    val report = Files.createTempFile("TEST-BodyTest", ".xml")
    report.writeText(
      """
      <testsuite name="com.example.BodyTest" tests="1" failures="1" errors="0" skipped="0" time="0.010">
        <testcase classname="com.example.BodyTest" name="fails" time="0.010">
          <failure message="junit.framework.AssertionFailedError" type="junit.framework.AssertionFailedError"><![CDATA[junit.framework.AssertionFailedError: expected:<1> but was:<2>
	at com.example.BodyTest.fails(BodyTest.java:10)
]]></failure>
        </testcase>
      </testsuite>
      """.trimIndent(),
    )

    val failure = MavenTestReportParser.parse(report).single().testCases.single().failure

    assertEquals("1", failure?.expectedText)
    assertEquals("2", failure?.actualText)
    assertEquals(true, failure?.isComparisonFailure)
  }

  @Test
  fun `parses assertj comparison failure from stack trace body`() {
    val report = Files.createTempFile("TEST-AssertJTest", ".xml")
    report.writeText(
      """
      <testsuite name="com.example.AssertJTest" tests="1" failures="1" errors="0" skipped="0" time="0.010">
        <testcase classname="com.example.AssertJTest" name="fails" time="0.010">
          <failure message="org.opentest4j.AssertionFailedError" type="org.opentest4j.AssertionFailedError"><![CDATA[org.opentest4j.AssertionFailedError:
expected: "expected"
 but was: "actual"
	at com.example.AssertJTest.fails(AssertJTest.java:10)
]]></failure>
        </testcase>
      </testsuite>
      """.trimIndent(),
    )

    val failure = MavenTestReportParser.parse(report).single().testCases.single().failure

    assertEquals("\"expected\"", failure?.expectedText)
    assertEquals("\"actual\"", failure?.actualText)
    assertEquals(true, failure?.isComparisonFailure)
  }
}
