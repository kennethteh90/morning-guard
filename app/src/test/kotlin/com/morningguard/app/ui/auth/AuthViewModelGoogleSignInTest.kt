package com.morningguard.app.ui.auth

import android.content.Context
import com.morningguard.app.R
import com.morningguard.app.auth.GoogleSignInHelper
import com.morningguard.app.auth.GoogleSignInUserCancelledException
import com.morningguard.data.auth.AuthRepository
import com.morningguard.data.auth.AuthState
import com.morningguard.data.auth.MagicLinkRateLimitStore
import com.morningguard.data.monitoring.ErrorReporter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AuthViewModelGoogleSignInTest {

    private val testDispatcher = StandardTestDispatcher()
    private val authRepository: AuthRepository = mockk(relaxed = true)
    private val magicLinkRateLimitStore: MagicLinkRateLimitStore = mockk(relaxed = true)
    private val googleSignInHelper: GoogleSignInHelper = mockk(relaxed = true)
    private val errorReporter: ErrorReporter = mockk(relaxed = true)
    private val appContext: Context = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { authRepository.state } returns MutableStateFlow(AuthState.SignedOut)
        every { appContext.getString(R.string.auth_google_sign_in_failed) } returns "Google failed"
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
        errorReporter,
    )

    @Test
    fun `google sign in exchanges id token with repository`() = runTest {
        assumeTrue(viewModel().isGoogleSignInEnabled())
        val activityContext: Context = mockk(relaxed = true)
        coEvery { googleSignInHelper.requestIdToken(activityContext) } returns Result.success("id-token")
        coEvery { authRepository.signInWithGoogle("id-token") } returns Result.success(Unit)
        val viewModel = viewModel()

        viewModel.signInWithGoogle(activityContext)
        advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.signInWithGoogle("id-token") }
        assertEquals(false, viewModel.uiState.value.isSigningInWithGoogle)
    }

    @Test
    fun `google sign in user cancellation clears spinner without error or sentry`() = runTest {
        assumeTrue(viewModel().isGoogleSignInEnabled())
        val activityContext: Context = mockk(relaxed = true)
        coEvery { googleSignInHelper.requestIdToken(activityContext) } returns
            Result.failure(GoogleSignInUserCancelledException())
        val viewModel = viewModel()

        viewModel.signInWithGoogle(activityContext)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isSigningInWithGoogle)
        assertNull(viewModel.uiState.value.error)
        verify(exactly = 0) { errorReporter.reportNonFatal(any(), any(), any()) }
        coVerify(exactly = 0) { authRepository.signInWithGoogle(any()) }
    }

    @Test
    fun `google sign in credential failure shows friendly error and reports sentry`() = runTest {
        assumeTrue(viewModel().isGoogleSignInEnabled())
        val activityContext: Context = mockk(relaxed = true)
        val failure = IllegalStateException("28433")
        coEvery { googleSignInHelper.requestIdToken(activityContext) } returns Result.failure(failure)
        val viewModel = viewModel()

        viewModel.signInWithGoogle(activityContext)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isSigningInWithGoogle)
        assertEquals("Google failed", viewModel.uiState.value.error)
        verify(exactly = 1) {
            errorReporter.reportNonFatal(failure, tags = mapOf("flow" to "google_sign_in"))
        }
    }

    @Test
    fun `google sign in supabase failure clears spinner and reports sentry`() = runTest {
        assumeTrue(viewModel().isGoogleSignInEnabled())
        val activityContext: Context = mockk(relaxed = true)
        val failure = IllegalStateException("Supabase rejected token")
        coEvery { googleSignInHelper.requestIdToken(activityContext) } returns Result.success("id-token")
        coEvery { authRepository.signInWithGoogle("id-token") } returns Result.failure(failure)
        val viewModel = viewModel()

        viewModel.signInWithGoogle(activityContext)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.isSigningInWithGoogle)
        assertEquals("Google failed", viewModel.uiState.value.error)
        verify(exactly = 1) {
            errorReporter.reportNonFatal(failure, tags = mapOf("flow" to "google_sign_in"))
        }
    }
}
