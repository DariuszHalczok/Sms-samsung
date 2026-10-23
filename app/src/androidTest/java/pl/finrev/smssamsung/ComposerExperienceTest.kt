package pl.finrev.smssamsung

import android.content.Context
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
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposerExperienceTest {
    private val profile = "11111111-1111-4111-8111-111111111111"
    @Test fun clearingDraftDoesNotPersistRecipientsAndRetainsOnlyPublicAdvisorPreference() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = AdvisorProfileStore(context); store.clear(); store.save(profile)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val session = ViewModelProvider(activity)[DraftSession::class.java]
                    session.addPicked(Recipient("+12025550101", "Test Example"))
                    activity.findViewById<EditText>(R.id.message).setText("Synthetic private draft")
                    activity.findViewById<android.view.View>(R.id.clear_session).performClick()
                    assertTrue(session.batch.all().isEmpty()); assertNull(session.selected()); assertEquals("", session.body)
                }
            }
            assertEquals(profile, AdvisorProfileStore(context).load())
            assertEquals(mapOf("public_id" to profile), context.getSharedPreferences("advisor_public_profile",0).all)
            assertThrows(IllegalArgumentException::class.java) { store.save("+12025550101") }
            assertEquals(profile, store.load())
        } finally { store.clear() }
    }
    @Test fun damagedProfileAndExtraFieldsAreSanitizedWithoutExposingThem() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("advisor_public_profile",0); val store=AdvisorProfileStore(context)
        try {
            prefs.edit().clear().putInt("public_id",1).commit(); assertNull(store.load()); assertTrue(prefs.all.isEmpty())
            prefs.edit().putString("public_id",profile).putString("unexpected_field","test-only").commit()
            assertEquals(profile,store.load()); assertEquals(setOf("public_id"),prefs.all.keys)
        } finally { store.clear() }
    }
    @Test fun firstPurposeChoiceKeepsSharedDraftAndDoesNotAskToDiscardIt() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.message).setText("Synthetic shared draft")
                assertNull(ViewModelProvider(activity)[DraftSession::class.java].mode)
                activity.findViewById<android.view.View>(R.id.choose_mode).performClick()
            }
            onView(withText(CommunicationMode.PRIVATE.label)).inRoot(isDialog()).perform(click())
            onView(withText(R.string.mode_change_title)).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
            scenario.onActivity { activity ->
                assertEquals("Synthetic shared draft", activity.findViewById<EditText>(R.id.message).text.toString())
                assertEquals(CommunicationMode.PRIVATE, ViewModelProvider(activity)[DraftSession::class.java].mode)
            }
        }
    }
    @Test fun cancelingModeChangeKeepsDraftAndConfirmingClearsIt() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[DraftSession::class.java].mode = CommunicationMode.PRIVATE
                activity.findViewById<EditText>(R.id.message).setText("Synthetic private draft")
                activity.findViewById<android.view.View>(R.id.choose_mode).performClick()
            }
            onView(withText(CommunicationMode.REQUESTED.label)).inRoot(isDialog()).perform(click())
            onView(withText(R.string.cancel)).inRoot(isDialog()).perform(click())
            scenario.onActivity { activity ->
                assertEquals("Synthetic private draft",activity.findViewById<EditText>(R.id.message).text.toString())
                assertEquals(CommunicationMode.PRIVATE,ViewModelProvider(activity)[DraftSession::class.java].mode)
                activity.findViewById<android.view.View>(R.id.choose_mode).performClick()
            }
            onView(withText(CommunicationMode.REQUESTED.label)).inRoot(isDialog()).perform(click())
            onView(withText(R.string.mode_change_confirm)).inRoot(isDialog()).perform(click())
            scenario.onActivity { activity ->
                assertEquals("",activity.findViewById<EditText>(R.id.message).text.toString())
                assertEquals(CommunicationMode.REQUESTED,ViewModelProvider(activity)[DraftSession::class.java].mode)
                assertTrue(activity.findViewById<android.view.View>(R.id.material_section).visibility == android.view.View.VISIBLE)
            }
        }
    }
    @Test fun previewRequiresNewConfirmationAfterRemovingAndSelectingAnotherContact() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[DraftSession::class.java].apply {
                    mode=CommunicationMode.PRIVATE
                    addPicked(Recipient("+12025550101","Test One")); addPicked(Recipient("+12025550102","Test Two"))
                }
                activity.findViewById<EditText>(R.id.message).setText("Test {imie}")
                activity.findViewById<android.view.View>(R.id.remove_recipient).performClick()
                activity.findViewById<android.view.View>(R.id.preview).performClick()
            }
            onView(withText("Test One — +12025550101")).inRoot(isDialog()).perform(click())
            onView(withId(android.R.id.button1)).inRoot(isDialog()).check(matches(org.hamcrest.Matchers.not(isEnabled())))
            onView(withText(R.string.cancel)).inRoot(isDialog()).perform(click())
            scenario.onActivity { activity ->
                assertEquals("+12025550101",ViewModelProvider(activity)[DraftSession::class.java].selected()?.phone)
                assertEquals("Test {imie}",activity.findViewById<EditText>(R.id.message).text.toString())
            }
        }
    }
}
