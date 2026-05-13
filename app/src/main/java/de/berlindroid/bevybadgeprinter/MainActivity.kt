package de.berlindroid.bevybadgeprinter

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import de.berlindroid.bevybadgeprinter.BevyViewModel.Attendee
import de.berlindroid.bevybadgeprinter.BevyViewModel.State
import de.berlindroid.bevybadgeprinter.ui.theme.BevyBadgePrinterTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: BevyViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback { if (vm.canGoBack()) vm.goBack() }

        enableEdgeToEdge()

        setContent {
            BevyBadgePrinterTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(stringResource(R.string.app_name))
                            },
                            navigationIcon = {
                                MainNavigationIcon(vm)
                            },
                            actions = {
                                IconButton(onClick = { vm.deleteAdditionals() }) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                                }
                                IconButton(onClick = { vm.showAdditionals() }) {
                                    Icon(imageVector = Icons.Default.Person, contentDescription = null)
                                }
                            }
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                ) { innerPadding ->
                    Box(
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        MainSelectorView(
                            vm
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BoxScope.MainSelectorView(
    vm: BevyViewModel
) {
    val context = LocalContext.current

    when (val state = vm.state) {
        is State.Initializing -> {
            vm.updateStateFromPreferences()
            LoadingView()
        }

        is State.Loading -> LoadingView()

        is State.Error -> ErrorView(state.throwable, vm::copyToClipboard, vm::dismissError)

        is State.EnterApiToken -> EnterValueView(
            titleRes = R.string.enter_token_title,
            textLabelRes = R.string.enter_token_label,
            valueEntered = vm::apiTokenEntered
        )

        is State.Authenticated.EnterChapterId -> EnterValueView(
            titleRes = R.string.enter_chapter_id_title,
            textLabelRes = R.string.enter_chapter_id_label,
            valueEntered = vm::chapterIdEntered
        )

        is State.Authenticated.SelectEvent -> SelectEventView(
            events = state.events,
            eventSelected = vm::eventSelected
        )

        is State.Authenticated.CheckAttendeesIn -> CheckAttendeesInView(
            attendees = state.attendees,
            attendeesByHand = state.attendeesByHand,
            createNewAttendee = vm::createNewAttendee,
            attendeeSelected = vm::attendeeSelected,
        )

        is State.CreateNewAttendee -> EnterNewAttendeeDetailView(
            onCompleted = vm::newAttendeeCreated
        )

        is State.ShowAdditionalAttendees -> ShowAdditionalAttendeesView(
            attendees = state.attendees,
            copyToClipboard = vm::copyToClipboard,
            onDone = {
                vm.goBack()
            },
        )

        is State.Authenticated.ConfirmAttendeePrint -> ConfirmBadgePrintView(
            state.attendee,
            onConfirmed = { attendee, badge ->
                vm.printConfirmed(
                    attendee,
                    context,
                    badge
                )
            },
            onDismiss = vm::goBack,
        )
    }
}

@Composable
fun MainNavigationIcon(
    vm: BevyViewModel
) {
    if (vm.canGoBack()) {
        val arrowBack = Icons.AutoMirrored.Default.ArrowBack

        IconButton(
            onClick = vm::goBack
        ) {
            Icon(imageVector = arrowBack, contentDescription = null)
        }
    }
}

@Preview
@Composable
private fun ErrorViewPreview() {
    try {
        throw IllegalStateException(
            "Sample error ocourred"
        )
    } catch (th: Throwable) {
        ErrorView(
            th, {}, {}
        )
    }
}

@Composable
fun ErrorView(
    throwable: Throwable,
    copyToClipboard: (String) -> Unit,
    dismissed: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = dismissed,
        confirmButton = {
            Button(onClick = dismissed) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            Button(onClick = { copyToClipboard(throwable.toString()) }) {
                Text(stringResource(android.R.string.copy))
            }
        },
        title = {
            Text(text = "Error Occurred")
        },
        text = {
            Text(
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                text = "$throwable"
            )
        }
    )
}

@Preview
@Composable
private fun EnterValueViewPreview() {
    EnterValueView(
        titleRes = android.R.string.VideoView_error_title,
        textLabelRes = android.R.string.VideoView_error_text_unknown,
        valueEntered = {}
    )
}

@Composable
fun EnterValueView(
    @StringRes confirmButtonTitleRes: Int = android.R.string.ok,
    @StringRes dismissButonTitleRes: Int = android.R.string.cancel,
    @StringRes titleRes: Int,
    @StringRes textLabelRes: Int,
    valueEntered: (String) -> Unit,
    dismissed: () -> Unit = { valueEntered("") }
) {
    var value: String by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = dismissed,
        confirmButton = {
            Button(
                onClick = { valueEntered(value) }
            ) {
                Text(stringResource(confirmButtonTitleRes))
            }
        },
        dismissButton = {
            Button(
                onClick = dismissed
            ) {
                Text(stringResource(dismissButonTitleRes))
            }
        },
        title = {
            Text(stringResource(titleRes))
        },
        text = {
            TextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(textLabelRes)) },
            )
        }
    )
}

@Preview
@Composable
fun LoadingView() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Loading",
            modifier = Modifier
                .scale(5f)
                .rotate(rotation)
        )
    }
}

@Preview(showSystemUi = true)
@Composable
private fun SelectEventViewPreview() {
    SelectEventView(
        listOf(
            BevyViewModel.Event(12, "Monthly Meetup", ""),
            BevyViewModel.Event(12, "GDG BERLIN ANDROID PRESENETA SA FDS", null),
            BevyViewModel.Event(12, "Frankensteins Book Club", "foobar"),
        ),
        {}
    )
}

@Composable
fun SelectEventView(
    events: List<BevyViewModel.Event>,
    eventSelected: (BevyViewModel.Event) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(events) { event ->
            EventView(event) {
                eventSelected(event)
            }
        }
    }
}

@Preview
@Composable
private fun EventViewPreview() {
    EventView(
        BevyViewModel.Event(134, "Google IO Extended and Watch Party", null)
    ) {}
}

@Composable
fun EventView(
    event: BevyViewModel.Event,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(4.dp)
            .size(150.dp)
            .clickable(
                onClick = onClick
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            event.bannerUrl?.let { banner ->
                AsyncImage(
                    model = banner,
                    contentDescription = null
                )
            } ?: Image(
                painter = painterResource(android.R.drawable.ic_btn_speak_now),
                contentDescription = null
            )

            Text(
                modifier = Modifier.blur(10.dp, BlurredEdgeTreatment.Unbounded),
                text = event.name,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                modifier = Modifier.blur(0.1.dp, BlurredEdgeTreatment.Unbounded),
                text = event.name,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun CheckAttendeesInViewPreview() {
    CheckAttendeesInView(
        listOf(
            BevyViewModel.Attendee(12, "Pegg Nose Pete", false),
            BevyViewModel.Attendee(654, "Checked", true),
        ),
        listOf(
            BevyViewModel.Attendee(-1, "New Peete", false),
            BevyViewModel.Attendee(-1, "New Peete", true),
        ),
        {}
    ) {}
}

@Composable
fun CheckAttendeesInView(
    attendees: List<Attendee>,
    attendeesByHand: List<BevyViewModel.Attendee>,
    attendeeSelected: (BevyViewModel.Attendee) -> Unit,
    createNewAttendee: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var filteredAttendees by remember { mutableStateOf(attendees + attendeesByHand) }
    var tempName by remember { mutableStateOf("") }

    var firstTime by remember { mutableStateOf(true) }
    LaunchedEffect(firstTime) {
        if (firstTime) {
            focusRequester.requestFocus()
        }
        firstTime = false
    }

    val refreshState = rememberPullToRefreshState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pullToRefresh(
                state = refreshState,
                isRefreshing = refreshState.isAnimating,
                enabled = true,
                onRefresh = {
                    tempName = ""
                    filteredAttendees = attendees + attendeesByHand
                }
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp)
                    .focusRequester(focusRequester),
                value = tempName,
                label = { Text(stringResource(R.string.attendee_search_label)) },
                trailingIcon = {
                    Row {
                        IconButton(
                            onClick = {
                                tempName = ""
                                filteredAttendees = attendees + attendeesByHand
                            }
                        ) { Icon(imageVector = Icons.Default.Delete, contentDescription = null) }
                        IconButton(
                            onClick = {
                                filteredAttendees = (attendees + attendeesByHand).filter { it.checkedIn }
                            }
                        ) { Icon(imageVector = Icons.Default.Check, contentDescription = null) }
                        IconButton(
                            onClick = {
                                filteredAttendees = (attendees + attendeesByHand).filter { it.isArtificial }
                            }
                        ) { Text("😅") }
                    }
                },
                onValueChange = { new ->
                    tempName = new
                    filteredAttendees = if (new.length > 2) {
                        (attendees + attendeesByHand).filter { attendee ->
                            attendee.name.contains(tempName, ignoreCase = true)
                        }
                    } else {
                        attendees + attendeesByHand
                    }
                },
            )
            Button(
                onClick = createNewAttendee
            ) {
                Text(text = stringResource(R.string.attendee_button_new))
            }
        }
        Text(
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End,
            text = "Found ${filteredAttendees.size} attendee${if (filteredAttendees.size == 1) "" else "s"}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            columns = GridCells.Adaptive(300.dp)
        ) {
            items(filteredAttendees) { attendee ->
                AttendeeView(
                    attendee,
                ) {
                    attendeeSelected(attendee)
                }
            }
        }
    }
}

@Preview
@Composable
private fun AttendeeViewPreview() {
    AttendeeView(
        BevyViewModel.Attendee(124, "Pete Poke", false),
    ) {}
}

@Preview
@Composable
private fun AttendeeViewCheckedPreview() {
    AttendeeView(
        BevyViewModel.Attendee(124, "Pete Poke", true),
    ) {}
}

@Composable
fun AttendeeView(
    attendee: BevyViewModel.Attendee,
    onClick: () -> Unit,
) {
    val containerColor = when {
        attendee.isArtificial -> Color(0xFFA08000)
        else -> Color.DarkGray
    }

    val cardColor = CardDefaults.cardColors(
        containerColor = containerColor
    )

    Card(
        modifier = Modifier
            .padding(4.dp)
            .size(300.dp, 150.dp)
            .clickable(
                onClick = onClick
            ),
        colors = cardColor
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (attendee.checkedIn) {
                Text(
                    modifier = Modifier.fillMaxSize(),
                    text = "☑",
                    fontSize = 32.sp,
                    textAlign = TextAlign.End,
                    color = Color.Green,
                )
            }

            Text(
                text = attendee.name,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 32.sp,
                    color = Color.White,
                    lineHeight = .85.em,
                )
            )
        }
    }
}

@Preview
@Composable
private fun NewAttendeeViewPreview() {
    NewAttendeeView {}
}

@Composable
fun NewAttendeeView(
    onClick: () -> Unit,
) {
    AttendeeView(
        BevyViewModel.Attendee(
            0,
            stringResource(R.string.attendee_button_new),
            false,
        ), onClick
    )
}

@Preview
@Composable
private fun EnterNewAttendeeDetailViewPreview() {
    EnterNewAttendeeDetailView {}
}

@Composable
fun EnterNewAttendeeDetailView(
    onCompleted: (String) -> Unit,
) {
    var tempUser: String by remember { mutableStateOf("") }

    AlertDialog(
        title = { Text(stringResource(R.string.create_attendee_title)) },
        onDismissRequest = { onCompleted("") },
        confirmButton = {
            Button(onClick = { onCompleted(tempUser) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            Button(onClick = { onCompleted(tempUser) }) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        text = {
            LazyColumn {
                item {
                    Text(
                        text = stringResource(R.string.create_attendee_description)
                    )
                }
                item {
                    TextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = tempUser,
                        onValueChange = { tempUser = it },
                        label = { Text(text = stringResource(R.string.create_attendee_label)) }
                    )
                }
            }
        }
    )
}


@Preview
@Composable
private fun ShowAdditionalAttendeesViewPreview() {
    ShowAdditionalAttendeesView(
        listOf(
            Attendee(-1, "Foo Bar", false),
            Attendee(-1, "Foo Bar", true)
        ), {}
    ) {}
}

@Composable
fun ShowAdditionalAttendeesView(
    attendees: List<Attendee>,
    copyToClipboard: (String) -> Unit,
    onDone: () -> Unit,
) {
    AlertDialog(
        title = { Text(text = stringResource(R.string.additionals_title)) },
        onDismissRequest = onDone,
        dismissButton = {
            Button(onClick = {
                copyToClipboard(attendees.joinToString())
            }
            ) { Text(stringResource(android.R.string.copy)) }
        },
        confirmButton = {
            Button(onClick = onDone) { Text(stringResource(android.R.string.ok)) }
        },
        text = {
            LazyColumn {
                items(attendees) { attendee ->
                    Text(text = "${if (attendee.checkedIn) "☑" else "☐"}: ${attendee.name}")
                }
            }
        }
    )
}

@Composable
fun ConfirmBadgePrintView(
    attendee: BevyViewModel.Attendee,
    onConfirmed: (attendee: Attendee, badge: Bitmap) -> Unit,
    onDismiss: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()

    var bitmap: Bitmap? by remember { mutableStateOf(null) }

    AlertDialog(
        title = { Text(text = stringResource(R.string.confirm_badge_title)) },
        onDismissRequest = onDismiss,
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmed(
                        attendee,
                        bitmap ?: throw IllegalStateException("Button should not be enabled.")
                    )
                },
                enabled = bitmap != null
            ) {
                Text(stringResource(R.string.confirm_badge_print_button_title))
            }
        },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .size(300.dp, 200.dp)
                        .drawWithContent {
                            graphicsLayer.record {
                                this@drawWithContent.drawContent()
                            }
                            drawContent()

                            coroutineScope.launch {
                                bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                            }
                        }
                ) {
                    Badge(
                        modifier = Modifier.size(300.dp, 200.dp),
                        name = attendee.name,
                    )
                }

                Text(text = attendee.name)
            }
        }
    )
}
