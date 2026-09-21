package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.SupabaseConfigManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("USSD Listener", appName)
    }

    @Test
    fun `test supabase config url validation`() {
        assertTrue(SupabaseConfigManager.isValidHttpsUrl("https://xyz123.supabase.co"))
        assertFalse(SupabaseConfigManager.isValidHttpsUrl("http://xyz123.supabase.co"))
        assertFalse(SupabaseConfigManager.isValidHttpsUrl("https://your-project.supabase.co"))
        assertFalse(SupabaseConfigManager.isValidHttpsUrl("not-a-url"))
    }

    @Test
    fun `test save and load supabase config`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SupabaseConfigManager.clearConfig(context)

        SupabaseConfigManager.saveConfig(
            context = context,
            url = "https://testproject.supabase.co",
            anonKey = "sb_secret_sample_key_12345678"
        )

        assertTrue(SupabaseConfigManager.isConfigured(context))
        assertEquals("https://testproject.supabase.co", SupabaseConfigManager.getSupabaseUrl(context))
        assertEquals("sb_secret_sample_key_12345678", SupabaseConfigManager.getSupabaseKey(context))

        SupabaseConfigManager.clearConfig(context)
        assertFalse(SupabaseConfigManager.isConfigured(context))
    }
}

