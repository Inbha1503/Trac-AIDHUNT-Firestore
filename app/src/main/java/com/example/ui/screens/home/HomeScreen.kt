package com.example.ui.screens.home

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.JobEntryEntity
import com.example.pdf.PdfGeneratorHelper
import com.example.ui.components.FlatPdfIconButton
import com.example.ui.components.FlatShareIconButton
import com.example.ui.components.MetricCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.buildJobWhatsAppMessage
import com.example.ui.components.formatDate
import com.example.ui.components.formatDateTime
import com.example.ui.components.formatInr
import com.example.ui.components.openDialer
import com.example.ui.components.openWhatsApp
import com.example.ui.components.sendWhatsAppMessage
import com.example.ui.components.shareGenericText
import com.example.ui.theme.AppTheme
import com.example.ui.theme.AlertDueRed
import com.example.ui.theme.AlertDueRedBg
import com.example.ui.theme.CreamBackground
import com.example.ui.theme.DeepSageGreen
import com.example.ui.theme.ForestGreenHeader
import com.example.ui.theme.SageAccent
import com.example.ui.theme.SageCardBg
import com.example.ui.theme.SageOutline
import com.example.ui.theme.SoftSageGreen
import com.example.ui.theme.SuccessPaidGreen
import com.example.ui.theme.SuccessPaidGreenBg
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextSecondaryDark
import java.util.Locale

import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    settings: AppSettingsEntity,
    totalReceived: Double,
    totalPending: Double,
    availableBalance: Double,
    recentJobs: List<JobEntryEntity>,
    isOnline: Boolean = true,
    isSyncing: Boolean = false,
    unsyncedCount: Int = 0,
    syncMessage: String = "",
    onTriggerSync: () -> Unit = {},
    onFabClick: () -> Unit = {},
    onDeleteJob: (JobEntryEntity) -> Unit
) {
    val context = LocalContext.current
    var selectedJob by remember { mutableStateOf<JobEntryEntity?>(null) }
    var jobToDelete by remember { mutableStateOf<JobEntryEntity?>(null) }
    val isTamil = settings.language.equals("TA", ignoreCase = true)

    val responsive = com.example.ui.theme.rememberResponsiveDimensions()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = responsive.screenPaddingHorizontal,
                end = responsive.screenPaddingHorizontal,
                top = responsive.screenPaddingVertical,
                bottom = 88.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (responsive.isSmallPhone) 10.dp else 14.dp)
        ) {
            // 1. Redesigned Business / Profile Card (Matching navigation bar color, white text, white avatar shape)
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF072D18)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_business_profile_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(if (responsive.isSmallPhone) 12.dp else 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (responsive.isSmallPhone) 12.dp else 16.dp)
                    ) {
                        com.example.ui.components.PartnerAvatarImage(
                            photoUri = settings.profilePhotoUri,
                            name = if (settings.businessName.isNotBlank()) settings.businessName else settings.ownerName,
                            size = if (responsive.isSmallPhone) 50.dp else if (responsive.isLargePhone) 64.dp else 56.dp,
                            fallbackBgColor = Color.White,
                            fallbackTextColor = Color(0xFF072D18),
                            modifier = Modifier.testTag("home_partner_avatar")
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = settings.businessName.ifBlank { "AIDHUNT Agri & Tractor Services" },
                                fontSize = if (responsive.isSmallPhone) 17.sp else if (responsive.isLargePhone) 21.sp else 18.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (settings.ownerName.isNotBlank()) "Owner: ${settings.ownerName}" else settings.activePartnerName,
                                fontSize = if (responsive.isSmallPhone) 12.5.sp else 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.88f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 2. Financial Metrics Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (responsive.isSmallPhone) 8.dp else 12.dp)
                ) {
                    MetricCard(
                        title = "Total Received",
                        amount = totalReceived,
                        subtitle = "Collections",
                        icon = Icons.Default.CheckCircle,
                        containerColor = SuccessPaidGreenBg,
                        contentColor = SuccessPaidGreen,
                        titleColor = Color(0xFF14532D),
                        modifier = Modifier.weight(1f)
                    )

                    MetricCard(
                        title = "Total Due",
                        amount = totalPending,
                        subtitle = "Pending Dues",
                        icon = Icons.Default.PendingActions,
                        containerColor = AlertDueRedBg,
                        contentColor = AlertDueRed,
                        titleColor = Color(0xFF991B1B),
                        modifier = Modifier.weight(1f),
                        isNegative = true
                    )
                }
            }

            // 3. Available Business Balance
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBg),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.cardBorder.copy(alpha = 0.7f))),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(if (responsive.isSmallPhone) 12.dp else 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = "Available Business Balance",
                                fontSize = if (responsive.isSmallPhone) 12.sp else 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppTheme.colors.textPrimary,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Cash in hand",
                                fontSize = if (responsive.isSmallPhone) 10.5.sp else 11.sp,
                                color = AppTheme.colors.textMuted,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatInr(availableBalance, settings.currency),
                            fontSize = if (responsive.isSmallPhone) 16.sp else if (responsive.isLargePhone) 20.sp else 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (availableBalance >= 0) DeepSageGreen else AlertDueRed,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 4. Recent Job Entries Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Job Entries",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )

                    Text(
                        text = "${recentJobs.size} Entries",
                        fontSize = 12.sp,
                        color = SageAccent,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 5. Job List
            if (recentJobs.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Agriculture,
                                contentDescription = null,
                                tint = SageAccent,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No tractor job entries yet",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppTheme.colors.textPrimary
                            )
                            Text(
                                text = "Tap '+ New Job' to record your first field work",
                                fontSize = 12.sp,
                                color = AppTheme.colors.textSecondary
                            )
                        }
                    }
                }
            } else {
                items(recentJobs, key = { it.id }) { job ->
                    JobEntryItemCard(
                        job = job,
                        settings = settings,
                        onClick = { selectedJob = job },
                        onShareWhatsApp = {
                            val msg = buildJobWhatsAppMessage(job, settings.businessName)
                            sendWhatsAppMessage(context, job.customerPhone, msg)
                        },
                        onSharePdf = {
                            val pdfFile = PdfGeneratorHelper.generateJobReceiptPdf(context, settings, job)
                            if (pdfFile != null) {
                                PdfGeneratorHelper.sharePdf(context, pdfFile, "Tractor Job Work Slip - Entry #${job.id} (${job.customerName})")
                            } else {
                                Toast.makeText(context, "Could not generate PDF receipt", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }

        // Restored Floating Action Button for New Job
        FloatingActionButton(
            onClick = onFabClick,
            containerColor = DeepSageGreen,
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = responsive.screenPaddingHorizontal, bottom = 16.dp)
                .testTag("home_fab_new_job")
                .height(if (responsive.isSmallPhone) 48.dp else 54.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = if (responsive.isSmallPhone) 12.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Job",
                    tint = Color.White,
                    modifier = Modifier.size(if (responsive.isSmallPhone) 20.dp else 22.dp)
                )
                Text(
                    text = if (isTamil) "புதிய வேலை" else "New Job",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = if (responsive.isSmallPhone) 13.sp else 14.sp
                )
            }
        }
    }

    // Customer Summary Popup Dialog
    selectedJob?.let { job ->
        CustomerSummaryPopup(
            job = job,
            settings = settings,
            onDismiss = { selectedJob = null },
            onCallCustomer = {
                if (job.customerPhone.isNotBlank()) {
                    openDialer(context, job.customerPhone)
                } else {
                    Toast.makeText(context, "No phone number available for ${job.customerName}", Toast.LENGTH_SHORT).show()
                }
            },
            onShareWhatsApp = {
                val msg = buildJobWhatsAppMessage(job, settings.businessName)
                sendWhatsAppMessage(context, job.customerPhone, msg)
            }
        )
    }

    // Confirm Delete Dialog
    jobToDelete?.let { job ->
        AlertDialog(
            onDismissRequest = { jobToDelete = null },
            title = { Text("Delete Job Record?") },
            text = {
                Text("Are you sure you want to delete the job entry #${job.id} for '${job.customerName}' (${formatInr(job.totalAmount, settings.currency)})? This will update the customer's balance due.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteJob(job)
                        jobToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertDueRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { jobToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun getCustomerInitials(name: String): String {
    val clean = name.trim()
    if (clean.isBlank()) return "C"
    val parts = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "C"
        parts.size == 1 -> parts[0].take(2).uppercase(java.util.Locale.getDefault())
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase(java.util.Locale.getDefault())
    }
}

private data class CustomerAvatarColor(val bg: Color, val text: Color)

private fun getCustomerAvatarColor(name: String): CustomerAvatarColor {
    val palette = listOf(
        CustomerAvatarColor(bg = Color(0xFFDCFCE7), text = Color(0xFF166534)), // Light Mint Green
        CustomerAvatarColor(bg = Color(0xFFFFEDD5), text = Color(0xFFC2410C)), // Light Warm Orange
        CustomerAvatarColor(bg = Color(0xFFE0E7FF), text = Color(0xFF3730A3)), // Light Indigo
        CustomerAvatarColor(bg = Color(0xFFFCE7F3), text = Color(0xFF9D174D)), // Light Rose
        CustomerAvatarColor(bg = Color(0xFFE0F2FE), text = Color(0xFF0369A1)), // Light Sky
        CustomerAvatarColor(bg = Color(0xFFFEF3C7), text = Color(0xFF92400E))  // Light Amber
    )
    val index = kotlin.math.abs(name.hashCode()) % palette.size
    return palette[index]
}

@Composable
fun JobEntryItemCard(
    job: JobEntryEntity,
    settings: AppSettingsEntity,
    onClick: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onSharePdf: () -> Unit
) {
    val responsive = com.example.ui.theme.rememberResponsiveDimensions()
    val isPaid = job.pendingAmount <= 0.0
    val avatarColor = getCustomerAvatarColor(job.customerName)
    val initials = getCustomerInitials(job.customerName)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBg),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.cardBorder.copy(alpha = 0.7f))),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("job_item_${job.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (responsive.isSmallPhone) 12.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: Avatar + Name & Phone & #ID Badge on Left + Amount & Status on Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Customer Avatar with Initials
                Box(
                    modifier = Modifier
                        .size(if (responsive.isSmallPhone) 42.dp else 46.dp)
                        .clip(CircleShape)
                        .background(avatarColor.bg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        fontSize = if (responsive.isSmallPhone) 14.sp else 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = avatarColor.text
                    )
                }

                // 2. Customer Name with #ID badge on the left & Phone
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = job.customerName.ifBlank { "Customer" },
                            fontSize = if (responsive.isSmallPhone) 15.sp else 16.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFDCFCE7)
                        ) {
                            Text(
                                text = "#${job.id}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF166534),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (job.customerPhone.isNotBlank()) {
                        Text(
                            text = job.customerPhone,
                            fontSize = if (responsive.isSmallPhone) 12.5.sp else 13.sp,
                            color = Color(0xFF4B5563),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 3. Status Amount + Chevron Right on top, Paid/Due underneath
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (isPaid) formatInr(0.0, settings.currency) else formatInr(job.pendingAmount, settings.currency),
                            fontSize = if (responsive.isSmallPhone) 14.5.sp else 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPaid) Color(0xFF166534) else Color(0xFFDC2626),
                            maxLines = 1
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "View Details",
                            tint = Color(0xFF9CA3AF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = if (isPaid) "Paid" else "Due",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPaid) Color(0xFF166534) else Color(0xFFDC2626),
                        maxLines = 1
                    )
                }
            }

            Divider(
                color = Color(0xFFF3F4F6),
                thickness = 1.dp
            )

            // Bottom Section: Even and adaptive layout distributed across full width
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left side: Duration & Total Amount
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Color(0xFF6B7280),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = com.example.ui.util.WorkBillingCalculator.formatDuration(job.durationMinutes),
                            fontSize = if (responsive.isSmallPhone) 11.5.sp else 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF374151)
                        )
                    }

                    Text(
                        text = "•",
                        color = Color(0xFFD1D5DB),
                        fontSize = 11.sp
                    )

                    Text(
                        text = formatInr(job.totalAmount, settings.currency),
                        fontSize = if (responsive.isSmallPhone) 12.sp else 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )
                }

                // Right side: Tractor & Operator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Agriculture,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(13.5.dp)
                        )
                        Text(
                            text = job.tractorLabel.ifBlank { "Mahindra 575 DI" },
                            fontSize = if (responsive.isSmallPhone) 11.5.sp else 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF4B5563),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val operatorDisplay = if (job.operatorName.isNotBlank()) job.operatorName else if (settings.ownerName.isNotBlank()) settings.ownerName else ""
                    if (operatorDisplay.isNotBlank()) {
                        Text(
                            text = "•",
                            color = Color(0xFFD1D5DB),
                            fontSize = 11.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color(0xFF9CA3AF),
                                modifier = Modifier.size(12.5.dp)
                            )
                            Text(
                                text = operatorDisplay,
                                fontSize = if (responsive.isSmallPhone) 11.sp else 11.5.sp,
                                color = Color(0xFF6B7280),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerSummaryPopup(
    job: JobEntryEntity,
    settings: AppSettingsEntity,
    onDismiss: () -> Unit,
    onCallCustomer: () -> Unit,
    onShareWhatsApp: () -> Unit
) {
    val responsive = com.example.ui.theme.rememberResponsiveDimensions()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (responsive.isSmallPhone) 16.dp else 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 400.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(if (responsive.isSmallPhone) 16.dp else 20.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    // 1. Header: Circular Avatar, Name & Phone, Call Button, Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            val initial = job.customerName.trim().take(1).uppercase(Locale.getDefault()).ifBlank { "C" }
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFE8F5E9),
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = initial,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SuccessPaidGreen
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = job.customerName.ifBlank { "—" },
                                    fontSize = if (responsive.isSmallPhone) 16.sp else 17.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF111827),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (job.customerPhone.isNotBlank()) job.customerPhone else "No phone number",
                                    fontSize = 13.sp,
                                    color = Color(0xFF6B7280),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (job.customerPhone.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFDCFCE7),
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable(onClick = onCallCustomer)
                                        .testTag("modal_header_call_btn")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Call,
                                            contentDescription = "Call Customer",
                                            tint = SuccessPaidGreen,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("modal_header_close_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xFF6B7280),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // 2. Call Customer Card
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE5E7EB))
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                enabled = job.customerPhone.isNotBlank(),
                                onClick = onCallCustomer
                            )
                            .testTag("btn_call_customer_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFDCFCE7),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Call,
                                        contentDescription = null,
                                        tint = SuccessPaidGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Call Customer",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF111827)
                                )
                                Text(
                                    text = if (job.customerPhone.isNotBlank()) "Tap to call using phone number" else "Phone number unavailable",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF6B7280)
                                )
                            }
                        }
                    }

                    // 3. Financial Summary Card (2x2 Metric Card with individual icons and divider)
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE5E7EB))
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("modal_financial_summary_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Row 1: Total Amount & Total Time
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Metric 1: Total Amount
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFFE8F5E9),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.Payments,
                                                contentDescription = null,
                                                tint = SuccessPaidGreen,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Total Amount",
                                            fontSize = 11.sp,
                                            color = Color(0xFF6B7280),
                                            fontWeight = FontWeight.Normal
                                        )
                                        Text(
                                            text = formatInr(job.totalAmount, settings.currency),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF111827),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // Metric 2: Total Time
                                val durationStr = remember(job.durationMinutes) {
                                    com.example.ui.util.WorkBillingCalculator.formatDuration(job.durationMinutes)
                                }
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFFF3F4F6),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.AccessTime,
                                                contentDescription = null,
                                                tint = Color(0xFF4B5563),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Total Time",
                                            fontSize = 11.sp,
                                            color = Color(0xFF6B7280),
                                            fontWeight = FontWeight.Normal
                                        )
                                        Text(
                                            text = durationStr,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF111827),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // Horizontal Divider
                            Divider(
                                color = Color(0xFFE5E7EB),
                                thickness = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Row 2: Paid & Balance Due
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Metric 3: Paid
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFFDCFCE7),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.AccountBalanceWallet,
                                                contentDescription = null,
                                                tint = SuccessPaidGreen,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Paid",
                                            fontSize = 11.sp,
                                            color = Color(0xFF6B7280),
                                            fontWeight = FontWeight.Normal
                                        )
                                        Text(
                                            text = formatInr(job.amountReceived, settings.currency),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SuccessPaidGreen,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // Metric 4: Balance Due
                                val balanceDue = job.pendingAmount
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (balanceDue > 0) Color(0xFFFEE2E2) else Color(0xFFDCFCE7),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.ReceiptLong,
                                                contentDescription = null,
                                                tint = if (balanceDue > 0) AlertDueRed else SuccessPaidGreen,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Balance Due",
                                            fontSize = 11.sp,
                                            color = Color(0xFF6B7280),
                                            fontWeight = FontWeight.Normal
                                        )
                                        Text(
                                            text = formatInr(balanceDue, settings.currency),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (balanceDue > 0) AlertDueRed else SuccessPaidGreen,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Tractor / Operator Card
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE5E7EB))
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("modal_tractor_operator_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Tractor Section
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFE8F5E9),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Agriculture,
                                            contentDescription = "Tractor",
                                            tint = SuccessPaidGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Tractor",
                                        fontSize = 11.sp,
                                        color = Color(0xFF6B7280),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = job.tractorLabel.ifBlank { "—" },
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF111827),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Operator Section
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFE8F5E9),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = "Operator",
                                            tint = SuccessPaidGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Operator",
                                        fontSize = 11.sp,
                                        color = Color(0xFF6B7280),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = job.operatorName.ifBlank { "—" },
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF111827),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // 5. WhatsApp Button
                    Button(
                        onClick = onShareWhatsApp,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessPaidGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_modal_share_whatsapp")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Share on WhatsApp",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
