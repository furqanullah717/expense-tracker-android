package com.codewithfk.expensetracker.android

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.codewithfk.expensetracker.android.data.model.ExpenseEntity
import com.codewithfk.expensetracker.android.ui.theme.Zinc
import com.codewithfk.expensetracker.android.viewmodel.HomeViewModel
import com.codewithfk.expensetracker.android.viewmodel.HomeViewModelFactory
import com.codewithfk.expensetracker.android.widget.ExpenseTextView
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.CartesianChartHost
import com.patrykandpatrick.vico.compose.chart.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.chart.layer.rememberLineSpec
import com.patrykandpatrick.vico.compose.chart.rememberCartesianChart
import com.patrykandpatrick.vico.compose.chart.zoom.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.component.shape.shader.color
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.component.marker.MarkerComponent
import com.patrykandpatrick.vico.core.component.shape.shader.DynamicShaders
import com.patrykandpatrick.vico.core.model.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.model.lineSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random
import android.graphics.Typeface
import android.text.Layout
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.component.fixed
import com.patrykandpatrick.vico.compose.component.rememberLayeredComponent
import com.patrykandpatrick.vico.compose.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.component.shape.dashedShape
import com.patrykandpatrick.vico.compose.component.shape.markerCorneredShape
import com.patrykandpatrick.vico.compose.dimensions.dimensionsOf
import com.patrykandpatrick.vico.core.chart.dimensions.HorizontalDimensions
import com.patrykandpatrick.vico.core.chart.insets.Insets
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.patrykandpatrick.vico.core.component.shape.cornered.Corner
import com.patrykandpatrick.vico.core.component.text.TextComponent
import com.patrykandpatrick.vico.core.context.MeasureContext
import com.patrykandpatrick.vico.core.extension.copyColor
import com.patrykandpatrick.vico.core.marker.Marker


@Composable
fun HomeScreen(navController: NavController) {
    val viewModel = HomeViewModelFactory(LocalContext.current).create(HomeViewModel::class.java)
    Surface(modifier = Modifier.fillMaxSize()) {
        ConstraintLayout(modifier = Modifier.fillMaxSize()) {
            val (nameRow, list, card, topBar, add) = createRefs()
            Image(painter = painterResource(id = R.drawable.ic_topbar), contentDescription = null,
                modifier = Modifier.constrainAs(topBar) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                })
            Box(modifier = Modifier
                .fillMaxWidth()
                .padding(top = 64.dp, start = 16.dp, end = 16.dp)
                .constrainAs(nameRow) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }) {
                Column(modifier = Modifier.align(Alignment.CenterStart)) {
                    ExpenseTextView(text = "Good Afernoon", fontSize = 16.sp, color = Color.White)
                    ExpenseTextView(
                        text = "CodeWithFK",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Image(
                    painter = painterResource(id = R.drawable.ic_notification),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }

            val state = viewModel.expenses.collectAsState(initial = emptyList())
            val expense = viewModel.getTotalExpense(state.value)
            val income = viewModel.getTotalIncome(state.value)
            val balance = viewModel.getBalance(state.value)
            CardItem(
                modifier = Modifier.constrainAs(card) {
                    top.linkTo(nameRow.bottom)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                },
                balance = balance, income = income, expense = expense
            )
            TransactionList(
                modifier = Modifier
                    .fillMaxWidth()
                    .constrainAs(list) {
                        top.linkTo(card.bottom)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                        bottom.linkTo(parent.bottom)
                        height = Dimension.fillToConstraints
                    }, list = state.value,
                viewModel
            )

            Image(
                painter = painterResource(id = android.R.drawable.ic_menu_add),
                contentDescription = null,
                modifier = Modifier
                    .constrainAs(add) {
                        bottom.linkTo(parent.bottom)
                        end.linkTo(parent.end)
                    }
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable {
                        navController.navigate("/add")
                    }
            )
        }
    }
}


@Composable
fun CardItem(
    modifier: Modifier,
    balance: String, income: String, expense: String
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Zinc)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column {
                ExpenseTextView(text = "Total Balance", fontSize = 16.sp, color = Color.White)
                ExpenseTextView(
                    text = balance, fontSize = 20.sp, color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Image(
                painter = painterResource(id = R.drawable.dots_menu),
                contentDescription = null,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            CardRowItem(
                modifier = Modifier
                    .align(Alignment.CenterStart),
                title = "Income",
                amount = income,
                imaget = R.drawable.ic_income
            )
            Spacer(modifier = Modifier.size(8.dp))
            CardRowItem(
                modifier = Modifier
                    .align(Alignment.CenterEnd),
                title = "Expense",
                amount = expense,
                imaget = R.drawable.ic_expense
            )
        }

    }
}


@Composable
fun TransactionList(modifier: Modifier, list: List<ExpenseEntity>, viewModel: HomeViewModel) {
    LazyColumn(modifier = modifier.padding(horizontal = 16.dp)) {
        item {
            Column {
                Box(modifier = modifier.fillMaxWidth()) {
                    ExpenseTextView(
                        text = "Recent Transactions",
                        fontSize = 20.sp,
                    )
                    ExpenseTextView(
                        text = "See all",
                        fontSize = 16.sp,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }
        items(list) { item ->
            val icon = viewModel?.getItemIcon(item)
            TransactionItem(
                title = item.title,
                amount = item.amount.toString(),
                icon = icon!!,
                date = item.date,
                color = if (item.type == "Income") Color.Green else Color.Red
            )
        }

    }
}

@Composable
fun TransactionItem(
    title: String,
    amount: String,
    icon: Int,
    date: String,
    color: Color
) {

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row() {
            Image(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier.size(50.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Column {
                ExpenseTextView(text = title, fontSize = 16.sp)
                ExpenseTextView(text = date, fontSize = 12.sp)
            }
        }
        ExpenseTextView(
            text = amount,
            fontSize = 20.sp,
            modifier = Modifier.align(Alignment.CenterEnd),
            color = color
        )
    }
}

@Composable
fun CardRowItem(modifier: Modifier, title: String, amount: String, imaget: Int) {
    Column(modifier = modifier) {
        Row {

            Image(
                painter = painterResource(id = imaget),
                contentDescription = null,
            )
            Spacer(modifier = Modifier.size(8.dp))
            ExpenseTextView(text = title, fontSize = 16.sp, color = Color.White)
        }
        ExpenseTextView(text = amount, fontSize = 20.sp, color = Color.White)
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    HomeScreen(rememberNavController())
}


@Composable
internal fun rememberMarker(labelPosition: MarkerComponent.LabelPosition = MarkerComponent.LabelPosition.Top): Marker {
    val labelBackgroundShape = Shapes.markerCorneredShape(Corner.FullyRounded)
    val labelBackground =
        rememberShapeComponent(labelBackgroundShape, MaterialTheme.colorScheme.surface)
            .setShadow(
                radius = LABEL_BACKGROUND_SHADOW_RADIUS_DP,
                dy = LABEL_BACKGROUND_SHADOW_DY_DP,
                applyElevationOverlay = true,
            )
    val label =
        rememberTextComponent(
            color = MaterialTheme.colorScheme.onSurface,
            background = labelBackground,
            padding = dimensionsOf(8.dp, 4.dp),
            typeface = Typeface.MONOSPACE,
            textAlignment = Layout.Alignment.ALIGN_CENTER,
            minWidth = TextComponent.MinWidth.fixed(40.dp),
        )
    val indicatorFrontComponent =
        rememberShapeComponent(Shapes.pillShape, MaterialTheme.colorScheme.surface)
    val indicatorCenterComponent = rememberShapeComponent(Shapes.pillShape)
    val indicatorRearComponent = rememberShapeComponent(Shapes.pillShape)
    val indicator =
        rememberLayeredComponent(
            rear = indicatorRearComponent,
            front =
            rememberLayeredComponent(
                rear = indicatorCenterComponent,
                front = indicatorFrontComponent,
                padding = dimensionsOf(5.dp),
            ),
            padding = dimensionsOf(10.dp),
        )
    val guideline =
        rememberLineComponent(
            color = MaterialTheme.colorScheme.onSurface.copy(.2f),
            thickness = 2.dp,
            shape = Shapes.dashedShape(
                shape = Shapes.pillShape,
                dashLength = 8.dp,
                gapLength = 4.dp
            ),
        )
    return remember(label, labelPosition, indicator, guideline) {
        object : MarkerComponent(label, labelPosition, indicator, guideline) {
            init {
                indicatorSizeDp = 36f
                onApplyEntryColor = { entryColor ->
                    indicatorRearComponent.color = entryColor.copyColor(alpha = .15f)
                    with(indicatorCenterComponent) {
                        color = entryColor
                        setShadow(radius = 12f, color = entryColor)
                    }
                }
            }

            override fun getInsets(
                context: MeasureContext,
                outInsets: Insets,
                horizontalDimensions: HorizontalDimensions,
            ) {
                with(context) {
                    outInsets.top =
                        (
                                CLIPPING_FREE_SHADOW_RADIUS_MULTIPLIER * LABEL_BACKGROUND_SHADOW_RADIUS_DP -
                                        LABEL_BACKGROUND_SHADOW_DY_DP
                                )
                            .pixels
                    if (labelPosition == LabelPosition.AroundPoint) return
                    outInsets.top += label.getHeight(context) + labelBackgroundShape.tickSizeDp.pixels
                }
            }
        }
    }
}

private const val LABEL_BACKGROUND_SHADOW_RADIUS_DP = 4f
private const val LABEL_BACKGROUND_SHADOW_DY_DP = 2f
private const val CLIPPING_FREE_SHADOW_RADIUS_MULTIPLIER = 1.4f