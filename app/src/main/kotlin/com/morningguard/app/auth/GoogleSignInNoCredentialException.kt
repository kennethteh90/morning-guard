package com.morningguard.app.auth

/**
 * Device has no eligible Google account for Credential Manager one-tap sign-in.
 * Not an error worth reporting to monitoring.
 */
class GoogleSignInNoCredentialException : Exception("No Google account available on this device")
