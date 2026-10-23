package pl.finrev.smssamsung

import android.content.Intent
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import org.hamcrest.Matchers.not
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionSmokeTest {
    @Test fun applicationHasNoContactsSmsAudioPermissionsOrLegacyLinkHandler() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val info = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        val forbidden = setOf("SEND_SMS", "READ_SMS", "RECEIVE_SMS", "READ_CONTACTS", "WRITE_CONTACTS", "RECORD_AUDIO")
        assertTrue(info.requestedPermissions.orEmpty().none { it.removePrefix("android.permission.") in forbidden })
        assertTrue("android.permission.INTERNET" in info.requestedPermissions.orEmpty())
        val link = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("smssamsung://compose?text=test"))
            .setPackage(context.packageName).addCategory(Intent.CATEGORY_BROWSABLE)
        assertTrue(context.packageManager.queryIntentActivities(link, 0).isEmpty())
    }
    @Test fun oneDraftNeedsExplicitConfirmationAndNeverLaunchesEditorInTest() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.message).setText("Test {imie}")
                ViewModelProvider(activity)[DraftSession::class.java].apply {
                    mode = CommunicationMode.PRIVATE
                    batch.replace(listOf(Recipient("+12025550101", "Test Example")))
                }
                activity.findViewById<android.view.View>(R.id.preview).performClick()
            }
            onView(withText("Test Example — +12025550101")).inRoot(isDialog()).perform(click())
            onView(withId(android.R.id.button1)).inRoot(isDialog()).check(matches(not(isEnabled())))
            onView(isAssignableFrom(android.widget.CheckBox::class.java)).inRoot(isDialog()).perform(click())
            onView(withId(android.R.id.button1)).inRoot(isDialog()).check(matches(isEnabled()))
            // Do not click the editor button.
        }
    }
    @Test fun rotationRetainsOnlyMemoryAndClearRemovesAllDraftState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.message).setText("Test {imie}")
                ViewModelProvider(activity)[DraftSession::class.java].batch.replace(listOf(Recipient("+12025550101", "Test Example")))
                assertFalse(activity.findViewById<EditText>(R.id.message).isSaveEnabled)
                assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals("Test {imie}", activity.findViewById<EditText>(R.id.message).text.toString())
                assertTrue(activity.findViewById<TextView>(R.id.recipients).text.contains("Test Example"))
                activity.findViewById<android.view.View>(R.id.clear_session).performClick()
                assertEquals("", activity.findViewById<EditText>(R.id.message).text.toString())
                val session = ViewModelProvider(activity)[DraftSession::class.java]
                assertTrue(session.batch.all().isEmpty()); assertNull(session.mode); assertNull(session.material)
            }
        }
    }
    @Test fun finishedActivityAndUntrustedLauncherCannotRestorePrivateState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.findViewById<EditText>(R.id.message).setText("Test private draft") }
        }
        val incoming = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW; data = android.net.Uri.parse("smssamsung://compose?text=untrusted")
            putExtra(Intent.EXTRA_TEXT, "untrusted")
        }
        ActivityScenario.launch<MainActivity>(incoming).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals("", activity.findViewById<EditText>(R.id.message).text.toString())
                assertTrue(ViewModelProvider(activity)[DraftSession::class.java].batch.all().isEmpty())
            }
        }
    }
    @Test fun editorAndPickerIntentsStaySinglePurposeWithoutDispatch() {
        val intent = SmsIntents.editor(Recipient("+12025550101", "Test"), "Test draft")
        assertEquals(Intent.ACTION_SENDTO, intent.action); assertEquals("smsto", intent.data?.scheme)
        assertEquals("+12025550101", intent.data?.schemeSpecificPart)
        assertEquals("Test draft", intent.getStringExtra("sms_body"))
        val picker = ContactPicker.intent()
        assertEquals(Intent.ACTION_PICK, picker.action)
        assertEquals(android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI, picker.data)
        assertEquals(0, picker.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }
    @Test fun pickerRejectsBroadForeignOrParameterizedUrisWithoutReadingProvider() {
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        for (uri in listOf("content://com.android.contacts/data", "content://evil.test/data/1",
            "content://com.android.contacts/data/1?phone=example", "file:///tmp/contact")) {
            assertThrows(IllegalArgumentException::class.java) { ContactPicker.read(resolver, android.net.Uri.parse(uri)) }
        }
    }
    @Test fun marketingRemainsBlockedWithoutOpeningAReviewDialog() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[DraftSession::class.java].apply {
                    mode = CommunicationMode.MARKETING; body = "Test"
                    batch.replace(listOf(Recipient("+12025550101", "Test")))
                }
                activity.findViewById<android.view.View>(R.id.preview).performClick()
            }
            onView(withText("Sprawdź jedną wiadomość")).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
        }
    }
}
