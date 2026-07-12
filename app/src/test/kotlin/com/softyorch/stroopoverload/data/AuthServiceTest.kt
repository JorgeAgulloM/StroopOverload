package com.softyorch.stroopoverload.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthServiceTest {

    @Test
    fun `validatePasswordStrength flags each missing requirement`() {
        assertEquals(RegistrationError.PasswordTooShort, AuthService.validatePasswordStrength("Ab1!"))
        assertEquals(RegistrationError.PasswordNeedsUppercase, AuthService.validatePasswordStrength("lowercase1!"))
        assertEquals(RegistrationError.PasswordNeedsLowercase, AuthService.validatePasswordStrength("UPPERCASE1!"))
        assertEquals(RegistrationError.PasswordNeedsDigit, AuthService.validatePasswordStrength("NoDigitsHere!"))
        assertEquals(RegistrationError.PasswordNeedsSymbol, AuthService.validatePasswordStrength("NoSymbol123"))
    }

    @Test
    fun `validatePasswordStrength accepts a strong password`() {
        assertNull(AuthService.validatePasswordStrength("Str0ng!Pass"))
    }

    // validateRegistration itself isn't covered here: its email-format check goes through
    // android.util.Patterns.EMAIL_ADDRESS, which is null under plain JUnit (no Robolectric/
    // instrumentation in this module) — a pre-existing gap, not something introduced by
    // extracting validatePasswordStrength above.
}
