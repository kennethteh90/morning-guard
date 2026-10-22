package com.morningguard.app.ui.auth

import android.content.Context
import com.morningguard.app.R
import com.morningguard.app.auth.GoogleSignInHelper
import com.morningguard.data.auth.AuthRepository
import com.morningguard.data.auth.AuthState
import com.morningguard.data.auth.MagicLinkRateLimitStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val authRepository: AuthRepository = mockk(relaxed = true)
    private val magicLinkRateLimitStore: MagicLinkRateLimitStore = mockk(relaxed = true)
    private val googleSignInHelper: GoogleSignInHelper = mockk(relaxed = true)
    private val appContext: Context = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { authRepository.state } returns MutableStateFlow(AuthState.SignedOut)
        every { magicLinkRateLimitStore.secondsUntilResend(any()) } returns 0
        every { appContext.getString(R.string.auth_magic_link_inbox_title) } returns "Check your inbox"
        every { appContext.getString(R.string.auth_magic_link_rate_limit, any()) } returns "Wait"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = AuthViewModel(
        appContext,
        authRepository,
        magicLinkRateLimitStore,
        googleSignInHelper,
    )

    @Test
    fun `successful send shows awaiting inbox phase`() = runTest {
        coEvery { authRepository.sendMagicLink(any()) } returns Result.success(Unit)
        val viewModel = viewModel()

        viewModel.sendMagicLink("user@example.com")
        advanceUntilIdle()

        assertEquals(MagicLinkUiPhase.AwaitingInbox, viewModel.uiState.value.magicLinkPhase)
        assertEquals("user@example.com", viewModel.uiState.value.pendingEmail)
    }

    @Test
    fun `client cooldown shows awaiting inbox without second api call`() = runTest {
        coEvery { authRepository.sendMagicLink(any()) } returns Result.success(Unit)
        every { magicLinkRateLimitStore.secondsUntilResend("user@example.com") } returnsMany listOf(0, 45)
        val viewModel = viewModel()

        viewModel.sendMagicLink("user@example.com")
        advanceUntilIdle()
        viewModel.resendMagicLink()
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.sendMagicLink("user@example.com") }
        assertEquals(MagicLinkUiPhase.AwaitingInbox, viewModel.uiState.value.magicLinkPhase)
    }

    @Test
    fun `email is trimmed before repository call`() = runTest {
        coEvery { authRepository.sendMagicLink(any()) } returns Result.success(Unit)
        val viewModel = viewModel()

        viewModel.sendMagicLink("  user@example.com  ")
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.sendMagicLink("user@example.com") }
    }

    @Test
    fun `invalid email does not call repository`() = runTest {
        val viewModel = viewModel()

        viewModel.sendMagicLink("not-an-email")
        advanceUntilIdle()

        coVerify(exactly = 0) { authRepository.sendMagicLink(any()) }
    }
}
