package com.smartspend.ai.ai.analysis

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.smartspend.ai.R
import com.smartspend.ai.data.model.AiAnalysisEntity
import com.smartspend.ai.ui.theme.Zinc
import com.smartspend.ai.utils.Utils
import com.smartspend.ai.widget.ExpenseTextView
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

@Composable
fun AiHistoryScreen(
    navController: NavController,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val historyList by viewModel.aiHistoryList.collectAsState()

    var itemToDelete by remember { mutableStateOf<AiAnalysisEntity?>(null) }
    var itemToExport by remember { mutableStateOf<AiAnalysisEntity?>(null) }

    // Delete Confirmation Dialog
    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { ExpenseTextView(text = "Î∂ÑÏÑù Í∏∞Î°ù ??†ú", fontWeight = FontWeight.Bold) },
            text = { ExpenseTextView(text = "?†ÌÉù??AI Î∂ÑÏÑù Í∏∞Î°ù????†ú?òÏãúÍ≤†Ïäµ?àÍπå? (Î°úÏª¨ Î∞??¥Îùº?∞Îìú?êÏÑú Î™®Îëê ??†ú?©Îãà??)") },
            confirmButton = {
                TextButton(onClick = {
                    itemToDelete?.let { viewModel.deleteHistoryReport(it) }
                    itemToDelete = null
                }) {
                    ExpenseTextView(text = "??†ú", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    ExpenseTextView(text = "Ï∑®ÏÜå")
                }
            }
        )
    }

    // Export & Download JSON Dialog
    if (itemToExport != null) {
        val currentExportItem = itemToExport!!
        val jsonString = remember(currentExportItem) {
            formatEntityToJson(currentExportItem)
        }
        val safeTitle = remember(currentExportItem) {
            currentExportItem.title.replace(Regex("[^a-zA-Z0-9Í∞Ä-??-]"), "_")
        }
        val filename = "report_${currentExportItem.id}_$safeTitle.json"

        AlertDialog(
            onDismissRequest = { itemToExport = null },
            title = {
                ExpenseTextView(
                    text = "JSON Î∂ÑÏÑù ?∞Ïù¥??,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Zinc
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ExpenseTextView(
                        text = "?åÏùº: $filename",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Zinc
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF263238)
                    ) {
                        Text(
                            text = jsonString,
                            color = Color(0xFFECEFF1),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .padding(10.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 1. Direct Download to device's Download folder
                    Button(
                        onClick = {
                            val success = saveJsonToDownloads(context, filename, jsonString)
                            if (success) {
                                Toast.makeText(context, "?§Ïö¥Î°úÎìú ?¥Îçî??$filename ?Ä???ÑÎ£å!", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "?§Ïö¥Î°úÎìú ?§Ìå®", Toast.LENGTH_SHORT).show()
                            }
                            itemToExport = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Zinc),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        ExpenseTextView(text = "?ì• Í∏∞Í∏∞???§Ïö¥Î°úÎìú", color = Color.White, fontSize = 13.sp)
                    }
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // 2. Share .json file via Android Share Sheet
                    OutlinedButton(
                        onClick = {
                            shareJsonFile(context, currentExportItem)
                            itemToExport = null
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        ExpenseTextView(text = "?ì§ ?åÏùº Í≥µÏú†", color = Zinc, fontSize = 13.sp)
                    }
                    TextButton(onClick = { itemToExport = null }) {
                        ExpenseTextView(text = "?´Í∏∞", fontSize = 13.sp)
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_back),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clickable {
                            navController.navigateUp()
                        },
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.outline)
                )
                ExpenseTextView(
                    text = "AI Î∂ÑÏÑù Í∏∞Î°ù",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    ) { paddingValues ->
        if (historyList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ExpenseTextView(
                        text = "?§ñ",
                        fontSize = 48.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ExpenseTextView(
                        text = "?Ä?•Îêú AI Î∂ÑÏÑù Í∏∞Î°ù???ÜÏäµ?àÎã§.",
                        fontSize = 16.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ExpenseTextView(
                        text = "?µÍ≥Ñ ?îÎ©¥?êÏÑú 'Î∂ÑÏÑù?òÍ∏∞'Î•??åÎü¨ Î∂ÑÏÑù???§Ìñâ??Î≥¥ÏÑ∏??",
                        fontSize = 13.sp,
                        color = Color.LightGray
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                }
                items(historyList, key = { it.id }) { item ->
                    AiHistoryCard(
                        item = item,
                        onExportClick = { itemToExport = item },
                        onDeleteClick = { itemToDelete = item }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiHistoryCard(
    item: AiAnalysisEntity,
    onExportClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var isReportExpanded by remember { mutableStateOf(false) }
    var isPromptExpanded by remember { mutableStateOf(false) }
    var isRawJsonExpanded by remember { mutableStateOf(false) }
    val isDarkTheme = isSystemInDarkTheme()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Title & Export & Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpenseTextView(
                    text = item.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Zinc,
                    modifier = Modifier.weight(1f)
                )

                // Export (.json File Share / Download) Icon Button
                IconButton(
                    onClick = onExportClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "JSON ?¥Î≥¥?¥Í∏∞/?§Ïö¥Î°úÎìú",
                        tint = Zinc,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Spacer(modifier = Modifier.size(4.dp))

                // Delete Icon Button
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "??†ú",
                        tint = Color.Gray.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Metadata Badges (Provider, Model, Response Time, Tokens, Cost, Security, Network, Device, Finance)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (item.firestoreId.isEmpty()) {
                    InfoBadge(
                        text = "?îí Î°úÏª¨ ?ÑÏö© (???¨Ïã§??????†ú)",
                        bgColor = Color(0xFFFFF3E0),
                        textColor = Color(0xFFE65100)
                    )
                } else {
                    InfoBadge(
                        text = "?ÅÔ∏è ?¥Îùº?∞Îìú ?Ä?•Îê®",
                        bgColor = if (isDarkTheme) Color(0xFF0D47A1) else Color(0xFFE3F2FD),
                        textColor = if (isDarkTheme) Color(0xFFBBDEFB) else Color(0xFF1976D2)
                    )
                }
                
                InfoBadge(text = "?è∑Ô∏?${item.provider}", bgColor = Zinc.copy(alpha = 0.12f), textColor = Zinc)
                InfoBadge(text = "?§ñ ${item.modelName}")
                InfoBadge(text = "?õ†Ô∏?${item.agentVersion}")
                InfoBadge(text = "?±Ô∏è ?åÏöî?úÍ∞Ñ: ${Utils.formatDurationMs(item.responseTimeMs)}")
                if (item.totalTokens > 0) {
                    InfoBadge(text = "?™ô ?†ÌÅ∞: ${item.totalTokens} (?ÖÎ†• ${item.promptTokens} / Ï∂úÎ†• ${item.candidatesTokens})")
                }
                if (item.estimatedCostKrw > 0) {
                    InfoBadge(
                        text = "?íµ ?àÏÉÅÎπÑÏö©: ${Utils.formatCost(item.estimatedCostKrw)}",
                        bgColor = if (isDarkTheme) Color(0xFF1B5E20) else Color(0xFFE8F5E9),
                        textColor = if (isDarkTheme) Color(0xFFC8E6C9) else Color(0xFF2E7D32)
                    )
                }
                InfoBadge(text = "?î¢ ?¥Ïó≠: ${item.transactionCount}Í±?)
                if (item.totalExpenseSum > 0) {
                    InfoBadge(text = "?í∏ ÏßÄÏ∂úÌï©Í≥? ${Utils.formatCurrency(item.totalExpenseSum)}")
                }
                if (item.totalIncomeSum > 0) {
                    InfoBadge(text = "?í∞ ?òÏûÖ?©Í≥Ñ: ${Utils.formatCurrency(item.totalIncomeSum)}")
                }
                if (item.authMethod.isNotBlank()) {
                    InfoBadge(text = "?õ°Ô∏?${item.authMethod}")
                }
                if (item.networkType.isNotBlank()) {
                    InfoBadge(text = "?åê ${item.networkType}")
                }
                if (item.deviceModel.isNotBlank()) {
                    InfoBadge(text = "?ì± ${item.deviceModel}")
                }
                InfoBadge(text = "?ìÖ Í∏∞Í∞Ñ: ${item.startDate} ~ ${item.endDate}")
                InfoBadge(text = "?ïí ?ùÏÑ±: ${Utils.formatTimestampToDateTime(item.createdAt)}")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Expandable AI Report Content Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { isReportExpanded = !isReportExpanded }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpenseTextView(
                    text = "?ìä AI ?îÏïΩ Î≥¥Í≥†??,
                    fontSize = 13.sp,
                    color = Zinc,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (isReportExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Zinc,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(
                visible = isReportExpanded,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Zinc.copy(alpha = 0.06f))
                        .padding(12.dp)
                ) {
                    ExpenseTextView(
                        text = "?ìä ?îÏïΩ",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ExpenseTextView(
                        text = item.summary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    if (item.insights.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        ExpenseTextView(
                            text = "?í° Ï£ºÏöî ?∏ÏÇ¨?¥Ìä∏",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        item.insights.split("\n").filter { it.isNotBlank() }.forEach { insight ->
                            ExpenseTextView(
                                text = if (insight.startsWith("??)) insight else "??$insight",
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    if (item.savingTips.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        ExpenseTextView(
                            text = "?í∞ ?àÏïΩ ??,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        item.savingTips.split("\n").filter { it.isNotBlank() }.forEach { tip ->
                            ExpenseTextView(
                                text = if (tip.startsWith("??)) tip else "??$tip",
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Expandable Gemini Input Prompt Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { isPromptExpanded = !isPromptExpanded }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpenseTextView(
                    text = "?§ñ Gemini ?ÑÏÜ° ?ÑÎ°¨?ÑÌä∏ (ÏµúÏ¢Ö ?ÖÎ†• ${item.promptCharLength}??",
                    fontSize = 12.sp,
                    color = Zinc,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (isPromptExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Zinc,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(
                visible = isPromptExpanded,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF263238)
                ) {
                    Text(
                        text = item.geminiPrompt,
                        color = Color(0xFFECEFF1),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Expandable Raw JSON Response Section
            if (item.rawResponseJson.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isRawJsonExpanded = !isRawJsonExpanded }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExpenseTextView(
                        text = "?ìÑ AI ?ëÎãµ ?êÎ≥∏ JSON (${item.responseCharLength}??",
                        fontSize = 12.sp,
                        color = Color(0xFF546E7A),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = if (isRawJsonExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color(0xFF546E7A),
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(
                    visible = isRawJsonExpanded,
                    enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                    exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E293B)
                    ) {
                        Text(
                            text = item.rawResponseJson,
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InfoBadge(
    text: String,
    bgColor: Color = Zinc.copy(alpha = 0.12f),
    textColor: Color = Zinc
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor
    ) {
        ExpenseTextView(
            text = text,
            fontSize = 11.sp,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

fun saveJsonToDownloads(context: Context, filename: String, jsonContent: String): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(jsonContent.toByteArray(Charsets.UTF_8))
                }
                true
            } else {
                false
            }
        } else {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            val file = File(downloadDir, filename)
            file.writeText(jsonContent, Charsets.UTF_8)
            true
        }
    } catch (e: Exception) {
        Log.e("AiHistoryScreen", "Failed to save file to downloads", e)
        false
    }
}

fun shareJsonFile(context: Context, item: AiAnalysisEntity) {
    try {
        val jsonString = formatEntityToJson(item)
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) {
            reportsDir.mkdirs()
        }

        // Clean filename (e.g. report_1_26??1??1??Î∂ÑÏÑùÍ∏∞Î°ù.json)
        val safeTitle = item.title.replace(Regex("[^a-zA-Z0-9Í∞Ä-??-]"), "_")
        val file = File(reportsDir, "report_${item.id}_$safeTitle.json")
        file.writeText(jsonString, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, item.title)
            putExtra(Intent.EXTRA_TEXT, "${item.title} AI Î∂ÑÏÑù ?∞Ïù¥??(.json)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "AI Î∂ÑÏÑù Í∏∞Î°ù .json ?åÏùº ?¥Î≥¥?¥Í∏∞")
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "?åÏùº Í≥µÏú† Ï§??§Î•òÍ∞Ä Î∞úÏÉù?àÏäµ?àÎã§: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun formatEntityToJson(item: AiAnalysisEntity): String {
    val jsonObject = buildJsonObject {
        put("id", item.id)
        put("userId", item.userId)
        put("firestoreId", item.firestoreId)
        put("title", item.title)
        put("startDate", item.startDate)
        put("endDate", item.endDate)
        put("transactionCount", item.transactionCount)
        put("totalExpenseSum", item.totalExpenseSum)
        put("totalIncomeSum", item.totalIncomeSum)
        put("responseTimeMs", item.responseTimeMs)
        put("responseTimeFormatted", Utils.formatDurationMs(item.responseTimeMs))
        put("promptTokens", item.promptTokens)
        put("candidatesTokens", item.candidatesTokens)
        put("totalTokens", item.totalTokens)
        put("estimatedCostUsd", item.estimatedCostUsd)
        put("estimatedCostKrw", item.estimatedCostKrw)
        put("modelName", item.modelName)
        put("agentVersion", item.agentVersion)
        put("provider", item.provider)
        put("authMethod", item.authMethod)
        put("appCheckStatus", item.appCheckStatus)
        put("networkType", item.networkType)
        put("deviceModel", item.deviceModel)
        put("osVersion", item.osVersion)
        put("promptCharLength", item.promptCharLength)
        put("responseCharLength", item.responseCharLength)
        put("finishReason", item.finishReason)
        put("summary", item.summary)
        put(
            "insights",
            JsonArray(item.insights.split("\n").filter { it.isNotBlank() }.map { JsonPrimitive(it) })
        )
        put(
            "savingTips",
            JsonArray(item.savingTips.split("\n").filter { it.isNotBlank() }.map { JsonPrimitive(it) })
        )
        put("geminiPrompt", item.geminiPrompt)
        put("rawResponseJson", item.rawResponseJson)
        put("createdAt", item.createdAt)
        put("createdAtFormatted", Utils.formatTimestampToDateTime(item.createdAt))
    }
    val jsonFormatter = Json { prettyPrint = true }
    return jsonFormatter.encodeToString(JsonObject.serializer(), jsonObject)
}
