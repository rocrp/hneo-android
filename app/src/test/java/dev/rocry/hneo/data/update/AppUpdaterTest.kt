package dev.rocry.hneo.data.update

import dev.rocry.hneo.data.AppSettings
import dev.rocry.hneo.data.FakeSettingsStore
import dev.rocry.hneo.data.http.FakeHttpEngine
import dev.rocry.hneo.data.http.JsonHttp
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class AppUpdaterTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true }
    private val dispatcher = UnconfinedTestDispatcher()
    private val engine = FakeHttpEngine()
    private val settings = FakeSettingsStore(AppSettings(autoUpdateEnabled = true, updateCheckIntervalHours = 24))

    private val installed = mutableListOf<File>()

    /** Well past any interval, so a never-checked install is due on first launch. */
    private var clock = 30L * 24 * 60 * 60 * 1000L

    private val updateService by lazy {
        UpdateService(JsonHttp(engine, json, dispatcher), engine, json, dispatcher)
    }

    private val updater by lazy {
        AppUpdater(
            updateService = updateService,
            settings = settings,
            installer = { installed += it },
            currentVersionCode = 100,
            updatesDir = tempFolder.root,
            scope = TestScope(dispatcher),
            now = { clock },
        )
    }

    private fun releaseBody(build: Int) = """
        {"tag_name":"build-$build","name":"0.1.$build","body":"notes",
         "assets":[{"name":"hneo-0.1.$build.apk",
                    "browser_download_url":"https://example.com/hneo-0.1.$build.apk"}]}
    """.trimIndent()

    private val hoursInMillis = 60 * 60 * 1000L

    @Test
    fun `a newer release becomes available and prompts on launch`() = runTest {
        engine.respond(body = releaseBody(200))

        updater.checkOnLaunch()

        val state = updater.state.value as UpdateState.Available
        assertEquals(200, state.release.buildNumber)
        assertTrue(state.promptOnLaunch)
    }

    @Test
    fun `a release we already run reports up to date`() = runTest {
        engine.respond(body = releaseBody(100))

        updater.checkOnLaunch()

        assertEquals(UpdateState.UpToDate, updater.state.value)
    }

    @Test
    fun `a manual check stamps the interval, so relaunch does not re-prompt`() = runTest {
        // The defect: declining a manual update got re-prompted by the auto-check
        // on the very next launch, because only the auto path stamped the clock.
        engine.respond(body = releaseBody(200))

        updater.checkNow()
        updater.dismissLaunchPrompt()
        val requestsAfterManualCheck = engine.requests.size

        clock += hoursInMillis // well inside the 24 hour interval
        updater.checkOnLaunch()

        assertEquals("no second check", requestsAfterManualCheck, engine.requests.size)
        assertTrue((updater.state.value as UpdateState.Available).promptOnLaunch.not())
    }

    @Test
    fun `the launch check runs again once the interval has elapsed`() = runTest {
        engine.respond(body = releaseBody(200))
        updater.checkNow()
        val requestsAfterManualCheck = engine.requests.size

        clock += 25 * hoursInMillis
        updater.checkOnLaunch()

        assertEquals(requestsAfterManualCheck + 1, engine.requests.size)
    }

    @Test
    fun `the launch check does nothing when auto update is off`() = runTest {
        settings.update { it.copy(autoUpdateEnabled = false) }
        engine.respond(body = releaseBody(200))

        updater.checkOnLaunch()

        assertTrue(engine.requests.isEmpty())
        assertEquals(UpdateState.Idle, updater.state.value)
    }

    @Test
    fun `a rate limited check reports what GitHub actually said`() = runTest {
        // A 403 body used to reach the release parser and surface as "Missing tag_name".
        engine.respond(
            code = 403,
            body = """{"message":"API rate limit exceeded for 1.2.3.4.","documentation_url":"https://docs.github.com"}""",
        )

        updater.checkNow()

        val failed = updater.state.value as UpdateState.Failed
        assertEquals("API rate limit exceeded for 1.2.3.4.", failed.message)
    }

    @Test
    fun `a non-2xx without a message still reports its status`() = runTest {
        engine.respond(code = 502, body = "<html>bad gateway</html>")

        updater.checkNow()

        assertTrue((updater.state.value as UpdateState.Failed).message.contains("502"))
    }

    @Test
    fun `an unreachable GitHub is reported, not swallowed`() = runTest {
        engine.failWith(IOException("no network"))

        updater.checkOnLaunch()

        val failed = updater.state.value as UpdateState.Failed
        assertTrue(failed.message.contains("Could not reach GitHub"))
    }

    @Test
    fun `a release without an APK asset is reported readably`() = runTest {
        engine.respond(body = """{"tag_name":"build-200","name":"0.1.200","assets":[]}""")

        updater.checkNow()

        assertTrue((updater.state.value as UpdateState.Failed).message.contains("no APK"))
    }

    @Test
    fun `downloading installs the APK under the one filename convention`() = runTest {
        engine.respond(body = releaseBody(200))
        updater.checkOnLaunch()
        val release = (updater.state.value as UpdateState.Available).release

        engine.respond(body = "PK-fake-apk-bytes")
        updater.download(release)

        assertEquals(listOf("hneo-0.1.200.apk"), installed.map { it.name })
        assertEquals(UpdateState.Idle, updater.state.value)
    }

    @Test
    fun `a failed download is reported instead of installing`() = runTest {
        engine.respond(body = releaseBody(200))
        updater.checkOnLaunch()
        val release = (updater.state.value as UpdateState.Available).release

        engine.respond(code = 404, body = "gone")
        updater.download(release)

        assertTrue(installed.isEmpty())
        assertTrue((updater.state.value as UpdateState.Failed).message.contains("404"))
    }

    @Test
    fun `dismissing the launch prompt keeps the update reachable`() = runTest {
        engine.respond(body = releaseBody(200))
        updater.checkOnLaunch()

        updater.dismissLaunchPrompt()

        val state = updater.state.value as UpdateState.Available
        assertEquals(200, state.release.buildNumber)
        assertTrue(state.promptOnLaunch.not())
    }
}
