package com.smartspend.ai.feature.transaction_detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.smartspend.ai.R
import com.smartspend.ai.base.NavigationEvent
import com.smartspend.ai.ui.theme.Green
import com.smartspend.ai.ui.theme.LightGrey
import com.smartspend.ai.ui.theme.Red
import com.smartspend.ai.ui.theme.Typography
import com.smartspend.ai.ui.theme.Zinc
import com.smartspend.ai.utils.Utils
import com.smartspend.ai.widget.ExpenseTextView

@Composable
fun TransactionDetailScreen(
    navController: NavController,
    transactionId: Int,
    viewModel: TransactionDetailViewModel = hiltViewModel()
) {
    val transaction = viewModel.transaction.collectAsState()

    LaunchedEffect(transactionId) {
        viewModel.loadTransaction(transactionId)
    }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                NavigationEvent.NavigateBack -> navController.popBackStack()
                else -> {}
            }
        }
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
                        .clickable { viewModel.onEvent(TransactionDetailUiEvent.OnBackPressed) }
                )
                ExpenseTextView(
                    text = "ê±°ëž˜ ?ì„¸ ?•ë³´",
                    style = Typography.titleLarge,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            transaction.value?.let { item ->
                val icon = Utils.getItemIcon(item)
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Image(
                    painter = painterResource(id = icon),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                ExpenseTextView(
                    text = if (item.type == "Income") "?˜ìž…" else "ì§€ì¶?,
                    color = if (item.type == "Income") Green else Red,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (item.type == "Income") Green.copy(alpha = 0.1f) else Red.copy(alpha = 0.1f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                ExpenseTextView(
                    text = Utils.formatCurrency(item.amount),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                DetailRow(label = "ê±°ëž˜ëª?, value = item.title)
                if (item.category.isNotBlank()) {
                    DetailRow(label = "ì¹´í…Œê³ ë¦¬", value = item.category)
                }
                DetailRow(label = "? ì§œ", value = Utils.formatStringDateToMonthDayYear(item.date))
                DetailRow(label = "? í˜•", value = if (item.type == "Income") "?˜ìž…" else "ì§€ì¶?)
                
                Spacer(modifier = Modifier.weight(1f))
                
                Button(
                    onClick = { viewModel.onEvent(TransactionDetailUiEvent.OnDeleteClicked(item)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Red),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    ExpenseTextView(text = "ê±°ëž˜ ?´ì—­ ?? œ", color = Color.White)
                }
            } ?: run {
                ExpenseTextView(text = "ë¶ˆëŸ¬?¤ëŠ” ì¤?..")
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExpenseTextView(text = label, color = LightGrey, fontSize = 16.sp)
        Spacer(modifier = Modifier.weight(1f))
        ExpenseTextView(text = value, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}
