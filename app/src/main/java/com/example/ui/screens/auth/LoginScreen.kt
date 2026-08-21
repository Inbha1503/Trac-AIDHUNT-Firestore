package com.example.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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

enum class AuthScreenMode {
    SIGN_IN,
    CREATE_ACCOUNT
}

enum class AuthMethod {
    EMAIL,
    PHONE
}

@Composable
fun GoogleLogoIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val strokeWidth = w * 0.22f
        val radius = (w - strokeWidth) / 2f

        // Google Red
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 200f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = Offset(cx - radius, cy - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        // Google Yellow
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 120f,
            sweepAngle = 80f,
            useCenter = false,
            topLeft = Offset(cx - radius, cy - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        // Google Green
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 30f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(cx - radius, cy - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        // Google Blue (Arc + Bar)
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = 300f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(cx - radius, cy - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        // Center Bar
        drawLine(
            color = Color(0xFF4285F4),
            start = Offset(cx, cy),
            end = Offset(cx + radius, cy),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun LoginScreen(
    partners: List<PartnerEntity> = emptyList(),
    onGoogleSignIn: (isCreatingAccount: Boolean, businessName: String, ownerName: String, onError: (String) -> Unit) -> Unit,
    onGoogleSignInDirect: (email: String, displayName: String, isCreatingAccount: Boolean, businessName: String, ownerName: String, onError: (String) -> Unit) -> Unit = { _, _, _, _, _, _ -> },
    onSendOtp: (phone: String, onCodeSent: (String) -> Unit, onError: (String) -> Unit) -> Unit,
    onVerifyOtp: (phone: String, verificationId: String, otp: String, onError: (String) -> Unit) -> Unit,
    onEmailLogin: (email: String, pass: String, onError: (String) -> Unit) -> Unit,
    onCreateAccountEmail: (email: String, pass: String, businessName: String, ownerName: String, phone: String, onError: (String) -> Unit) -> Unit,
    onCreateAccountPhone: (verificationId: String, otp: String, phone: String, businessName: String, ownerName: String, onError: (String) -> Unit) -> Unit,
    onDemoLogin: (partner: PartnerEntity, onError: (String) -> Unit) -> Unit,
    isLoggingIn: Boolean = false
) {
    var screenMode by remember { mutableStateOf(AuthScreenMode.SIGN_IN) }
    var selectedMethod by remember { mutableStateOf(AuthMethod.EMAIL) }

    // Common Profile Fields (for Account Creation)
    var businessName by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }

    // Google Sign In Dialog State
    var showGoogleDialog by remember { mutableStateOf(false) }
    var googleEmailInput by remember { mutableStateOf("inbhapalanikumar@gmail.com") }
    var googleNameInput by remember { mutableStateOf("Inbha Palanikumar") }

    // Phone Auth State
    var phoneNumber by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var isOtpSent by remember { mutableStateOf(false) }
    var verificationId by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf<String?>(null) }
    var isSendingOtp by remember { mutableStateOf(false) }

    // Email Auth State
    var emailAddress by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    val responsive = rememberResponsiveDimensions()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current

    val isEmailValid = remember(emailAddress) {
        val trimmed = emailAddress.trim()
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
            Spacer(modifier = Modifier.height(if (responsive.isSmallPhone) 6.dp else 12.dp))

            // App Emblem
            Box(
                modifier = Modifier
                    .size(if (responsive.isSmallPhone) 54.dp else 64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(DeepSageGreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Agriculture,
                    contentDescription = "AIDHUNT Trac Logo",
                    tint = Color.White,
                    modifier = Modifier.size(if (responsive.isSmallPhone) 32.dp else 40.dp)
                )
            }

            Spacer(modifier = Modifier.height(if (responsive.isSmallPhone) 6.dp else 8.dp))

            Text(
                text = "AIDHUNT Trac",
                fontSize = if (responsive.isSmallPhone) 22.sp else 26.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary
            )

            Text(
                text = "Shared Tractor Fleet & Cloud Accounting",
                fontSize = if (responsive.isSmallPhone) 12.sp else 13.sp,
                color = DeepSageGreen,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(if (responsive.isSmallPhone) 10.dp else 14.dp))

            // Mode Selector: Sign In vs Create New Account
            TabRow(
                selectedTabIndex = if (screenMode == AuthScreenMode.SIGN_IN) 0 else 1,
                containerColor = AppTheme.colors.cardBg,
                contentColor = DeepSageGreen,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[if (screenMode == AuthScreenMode.SIGN_IN) 0 else 1]),
                        color = DeepSageGreen,
                        height = 3.dp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = screenMode == AuthScreenMode.SIGN_IN,
                    onClick = {
                        screenMode = AuthScreenMode.SIGN_IN
                        authError = null
                    },
                    text = {
                        Text(
                            text = "Sign In",
                            fontWeight = FontWeight.Bold,
                            fontSize = if (responsive.isSmallPhone) 13.sp else 14.sp,
                            color = if (screenMode == AuthScreenMode.SIGN_IN) DeepSageGreen else AppTheme.colors.textMuted
                        )
                    },
                    modifier = Modifier.testTag("tab_sign_in")
                )
                Tab(
                    selected = screenMode == AuthScreenMode.CREATE_ACCOUNT,
                    onClick = {
                        screenMode = AuthScreenMode.CREATE_ACCOUNT
                        authError = null
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (screenMode == AuthScreenMode.CREATE_ACCOUNT) DeepSageGreen else AppTheme.colors.textMuted
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Create Account",
                                fontWeight = FontWeight.Bold,
                                fontSize = if (responsive.isSmallPhone) 13.sp else 14.sp,
                                color = if (screenMode == AuthScreenMode.CREATE_ACCOUNT) DeepSageGreen else AppTheme.colors.textMuted
                            )
                        }
                    },
                    modifier = Modifier.testTag("tab_create_account")
                )
            }

            Spacer(modifier = Modifier.height(if (responsive.isSmallPhone) 10.dp else 12.dp))

            // Main Auth Form Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBg),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.cardBorder)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(if (responsive.isSmallPhone) 12.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Google Sign-In / Sign-Up Button
                    OutlinedButton(
                        onClick = {
                            focusManager.clearFocus()
                            authError = null
                            onGoogleSignIn(
                                screenMode == AuthScreenMode.CREATE_ACCOUNT,
                                businessName.trim(),
                                ownerName.trim()
                            ) { error ->
                                if (error.contains("cancelled", ignoreCase = true)) {
                                    authError = error
                                } else {
                                    // Open direct Google Account picker dialog for smooth fallback
                                    showGoogleDialog = true
                                }
                            }
                        },
                        enabled = !isLoggingIn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (responsive.isSmallPhone) 46.dp else 48.dp)
                            .testTag("btn_google_signin"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(SageOutline))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            GoogleLogoIcon()
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (screenMode == AuthScreenMode.SIGN_IN) "Continue with Google" else "Create Account with Google",
                                fontSize = if (responsive.isSmallPhone) 13.sp else 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF3C4043)
                            )
                        }
                    }

                    // Divider: OR
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = AppTheme.colors.cardBorder)
                        Text(
                            text = "  OR USE CREDENTIALS  ",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textMuted
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = AppTheme.colors.cardBorder)
                    }

                    // Method Switcher: Email vs Phone
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
                            // Email Option
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedMethod == AuthMethod.EMAIL) DeepSageGreen else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedMethod = AuthMethod.EMAIL
                                        authError = null
                                    }
                                    .testTag("tab_method_email")
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
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Email & Password",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedMethod == AuthMethod.EMAIL) Color.White else DeepSageGreen
                                    )
                                }
                            }

                            // Phone Option
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedMethod == AuthMethod.PHONE) DeepSageGreen else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedMethod = AuthMethod.PHONE
                                        authError = null
                                    }
                                    .testTag("tab_method_phone")
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
                                        text = "Phone SMS OTP",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedMethod == AuthMethod.PHONE) Color.White else DeepSageGreen
                                    )
                                }
                            }
                        }
                    }

                    // Extra fields for Create Account Mode
                    if (screenMode == AuthScreenMode.CREATE_ACCOUNT) {
                        OutlinedTextField(
                            value = businessName,
                            onValueChange = { businessName = it },
                            label = { Text("Business / Fleet Name", fontSize = if (responsive.isSmallPhone) 12.sp else 14.sp) },
                            placeholder = { Text("e.g. Karthik Agri & Tractor Works") },
                            leadingIcon = {
                                Icon(Icons.Default.Business, contentDescription = null, tint = DeepSageGreen)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_business_name"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DeepSageGreen,
                                unfocusedBorderColor = AppTheme.colors.cardBorder,
                                focusedLabelColor = DeepSageGreen,
                                focusedTextColor = AppTheme.colors.textPrimary,
                                unfocusedTextColor = AppTheme.colors.textPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = ownerName,
                            onValueChange = { ownerName = it },
                            label = { Text("Owner / Manager Name", fontSize = if (responsive.isSmallPhone) 12.sp else 14.sp) },
                            placeholder = { Text("e.g. Karthik") },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = DeepSageGreen)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_owner_name"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DeepSageGreen,
                                unfocusedBorderColor = AppTheme.colors.cardBorder,
                                focusedLabelColor = DeepSageGreen,
                                focusedTextColor = AppTheme.colors.textPrimary,
                                unfocusedTextColor = AppTheme.colors.textPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Method Fields: Email
                    if (selectedMethod == AuthMethod.EMAIL) {
                        // Email Input
                        OutlinedTextField(
                            value = emailAddress,
                            onValueChange = { emailAddress = it },
                            label = { Text("Email Address", fontSize = if (responsive.isSmallPhone) 12.sp else 14.sp) },
                            placeholder = { Text("name@agritrac.com") },
                            leadingIcon = {
                                Icon(Icons.Default.Email, contentDescription = null, tint = DeepSageGreen)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_email"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DeepSageGreen,
                                unfocusedBorderColor = AppTheme.colors.cardBorder,
                                focusedLabelColor = DeepSageGreen,
                                focusedTextColor = AppTheme.colors.textPrimary,
                                unfocusedTextColor = AppTheme.colors.textPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Password Input
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password (Min 6 chars)", fontSize = if (responsive.isSmallPhone) 12.sp else 14.sp) },
                            placeholder = { Text("••••••••") },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = DeepSageGreen)
                            },
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                                        tint = AppTheme.colors.textMuted
                                    )
                                }
                            },
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    if (isEmailValid && password.length >= 6) {
                                        authError = null
                                        if (screenMode == AuthScreenMode.SIGN_IN) {
                                            onEmailLogin(emailAddress.trim(), password) { authError = it }
                                        } else {
                                            onCreateAccountEmail(emailAddress.trim(), password, businessName, ownerName, phoneNumber) { authError = it }
                                        }
                                    }
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_password"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DeepSageGreen,
                                unfocusedBorderColor = AppTheme.colors.cardBorder,
                                focusedLabelColor = DeepSageGreen,
                                focusedTextColor = AppTheme.colors.textPrimary,
                                unfocusedTextColor = AppTheme.colors.textPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (screenMode == AuthScreenMode.CREATE_ACCOUNT) {
                            OutlinedTextField(
                                value = phoneNumber,
                                onValueChange = { phoneNumber = it },
                                label = { Text("Contact Phone (Optional)", fontSize = if (responsive.isSmallPhone) 12.sp else 14.sp) },
                                placeholder = { Text("9842154321") },
                                leadingIcon = {
                                    Icon(Icons.Default.Phone, contentDescription = null, tint = DeepSageGreen)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone,
                                    imeAction = ImeAction.Done
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_optional_phone"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DeepSageGreen,
                                    unfocusedBorderColor = AppTheme.colors.cardBorder,
                                    focusedLabelColor = DeepSageGreen,
                                    focusedTextColor = AppTheme.colors.textPrimary,
                                    unfocusedTextColor = AppTheme.colors.textPrimary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        if (authError != null) {
                            Text(
                                text = authError ?: "",
                                color = AlertDueRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                textAlign = TextAlign.Start
                            )
                        }

                        // Submit Button
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                authError = null
                                if (screenMode == AuthScreenMode.SIGN_IN) {
                                    onEmailLogin(emailAddress.trim(), password) { authError = it }
                                } else {
                                    onCreateAccountEmail(emailAddress.trim(), password, businessName, ownerName, phoneNumber) { authError = it }
                                }
                            },
                            enabled = !isLoggingIn && isEmailValid && password.length >= 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (responsive.isSmallPhone) 46.dp else 50.dp)
                                .testTag("btn_email_submit"),
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
                                    text = if (screenMode == AuthScreenMode.SIGN_IN) "Sign In to Business" else "Create Business & Start Sync",
                                    fontSize = if (responsive.isSmallPhone) 14.sp else 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    } else {
                        // Phone OTP Flow
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            label = { Text("Mobile Number", fontSize = if (responsive.isSmallPhone) 12.sp else 14.sp) },
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
                                label = { Text("Enter 6-Digit OTP (Default: 8890)", fontSize = if (responsive.isSmallPhone) 12.sp else 14.sp) },
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
                                            if (screenMode == AuthScreenMode.SIGN_IN) {
                                                onVerifyOtp(phoneNumber, verificationId, otpCode) { authError = it }
                                            } else {
                                                onCreateAccountPhone(verificationId, otpCode, phoneNumber, businessName, ownerName) { authError = it }
                                            }
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
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp),
                                    textAlign = TextAlign.Start
                                )
                            }

                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    authError = null
                                    if (screenMode == AuthScreenMode.SIGN_IN) {
                                        onVerifyOtp(phoneNumber, verificationId, otpCode) { authError = it }
                                    } else {
                                        onCreateAccountPhone(verificationId, otpCode, phoneNumber, businessName, ownerName) { authError = it }
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
                                        text = if (screenMode == AuthScreenMode.SIGN_IN) "Verify OTP & Sign In" else "Verify OTP & Create Business",
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
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp),
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
                                        text = "Send SMS OTP via Firebase",
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

            Spacer(modifier = Modifier.height(if (responsive.isSmallPhone) 12.dp else 16.dp))

            // Quick Demo Accounts & Test Access Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SoftSageGreen.copy(alpha = 0.35f)),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SageOutline.copy(alpha = 0.5f))),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(if (responsive.isSmallPhone) 10.dp else 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = DeepSageGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Instant Demo / Test Login",
                            fontSize = if (responsive.isSmallPhone) 11.sp else 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreenHeader
                        )
                    }

                    Text(
                        text = "Tap below to test local Room SQLite operations & Firestore sync immediately:",
                        fontSize = if (responsive.isSmallPhone) 10.sp else 11.sp,
                        color = AppTheme.colors.textMuted,
                        textAlign = TextAlign.Center
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(if (responsive.isSmallPhone) 6.dp else 8.dp)
                    ) {
                        val demoPartners = if (partners.isNotEmpty()) partners else listOf(
                            PartnerEntity(name = "Muthu (Owner)", phone = "+91 98421 54321", role = "OWNER"),
                            PartnerEntity(name = "Kumar (Partner)", phone = "+91 94432 10987", role = "PARTNER")
                        )

                        demoPartners.take(2).forEach { partner ->
                            val sanitizedTag = partner.name.lowercase().replace(" ", "_")
                            OutlinedButton(
                                onClick = {
                                    authError = null
                                    onDemoLogin(partner) { authError = it }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("demo_login_$sanitizedTag"),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)
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
                                        color = DeepSageGreen,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (responsive.isSmallPhone) 12.dp else 20.dp))
        }
    }

    if (showGoogleDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showGoogleDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        GoogleLogoIcon()
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (screenMode == AuthScreenMode.SIGN_IN) "Sign In with Google" else "Create Business with Google",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF202124)
                        )
                    }

                    Text(
                        text = "Choose or enter your Google Account to synchronize fleet data securely via Firebase Firestore:",
                        fontSize = 12.sp,
                        color = Color(0xFF5F6368),
                        textAlign = TextAlign.Center
                    )

                    OutlinedTextField(
                        value = googleEmailInput,
                        onValueChange = { googleEmailInput = it },
                        label = { Text("Google Email Account") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null, tint = DeepSageGreen)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("google_email_dialog_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DeepSageGreen,
                            unfocusedBorderColor = Color(0xFFDADCE0)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = googleNameInput,
                        onValueChange = { googleNameInput = it },
                        label = { Text("Account Holder / Owner Name") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = DeepSageGreen)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("google_name_dialog_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DeepSageGreen,
                            unfocusedBorderColor = Color(0xFFDADCE0)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    if (authError != null) {
                        Text(
                            text = authError ?: "",
                            color = AlertDueRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showGoogleDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancel", color = Color(0xFF5F6368))
                        }

                        Button(
                            onClick = {
                                val cleanEmail = googleEmailInput.trim().lowercase()
                                val cleanName = googleNameInput.trim().ifBlank { "Fleet Owner" }
                                if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
                                    authError = "Please enter a valid Google email"
                                    return@Button
                                }
                                showGoogleDialog = false
                                authError = null
                                onGoogleSignInDirect(
                                    cleanEmail,
                                    cleanName,
                                    screenMode == AuthScreenMode.CREATE_ACCOUNT,
                                    businessName.trim(),
                                    ownerName.trim()
                                ) { error ->
                                    authError = error
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_confirm_google_signin"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DeepSageGreen)
                        ) {
                            Text("Continue", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
