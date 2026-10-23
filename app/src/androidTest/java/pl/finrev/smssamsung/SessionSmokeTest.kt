package pl.finrev.smssamsung

import android.app.AlertDialog
import android.content.Intent
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import org.hamcrest.Matchers.not
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionSmokeTest {
    @Test fun consentIsRequiredBeforeReviewingAnyDraft() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.message).setText("Test {imie}")
                ViewModelProvider(activity)[DraftSession::class.java].batch.replace(
                    listOf(Recipient("+12025550101", "Test Example")))
                activity.findViewById<android.view.View>(R.id.preview).performClick()
            }
            onView(withText("Przeglądaj")).check(matches(not(isEnabled())))
        }
    }
    @Test fun rotationKeepsOnlyMemoryAndClearRemovesDraftAndRecipients() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.message).setText("Test {imie}")
                val session = ViewModelProvider(activity)[DraftSession::class.java]
                session.batch.replace(listOf(Recipient("+12025550101", "Test Example")))
                assertFalse(activity.findViewById<EditText>(R.id.message).isSaveEnabled)
                assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals("Test {imie}", activity.findViewById<EditText>(R.id.message).text.toString())
                assertTrue(activity.findViewById<TextView>(R.id.recipients).text.contains("Test Example"))
                activity.findViewById<android.view.View>(R.id.clear_session).performClick()
                assertEquals("", activity.findViewById<EditText>(R.id.message).text.toString())
                assertTrue(ViewModelProvider(activity)[DraftSession::class.java].batch.all().isEmpty())
            }
        }
    }

    @Test fun freshSessionAfterActivityFinishHasNoCustomerState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.findViewById<EditText>(R.id.message).setText("Test private draft") }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals("", activity.findViewById<EditText>(R.id.message).text.toString())
                assertTrue(ViewModelProvider(activity)[DraftSession::class.java].batch.all().isEmpty())
            }
        }
    }

    @Test fun launcherIgnoresUntrustedExtrasAndLegacyLinks() {
        val incoming = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("smssamsung://compose?text=untrusted")
            putExtra(Intent.EXTRA_TEXT, "untrusted")
        }
        ActivityScenario.launch<MainActivity>(incoming).use { scenario ->
            scenario.onActivity { activity -> assertEquals("", activity.findViewById<EditText>(R.id.message).text.toString()) }
        }
    }

    @Test fun smsEditorIntentHasOneRecipientAndNeverDispatchesDuringTests() {
        val intent = SmsIntents.editor(Recipient("+12025550101", "Test"), "Test draft")
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("smsto:+12025550101", intent.data.toString())
        assertEquals("Test draft", intent.getStringExtra("sms_body"))
        // Deliberately never call startActivity(intent).
    }

    @Test fun historyContainsOnlyTimestampAndCountAndRecoversCorruption() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = context.getSharedPreferences("draft_history", 0)
        prefs.edit().putString("items", "invalid").commit()
        val history = DraftHistory(context)
        assertEquals("Brak historii", history.display())
        repeat(35) { history.record(20) }
        val rows = org.json.JSONArray(prefs.getString("items", "[]"))
        assertEquals(30, rows.length())
        for (i in 0 until rows.length()) {
            val item = rows.getJSONObject(i)
            assertEquals(setOf("timestamp", "recipients"), item.keys().asSequence().toSet())
        }
        history.clear()
    }
}
