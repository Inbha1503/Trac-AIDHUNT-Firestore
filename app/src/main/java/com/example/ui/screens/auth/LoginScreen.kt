package com.example.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    GMAIL
}

@Composable
fun LoginScreen(
    partners: List<PartnerEntity> = emptyList(),
    onSendOtp: (phone: String, onCodeSent: (String) -> Unit, onError: (String) -> Unit) -> Unit,
    onVerifyOtp: (phone: String, verificationId: String, otp: String) -> Unit,
    onGmailLoginRequested: ((email: String) -> Unit)? = null,
    isLoggingIn: Boolean = false,
    initialAuthMethod: AuthMethod = AuthMethod.PHONE
) {
    var selectedMethod by remember { mutableStateOf(initialAuthMethod) }

    // Phone Auth State
    var phoneNumber by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var isOtpSent by remember { mutableStateOf(false) }
    var verificationId by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf<String?>(null) }
    var isSendingOtp by remember { mutableStateOf(false) }

    // Gmail Auth State
    var gmailAddress by remember { mutableStateOf("") }

    val responsive = rememberResponsiveDimensions()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current

    val cleanDigits = phoneNumber.filter { it.isDigit() }
    val isPhoneValid = cleanDigits.length in 10..12

    val isEmailValid = remember(gmailAddress) {
        val trimmed = gmailAddress.trim()
        trimmed.isNotBlank() &&
                trimmed.contains("@") &&
                trimmed.contains(".") &&
                "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex().matches(trimmed)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AppTheme.colors.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    horizontal = responsive.screenPaddingHorizontal,
                    vertical = responsive.screenPaddingVertical
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(if (responsive.isSmallPhone) 10.dp else 16.dp))

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
                    contentDescription = "AIDHUNT Trac Logo",
                    tint = Color.White,
                    modifier = Modifier.size(if (responsive.isSmallPhone) 36.dp else 44.dp)
                )
            }

            Spacer(modifier = Modifier.height(if (responsive.isSmallPhone) 8.dp else 12.dp))

            Text(
                text = "AIDHUNT Trac",
                fontSize = if (responsive.isSmallPhone) 22.sp else 26.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )

            Text(
                text = "Shared Tractor Business Management",
                fontSize = if (responsive.isSmallPhone) 12.sp else 14.sp,
                color = DeepSageGreen,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(if (responsive.isSmallPhone) 12.dp else 18.dp))

            // Shared Account Notice Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBg),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.cardBorder)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(if (responsive.isSmallPhone) 10.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = DeepSageGreen,
                        modifier = Modifier.size(if (responsive.isSmallPhone) 18.dp else 20.dp)
                    )
                    Text(
                        text = "Single Shared Login: Owner & Partners share this account with 100% offline & auto-cloud sync across all devices.",
                        fontSize = if (responsive.isSmallPhone) 11.sp else 12.sp,
                        lineHeight = if (responsive.isSmallPhone) 15.sp else 17.sp,
                        color = AppTheme.colors.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (responsive.isSmallPhone) 12.dp else 16.dp))

            // 1. Authentication Method Selector Card
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
                        text = "Login Method",
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
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Phone Number",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedMethod == AuthMethod.PHONE) Color.White else DeepSageGreen
                                    )
                                }
                            }

                            // Gmail Option
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedMethod == AuthMethod.GMAIL) DeepSageGreen else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedMethod = AuthMethod.GMAIL }
                                    .testTag("tab_auth_gmail")
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = null,
                                        tint = if (selectedMethod == AuthMethod.GMAIL) Color.White else DeepSageGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Gmail",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedMethod == AuthMethod.GMAIL) Color.White else DeepSageGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (responsive.isSmallPhone) 12.dp else 16.dp))

            // 2. Authentication Input & Action Based on Selected Method
            if (selectedMethod == AuthMethod.PHONE) {
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
                            text = "Phone Number",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreenHeader
                        )

                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            label = { Text("Registered Mobile Number", fontSize = if (responsive.isSmallPhone) 12.sp else 14.sp) },
                            placeholder = { Text("9842154321") },
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
                                label = { Text("Enter 4-Digit OTP (Default: 8890)", fontSize = if (responsive.isSmallPhone) 12.sp else 14.sp) },
                                placeholder = { Text("8890") },
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
                                            authError = null
                                            onVerifyOtp(phoneNumber, verificationId, otpCode)
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

                            if (authError != null) {
                                Text(
                                    text = authError ?: "",
                                    color = AlertDueRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                    textAlign = TextAlign.Start
                                )
                            }

                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    authError = null
                                    onVerifyOtp(phoneNumber, verificationId, otpCode)
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
                                        text = "Verify OTP & Open App",
                                        fontSize = if (responsive.isSmallPhone) 14.sp else 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        } else {
                            if (authError != null) {
                                Text(
                                    text = authError ?: "",
                                    color = AlertDueRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                    textAlign = TextAlign.Start
                                )
                            }

                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    isSendingOtp = true
                                    authError = null
                                    onSendOtp(
                                        phoneNumber,
                                        { verId ->
                                            verificationId = verId
                                            isOtpSent = true
                                            isSendingOtp = false
                                        },
                                        { error ->
                                            authError = error
                                            isSendingOtp = false
                                        }
                                    )
                                },
                                enabled = !isSendingOtp && phoneNumber.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (responsive.isSmallPhone) 46.dp else 50.dp)
                                    .testTag("send_otp_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DeepSageGreen)
                            ) {
                                if (isSendingOtp) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = "Continue / Get Login OTP",
                                        fontSize = if (responsive.isSmallPhone) 14.sp else 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // --- GMAIL AUTHENTICATION VIEW ---
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
                            text = "Gmail Address",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreenHeader
                        )

                        OutlinedTextField(
                            value = gmailAddress,
                            onValueChange = { gmailAddress = it },
                            label = { Text("Gmail Address", fontSize = if (responsive.isSmallPhone) 12.sp else 14.sp) },
                            placeholder = { Text("name@gmail.com") },
                            leadingIcon = {
                                Icon(Icons.Default.Email, contentDescription = null, tint = DeepSageGreen)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    if (isEmailValid) {
                                        onGmailLoginRequested?.invoke(gmailAddress.trim())
                                    }
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_gmail_input"),
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
                                onGmailLoginRequested?.invoke(gmailAddress.trim())
                            },
                            enabled = isEmailValid,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (responsive.isSmallPhone) 46.dp else 50.dp)
                                .testTag("login_gmail_continue_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DeepSageGreen)
                        ) {
                            Text(
                                text = "Continue with Gmail",
                                fontSize = if (responsive.isSmallPhone) 14.sp else 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // 3. Quick Select Partner (Dynamic from App Data)
            if (partners.isNotEmpty()) {
                Spacer(modifier = Modifier.height(if (responsive.isSmallPhone) 14.dp else 20.dp))

                Text(
                    text = "— Or Quick Select Partner —",
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
                                otpCode = "8890"
                                selectedMethod = AuthMethod.PHONE
                                isSendingOtp = true
                                authError = null
                                onSendOtp(
                                    partner.phone,
                                    { verId ->
                                        verificationId = verId
                                        isOtpSent = true
                                        isSendingOtp = false
                                        onVerifyOtp(partner.phone, verId, "8890")
                                    },
                                    { error ->
                                        authError = error
                                        isSendingOtp = false
                                    }
                                )
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
