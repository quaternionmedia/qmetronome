package media.quaternion.qmetronome.tools

import media.quaternion.qmetronome.tutorial.TutorialTopic
import media.quaternion.qmetronome.tutorial.TutorialTopics
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Not a behavioral test - this "test" *is* the `generateUserGuide` Gradle task (see
 * `app/build.gradle.kts`'s `generateUserGuide` `Test` task, which reuses this exact classpath and
 * filters down to just this one class, `dependsOn(testDebugUnitTest)` so every topic's screenshot
 * (and video, for [TutorialTopic.hasVideo] topics) has already been (re)captured by the time this
 * runs). Regenerates `docs/user-guide.md` from [TutorialTopics.all] grouped by category, embedding
 * each topic's already-captured screenshot (see `ComposeTestSupport.kt`'s `screenshotPath`) - the
 * one step of "tests spawn the rest" that isn't itself a Compose UI test, since there's no UI here
 * to drive, just [TutorialTopics]' own content plus files those *other* tests already produced.
 *
 * Asserting each topic's screenshot (and video, where expected) actually exists here (rather than
 * only trusting it was produced) is the real regression protection: a new [TutorialTopic] added
 * without a matching test fails this, rather than silently shipping a doc with a broken image/
 * video link.
 */
class GenerateUserGuideTest {

    @Test
    fun `regenerate user-guide md from TutorialTopics`() {
        val docsDir = File("../docs")
        val screenshotsDir = File(docsDir, "images/generated/screenshots")
        val videosDir = File(docsDir, "images/generated/videos")

        val missingScreenshots = TutorialTopics.all.filterNot { File(screenshotsDir, "${it.id}.png").isFile }
        assertTrue(
            "every TutorialTopic needs a captured screenshot before the user guide can " +
                "reference it - missing for: ${missingScreenshots.map { it.id }}. Run ./gradlew " +
                "testDebugUnitTest first (or just ./gradlew generateUserGuide, which depends on " +
                "it) to (re)capture them.",
            missingScreenshots.isEmpty(),
        )

        val missingVideos = TutorialTopics.all.filter { it.hasVideo }.filterNot { File(videosDir, "${it.id}.gif").isFile }
        assertTrue(
            "every TutorialTopic.hasVideo topic needs a captured video before the user guide " +
                "can reference it - missing for: ${missingVideos.map { it.id }}. Add/run its " +
                "*VideoTest.kt (see BpmDragVideoTest.kt for the pattern).",
            missingVideos.isEmpty(),
        )

        val categories = TutorialTopics.all.groupBy { it.category }
        val markdown = buildString {
            appendLine("# qMetronome User Guide")
            appendLine()
            appendLine(
                "Every gesture qMetronome has, one topic at a time - a screenshot of the real " +
                    "app plus a short video showing it in motion where a single still frame " +
                    "wouldn't tell the whole story (a drag, a swipe, a timed hold). This exact " +
                    "same content is also built into the app itself: tap the **?** icon next to " +
                    "Settings for a live, interactive version where you can actually try each " +
                    "control - richer than what's on this page, since it's the real thing rather " +
                    "than a picture of it.",
            )
            appendLine()
            appendLine(
                "*Generated from `TutorialTopics.all` - do not edit by hand; regenerate via " +
                    "`./gradlew generateUserGuide`.*",
            )
            appendLine()
            append("**Jump to:** ")
            appendLine(
                categories.keys.joinToString(" · ") { category ->
                    val anchor = category.displayName.lowercase().replace(" ", "-").replace("&", "")
                    "[${category.displayName}](#$anchor)"
                },
            )
            appendLine()
            categories.forEach { (category, topics) ->
                appendLine("## ${category.displayName}")
                appendLine()
                topics.forEach { topic ->
                    appendLine("### ${topic.title}")
                    appendLine()
                    appendLine(topic.description)
                    appendLine()
                    appendLine("![${topic.title}](images/generated/screenshots/${topic.id}.png)")
                    appendLine()
                    if (topic.hasVideo) {
                        appendLine("**In motion:**")
                        appendLine()
                        appendLine("![${topic.title} (in motion)](images/generated/videos/${topic.id}.gif)")
                        appendLine()
                    }
                }
            }
        }

        val outputFile = File(docsDir, "user-guide.md")
        outputFile.writeText(markdown)

        assertTrue("expected a non-empty user-guide.md to have been written", outputFile.length() > 0)
    }
}
