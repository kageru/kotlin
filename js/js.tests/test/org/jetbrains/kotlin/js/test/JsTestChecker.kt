/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.js.test

import org.jetbrains.kotlin.js.test.interop.ScriptEngine
import org.jetbrains.kotlin.js.test.interop.ScriptEngineNashorn
import org.jetbrains.kotlin.js.test.interop.ScriptEngineV8
import org.junit.Assert

fun createScriptEngine(): ScriptEngine {
    return ScriptEngineNashorn()
}

fun ScriptEngine.overrideAsserter() {
    evalVoid("this['kotlin-test'].kotlin.test.overrideAsserter_wbnzx$(this['kotlin-test'].kotlin.test.DefaultAsserter);")
}

class ThreadRunnerState {
    var antThread: Thread? = null
        get() = field
        set(value) {
            if (field == null) field = value
        }


    var boxThread: Thread? = null
        get() = field
        set(value) {
            if (field == null) field = value
        }
}

val threadRunnerState = ThreadRunnerState()

fun ScriptEngine.runTestFunction(
    testModuleName: String?,
    testPackageName: String?,
    testFunctionName: String,
    withModuleSystem: Boolean
): String? {
    var script = when {
        withModuleSystem -> BasicBoxTest.KOTLIN_TEST_INTERNAL + ".require('" + testModuleName!! + "')"
        testModuleName === null -> "this"
        else -> testModuleName
    }

    if (testPackageName !== null) {
        script += ".$testPackageName"
    }

    val testPackage = eval<Any>(script)
    return callMethod<String?>(testPackage, testFunctionName).also {
        releaseObject(testPackage)
    }
}

abstract class AbstractJsTestChecker {
    fun check(
        files: List<String>,
        testModuleName: String?,
        testPackageName: String?,
        testFunctionName: String,
        expectedResult: String,
        withModuleSystem: Boolean
    ) {
        val actualResult = run(files, testModuleName, testPackageName, testFunctionName, withModuleSystem)
        Assert.assertEquals(expectedResult, actualResult)
    }

    private fun run(
        files: List<String>,
        testModuleName: String?,
        testPackageName: String?,
        testFunctionName: String,
        withModuleSystem: Boolean
    ) = run(files) {
        runTestFunction(testModuleName, testPackageName, testFunctionName, withModuleSystem)
    }


    fun run(files: List<String>) {
        run(files) { null }
    }

    protected abstract fun run(files: List<String>, f: ScriptEngine.() -> Any?): Any?
}

fun ScriptEngine.runAndRestoreContext(f: ScriptEngine.() -> Any?): Any? {
    return try {
        saveState()
        f()
    } finally {
        restoreState()
    }
}

abstract class AbstractNashornJsTestChecker : AbstractJsTestChecker() {

    private var engineUsageCnt = 0

    private var engineCache: ScriptEngineNashorn? = null

    protected val engine: ScriptEngineNashorn
        get() = engineCache ?: createScriptEngineForTest().also {
            engineCache = it
        }

    protected open fun beforeRun() {}

    override fun run(files: List<String>, f: ScriptEngine.() -> Any?): Any? {
        // Recreate the engine once in a while
        if (engineUsageCnt++ > 100) {
            engineUsageCnt = 0
            engineCache = null
        }

        beforeRun()

        return engine.runAndRestoreContext {
            files.forEach { loadFile(it) }
            f()
        }
    }

    protected abstract val preloadedScripts: List<String>

    protected open fun createScriptEngineForTest(): ScriptEngineNashorn {
        val engine = ScriptEngineNashorn()

        preloadedScripts.forEach { engine.loadFile(it) }

        return engine
    }
}

const val SETUP_KOTLIN_OUTPUT = "kotlin.kotlin.io.output = new kotlin.kotlin.io.BufferedOutput();"
const val GET_KOTLIN_OUTPUT = "kotlin.kotlin.io.output.buffer;"

object NashornJsTestChecker : AbstractNashornJsTestChecker() {

    override fun beforeRun() {
        engine.evalVoid(SETUP_KOTLIN_OUTPUT)
    }

    override val preloadedScripts = listOf(
        BasicBoxTest.TEST_DATA_DIR_PATH + "nashorn-polyfills.js",
        BasicBoxTest.DIST_DIR_JS_PATH + "kotlin.js",
        BasicBoxTest.DIST_DIR_JS_PATH + "kotlin-test.js"
    )

    fun checkStdout(files: List<String>, expectedResult: String) {
        run(files)
        val actualResult = engine.eval<String>(GET_KOTLIN_OUTPUT)
        Assert.assertEquals(expectedResult, actualResult)
    }

    override fun createScriptEngineForTest(): ScriptEngineNashorn {
        val engine = super.createScriptEngineForTest()

        engine.overrideAsserter()

        return engine
    }
}

class NashornIrJsTestChecker : AbstractNashornJsTestChecker() {
    override val preloadedScripts = listOf(
        BasicBoxTest.TEST_DATA_DIR_PATH + "nashorn-polyfills.js",
        "libraries/stdlib/js/src/js/polyfills.js"
    )
}

abstract class AbstractV8JsTestChecker : AbstractJsTestChecker() {
    protected abstract val engine: ScriptEngineV8
}

object V8JsTestChecker : AbstractV8JsTestChecker() {
    private lateinit var creatorThread: Thread
    override val engine by lazy {
        creatorThread = Thread.currentThread()
        createV8Engine()
    }

    private fun createV8Engine(): ScriptEngineV8 {
        val v8 = ScriptEngineV8()

        listOf(
            BasicBoxTest.DIST_DIR_JS_PATH + "kotlin.js",
            BasicBoxTest.DIST_DIR_JS_PATH + "kotlin-test.js"
        ).forEach { v8.loadFile(it) }

        v8.overrideAsserter()

        return v8
    }


    fun checkStdout(files: List<String>, expectedResult: String) {
        run(files) {
            val actualResult = engine.eval<String>(GET_KOTLIN_OUTPUT)
            Assert.assertEquals(expectedResult, actualResult)
        }
    }

    override fun run(files: List<String>, f: ScriptEngine.() -> Any?): Any? {
        try {
            engine.evalVoid(SETUP_KOTLIN_OUTPUT)
            return engine.runAndRestoreContext {
                files.forEach { loadFile(it) }
                f()
            }
        } catch (err: Error) {
            if (Thread.currentThread() != creatorThread) {
                throw Error("The thread created V8 Engine is $creatorThread, when it is invoked from ${Thread.currentThread()}, " +
                                    "AntThread: ${threadRunnerState.antThread}, BoxThread: ${threadRunnerState.boxThread}", err)
            } else {
                throw Error("Threads are same, created V8 Engine is $creatorThread," +
                                    "AntThread: ${threadRunnerState.antThread}, BoxThread: ${threadRunnerState.boxThread}", err)
            }
        }
    }
}

object V8IrJsTestChecker : AbstractV8JsTestChecker() {
    override val engine get() = ScriptEngineV8()

    override fun run(files: List<String>, f: ScriptEngine.() -> Any?): Any? {

        val v8 = engine

        val v = try {
            files.forEach { v8.loadFile(it) }
            v8.f()
        } catch (t: Throwable) {
            try {
                v8.release()
            } finally {
                // Don't mask the original exception
                throw t
            }
        }
        v8.release()

        return v
    }
}