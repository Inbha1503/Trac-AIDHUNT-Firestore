package com.example.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.PartnerEntity
import com.example.ui.theme.AlertDueRed
import com.example.ui.theme.AppTheme
import com.example.ui.theme.DeepSageGreen
import com.example.ui.theme.ForestGreenHeader
import com.example.ui.theme.SageOutline
import com.example.ui.theme.SoftSageGreen
import com.example.ui.theme.rememberResponsiveDimensions

enum class AuthMethod {
    PHONE,
    EMAIL,
    GOOGLE
}

@Composable
fun LoginScreen(
    partners: List<PartnerEntity> = emptyList(),
    isTamil: Boolean = false,
    onToggleLanguage: (() -> Unit)? = null,
    onLoginSuccess: (phone: String, otp: String) -> Unit,
    onGoogleSignInRequested: (() -> Unit)? = null,
    onEmailSignInRequested: ((email: String, password: String) -> Unit)? = null,
    onEmailSignUpRequested: ((email: String, password: String) -> Unit)? = null,
    onSendOtpRequested: ((phoneNumber: String, onSuccess: () -> Unit) -> Unit)? = null,
    onVerifyOtpRequested: ((otpCode: String) -> Unit)? = null,
    onQuickPartnerSelected: ((PartnerEntity) -> Unit)? = null,
    onGmailLoginRequested: ((email: String) -> Unit)? = null,
    isLoggingIn: Boolean = false,
    errorMessage: String? = null,
    initialAuthMethod: AuthMethod = AuthMethod.PHONE
) {
    var selectedMethod by rememberSaveable { mutableStateOf(initialAuthMethod) }

    // Phone Auth State
    var phoneNumber by rememberSaveable { mutableStateOf("") }
    var otpCode by rememberSaveable { mutableStateOf("") }
    var isOtpSent by rememberSaveable { mutableStateOf(false) }
    var isRequestingOtpLocally by rememberSaveable { mutableStateOf(false) }

    // Email & Password Auth State
    var emailInput by rememberSaveable { mutableStateOf("") }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var isSignUpMode by rememberSaveable { mutableStateOf(false) }
    var emailError by rememberSaveable { mutableStateOf<String?>(null) }
    var passwordError by rememberSaveable { mutableStateOf<String?>(null) }

    val responsive = rememberResponsiveDimensions()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current

    val cleanDigits = phoneNumber.filter { it.isDigit() }
    val isPhoneValid = cleanDigits.length in 10..12

    fun validateAndSubmitEmail() {
        focusManager.clearFocus()
        val trimmedEmail = emailInput.trim()
        val trimmedPassword = passwordInput
        var isValid = true

        val emailRegex = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        if (trimmedEmail.isBlank() || !emailRegex.matches(trimmedEmail)) {
            emailError = if (isTamil) "சரியான மின்னஞ்சல் முகவரியை உள்ளிடவும்" else "Please enter a valid email address"
            isValid = false
        } else {
            emailError = null
        }

        if (trimmedPassword.length < 6) {
            passwordError = if (isTamil) "கடவுச்சொல் குறைந்தது 6 எழுத்துகள் இருக்க வேண்டும்" else "Password must be at least 6 characters"
            isValid = false
        } else {
            passwordError = null
        }

        if (isValid && !isLoggingIn) {
            if (isSignUpMode) {
                onEmailSignUpRequested?.invoke(trimmedEmail, trimmedPassword)
            } else {
                onEmailSignInRequested?.invoke(trimmedEmail, trimmedPassword)
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AppTheme.colors.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(
                    horizontal = responsive.screenPaddingHorizontal,
                    vertical = responsive.screenPaddingVertical
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (onToggleLanguage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = SoftSageGreen,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .clickable { onToggleLanguage() }
                            .testTag("login_language_toggle")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isTamil) "English" else "தமிழ்",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = DeepSageGreen
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(if (responsive.isSmallPhone) 10.dp else 16.dp))
            }

            // App Emblem
            Box(
                modifier = Modifier
                    .size(if (responsive.isSmallPhone) 60.dp else 72.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(DeepSageGreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Agriculture,
                    contentDescription = if (isTamil) "டிராக் லோகோ" else "Trac Logo",
                    tint = Color.White,
                    modifier = Modifier.size(if (responsive.isSmallPhone) 36.dp else 44.dp)
                )
            }

            Spacer(modifier = Modifier.height(if (responsive.isSmallPhone) 8.dp else 12.dp))

            Text(
                text = if (isTamil) "AIDHUNT டிராக்" else "AIDHUNT Trac",
                fontSize = if (responsive.isSmallPhone) 22.sp else 26.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )

            Text(
                text = if (isTamil) "பகிர்வு டிராக்டர் வணிக நிர்வாகம்" else "Shared Tractor Business Management",
                fontSize = if (responsive.isSmallPhone) 12.sp else 14.sp,
                color = DeepSageGreen,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = AlertDueRed.copy(alpha = 0.1f)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AlertDueRed.copy(alpha = 0.3f))),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage,
                        fontSize = 12.sp,
                        color = AlertDueRed,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (responsive.isSmallPhone) 12.dp else 16.dp))

            // 1. Authentication Method Selector Card (3-Tab Selector)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBg),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.cardBorder)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(if (responsive.isSmallPhone) 10.dp else 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isTamil) "உள்நுழைவு முறை" else "Login Method",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreenHeader
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SoftSageGreen.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Phone Number Option
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedMethod == AuthMethod.PHONE) DeepSageGreen else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedMethod = AuthMethod.PHONE }
                                    .testTag("tab_auth_phone")
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = if (selectedMethod == AuthMethod.PHONE) Color.White else DeepSageGreen,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isTamil) "தொலைபேசி" else "Phone",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedMethod == AuthMethod.PHONE) Color.White else DeepSageGreen
                                    )
                                }
                            }

                            // Email & Password Option
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedMethod == AuthMethod.EMAIL) DeepSageGreen else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedMethod = AuthMethod.EMAIL }
                                    .testTag("tab_auth_email")
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = null,
                                        tint = if (selectedMethod == AuthMethod.EMAIL) Color.White else DeepSageGreen,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isTamil) "மின்னஞ்சல்" else "Email",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedMethod == AuthMethod.EMAIL) Color.White else DeepSageGreen
                                    )
                                }
                            }

                            // Google Option
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedMethod == AuthMethod.GOOGLE) DeepSageGreen else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedMethod = AuthMethod.GOOGLE }
                                    .testTag("tab_auth_google")
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = null,
                                        tint = if (selectedMethod == AuthMethod.GOOGLE) Color.White else DeepSageGreen,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isTamil) "Google" else "Google",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedMethod == AuthMethod.GOOGLE) Color.White else DeepSageGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (responsive.isSmallPhone) 12.dp else 16.dp))

            // 2. Authentication Input & Action Based on Selected Method
            when (selectedMethod) {
                AuthMethod.PHONE -> {
                    // --- PHONE AUTHENTICATION VIEW ---
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBg),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.cardBorder)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(if (responsive.isSmallPhone) 12.dp else 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = if (isTamil) "தொலைபேசி எண்" else "Phone Number",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreenHeader
                            )

                            OutlinedTextField(
                                value = phoneNumber,
                                onValueChange = { phoneNumber = it },
                                label = { Text(if (isTamil) "பதிவுசெய்த மொபைல் எண்" else "Registered Mobile Number", fontSize = if (responsive.isSmallPhone) 12.sp else 14.sp) },
                                placeholder = { Text(if (isTamil) "10 இலக்க மொபைல் எண்" else "10-digit mobile number") },
                                leadingIcon = {
                                    Icon(Icons.Default.Phone, contentDescription = null, tint = DeepSageGreen)
                                },
                                prefix = {
                                    Text("+91 ", fontWeight = FontWeight.Bold, color = DeepSageGreen)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone,
                                    imeAction = if (isOtpSent) ImeAction.Next else ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { focusManager.clearFocus() }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("login_phone_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DeepSageGreen,
                                    unfocusedBorderColor = AppTheme.colors.cardBorder,
                                    focusedLabelColor = DeepSageGreen,
                                    focusedTextColor = AppTheme.colors.textPrimary,
                                    unfocusedTextColor = AppTheme.colors.textPrimary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )

                            if (isOtpSent) {
                                OutlinedTextField(
                                    value = otpCode,
                                    onValueChange = { otpCode = it },
                                    label = { Text(if (isTamil) "4 இலக்க OTP ஐ உள்ளிடவும்" else "Enter 4-Digit OTP", fontSize = if (responsive.isSmallPhone) 12.sp else 14.sp) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Lock, contentDescription = null, tint = DeepSageGreen)
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            focusManager.clearFocus()
                                            if (phoneNumber.isNotBlank() && otpCode.isNotBlank()) {
                                                onLoginSuccess(phoneNumber, otpCode)
                                            }
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("login_otp_input"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = DeepSageGreen,
                                        unfocusedBorderColor = AppTheme.colors.cardBorder,
                                        focusedLabelColor = DeepSageGreen,
                                        focusedTextColor = AppTheme.colors.textPrimary,
                                        unfocusedTextColor = AppTheme.colors.textPrimary
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        if (onVerifyOtpRequested != null) {
                                            onVerifyOtpRequested(otpCode.trim())
                                        } else {
                                            onLoginSuccess(phoneNumber, otpCode)
                                        }
                                    },
                                    enabled = !isLoggingIn && phoneNumber.isNotBlank() && otpCode.isNotBlank(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (responsive.isSmallPhone) 46.dp else 50.dp)
                                        .testTag("verify_otp_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = DeepSageGreen)
                                ) {
                                    if (isLoggingIn) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            modifier = Modifier.size(22.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text(
                                            text = if (isTamil) "OTP சரிபார்த்து பயன்பாட்டைத் திறக்கவும்" else "Verify OTP & Open App",
                                            fontSize = if (responsive.isSmallPhone) 14.sp else 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        isRequestingOtpLocally = true
                                        if (onSendOtpRequested != null) {
                                            onSendOtpRequested(phoneNumber.trim()) {
                                                isRequestingOtpLocally = false
                                                isOtpSent = true
                                            }
                                        } else {
                                            isRequestingOtpLocally = false
                                            isOtpSent = true
                                        }
                                    },
                                    enabled = !isLoggingIn && !isRequestingOtpLocally && phoneNumber.isNotBlank(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (responsive.isSmallPhone) 46.dp else 50.dp)
                                        .testTag("send_otp_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = DeepSageGreen)
                                ) {
                                    if (isLoggingIn || isRequestingOtpLocally) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            modifier = Modifier.size(22.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text(
                                            text = if (isTamil) "தொடர்க / உள்நுழைவு OTP பெறுக" else "Continue / Get Login OTP",
                                            fontSize = if (responsive.isSmallPhone) 14.sp else 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                AuthMethod.EMAIL -> {
                    // --- EMAIL + PASSWORD AUTHENTICATION VIEW ---
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBg),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.cardBorder)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(if (responsive.isSmallPhone) 12.dp else 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isSignUpMode) (if (isTamil) "மின்னஞ்சல் கணக்கை உருவாக்கவும்" else "Create Email Account") else (if (isTamil) "மின்னஞ்சல் மூலம் உள்நுழைவு" else "Email Sign-In"),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ForestGreenHeader
                                )

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SoftSageGreen.copy(alpha = 0.6f),
                                    modifier = Modifier.clickable {
                                        isSignUpMode = !isSignUpMode
                                        emailError = null
                                        passwordError = null
                                    }
                                ) {
                                    Text(
                                        text = if (isSignUpMode) (if (isTamil) "உள்நுழைவுக்கு மாறவும்" else "Switch to Sign In") else (if (isTamil) "புதியவரா? பதிவு செய்க" else "New? Sign Up"),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DeepSageGreen,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            // Email Field
                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = {
                                    emailInput = it
                                    emailError = null
                                },
                                label = { Text(if (isTamil) "மின்னஞ்சல் முகவரி" else "Email Address", fontSize = if (responsive.isSmallPhone) 12.sp else 14.sp) },
                                placeholder = { Text("name@example.com") },
                                leadingIcon = {
                                    Icon(Icons.Default.Email, contentDescription = null, tint = DeepSageGreen)
                                },
                                isError = emailError != null,
                                supportingText = {
                                    if (emailError != null) {
                                        Text(text = emailError ?: "", color = AlertDueRed, fontSize = 11.sp)
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("login_email_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DeepSageGreen,
                                    unfocusedBorderColor = AppTheme.colors.cardBorder,
                                    focusedLabelColor = DeepSageGreen,
                                    focusedTextColor = AppTheme.colors.textPrimary,
                                    unfocusedTextColor = AppTheme.colors.textPrimary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )

                            // Password Field
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = {
                                    passwordInput = it
                                    passwordError = null
                                },
                                label = {
                                    Text(
                                        if (isSignUpMode) (if (isTamil) "கடவுச்சொல் (குறைந்தது 6 எழுத்துகள்)" else "Password (min 6 characters)") else (if (isTamil) "கடவுச்சொல்" else "Password"),
                                        fontSize = if (responsive.isSmallPhone) 12.sp else 14.sp
                                    )
                                },
                                placeholder = { Text(if (isTamil) "உங்கள் கடவுச்சொல்லை உள்ளிடவும்" else "Enter your password") },
                                leadingIcon = {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = DeepSageGreen)
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { passwordVisible = !passwordVisible },
                                        modifier = Modifier.testTag("login_password_visibility_toggle")
                                    ) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = if (passwordVisible) (if (isTamil) "கடவுச்சொல்லை மறைக்க" else "Hide password") else (if (isTamil) "கடவுச்சொல்லைக் காட்ட" else "Show password"),
                                            tint = AppTheme.colors.textMuted
                                        )
                                    }
                                },
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                isError = passwordError != null,
                                supportingText = {
                                    if (passwordError != null) {
                                        Text(text = passwordError ?: "", color = AlertDueRed, fontSize = 11.sp)
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { validateAndSubmitEmail() }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("login_password_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DeepSageGreen,
                                    unfocusedBorderColor = AppTheme.colors.cardBorder,
                                    focusedLabelColor = DeepSageGreen,
                                    focusedTextColor = AppTheme.colors.textPrimary,
                                    unfocusedTextColor = AppTheme.colors.textPrimary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )

                            // Submit Button
                            Button(
                                onClick = { validateAndSubmitEmail() },
                                enabled = !isLoggingIn && emailInput.isNotBlank() && passwordInput.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (responsive.isSmallPhone) 46.dp else 50.dp)
                                    .testTag(if (isSignUpMode) "login_email_signup_button" else "login_email_signin_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DeepSageGreen)
                            ) {
                                if (isLoggingIn) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = if (isSignUpMode) (if (isTamil) "கணக்கை உருவாக்கு" else "Create Account") else (if (isTamil) "மின்னஞ்சல் மூலம் உள்நுழைக" else "Sign In with Email"),
                                        fontSize = if (responsive.isSmallPhone) 14.sp else 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                AuthMethod.GOOGLE -> {
                    // --- GOOGLE AUTHENTICATION VIEW ---
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBg),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.cardBorder)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(if (responsive.isSmallPhone) 12.dp else 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = if (isTamil) "Google மூலம் உள்நுழைவு" else "Google Sign-In",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreenHeader
                            )

                            Text(
                                text = if (isTamil) "வேகமான, பாதுகாப்பான கிளவுட் காப்புப்பிரதி மற்றும் ஒத்திசைவுக்கு உங்கள் Google கணக்குடன் நேரடியாக உள்நுழையவும்." else "Sign in directly with your Google Account for fast, secure cloud backup and synchronization.",
                                fontSize = if (responsive.isSmallPhone) 12.sp else 13.sp,
                                color = AppTheme.colors.textMuted,
                                lineHeight = 18.sp
                            )

                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    if (onGoogleSignInRequested != null) {
                                        onGoogleSignInRequested()
                                    } else {
                                        onGmailLoginRequested?.invoke("")
                                    }
                                },
                                enabled = !isLoggingIn,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (responsive.isSmallPhone) 46.dp else 50.dp)
                                    .testTag("login_gmail_continue_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DeepSageGreen)
                            ) {
                                if (isLoggingIn) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = if (isTamil) "Google மூலம் தொடரவும்" else "Continue with Google",
                                        fontSize = if (responsive.isSmallPhone) 14.sp else 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Quick Select Partner (Dynamic from App Data)
            if (partners.isNotEmpty()) {
                Spacer(modifier = Modifier.height(if (responsive.isSmallPhone) 14.dp else 20.dp))

                Text(
                    text = if (isTamil) "— அல்லது பங்குதாரரை விரைவாகத் தேர்ந்தெடுக்கவும் —" else "— Or Quick Select Partner —",
                    fontSize = if (responsive.isSmallPhone) 11.sp else 12.sp,
                    color = AppTheme.colors.textMuted,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(if (responsive.isSmallPhone) 8.dp else 10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (responsive.isSmallPhone) 6.dp else 8.dp)
                ) {
                    partners.forEach { partner ->
                        val sanitizedTag = partner.name.lowercase().replace(" ", "_")
                        OutlinedButton(
                            onClick = {
                                phoneNumber = partner.phone
                                otpCode = ""
                                isOtpSent = false
                                selectedMethod = AuthMethod.PHONE
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_login_$sanitizedTag"),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = partner.name,
                                    textAlign = TextAlign.Center,
                                    fontSize = if (responsive.isSmallPhone) 11.sp else 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "(${partner.role})",
                                    textAlign = TextAlign.Center,
                                    fontSize = if (responsive.isSmallPhone) 9.sp else 10.sp,
                                    color = AppTheme.colors.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (responsive.isSmallPhone) 12.dp else 20.dp))
        }
    }
}
