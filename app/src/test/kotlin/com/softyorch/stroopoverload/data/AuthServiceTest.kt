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

    @Test
    fun `validateRegistration accepts a well-formed form`() {
        assertNull(AuthService.validateRegistration("neo@example.com", " neo@example.com ", "Str0ng!Pass", "Str0ng!Pass", "Neo"))
    }

    @Test
    fun `validateRegistration reports the first broken rule`() {
        assertEquals(
            RegistrationError.NicknameTooShort,
            AuthService.validateRegistration("neo@example.com", "neo@example.com", "Str0ng!Pass", "Str0ng!Pass", " N "),
        )
        assertEquals(
            RegistrationError.EmailMismatch,
            AuthService.validateRegistration("neo@example.com", "neo@example.org", "Str0ng!Pass", "Str0ng!Pass", "Neo"),
        )
        assertEquals(
            RegistrationError.PasswordMismatch,
            AuthService.validateRegistration("neo@example.com", "neo@example.com", "Str0ng!Pass", "Str0ng!Pasz", "Neo"),
        )
    }

    @Test
    fun `validateRegistration email check matches android Patterns EMAIL_ADDRESS`() {
        listOf("a@b.co", "first.last+tag@sub.example.com", "x_y%z-w@a-b.io").forEach {
            assertNull(it, AuthService.validateRegistration(it, it, "Str0ng!Pass", "Str0ng!Pass", "Neo"))
        }
        listOf("plain", "no@tld", "@example.com", "a@.com", "a b@example.com", "a@example.").forEach {
            assertEquals(it, RegistrationError.InvalidEmailFormat, AuthService.validateRegistration(it, it, "Str0ng!Pass", "Str0ng!Pass", "Neo"))
        }
    }
}
