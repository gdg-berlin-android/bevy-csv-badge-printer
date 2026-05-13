package de.berlindroid.bevybadgeprinter

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import de.berlindroid.bevybadgeprinter.BevyViewModel.Attendee
import de.berlindroid.bevybadgeprinter.BevyViewModel.Chapter
import de.berlindroid.bevybadgeprinter.BevyViewModel.Event
import de.berlindroid.bevybadgeprinter.BevyViewModel.State.Loading.Reason
import de.berlindroid.bevybadgeprinter.bevy.Bevy
import io.nayuki.fastqrcodegen.QrCode
import io.nayuki.fastqrcodegen.QrCode.Ecc
import io.nayuki.fastqrcodegen.toBitmap
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

private val API_TOKEN = stringPreferencesKey("api_token")
private val CHAPTER_ID = intPreferencesKey("chapter_id")
private val EVENT_ID = intPreferencesKey("event_id")
private val ADDITIONAL_ATTENDEES = stringPreferencesKey("additional_attendees")

class BevyViewModel(application: Application) : AndroidViewModel(application) {
    data class Chapter(
        val id: Int,
        val name: String
    )

    data class Event(
        val id: Int,
        val name: String,
        val bannerUrl: String?
    )

    data class Attendee(
        val id: Int,
        val name: String,
        val checkedIn: Boolean,
    )

    sealed class State {
        object Initializing : State()

        data class Loading(
            val reason: Reason
        ) : State() {
            enum class Reason {
                CheckApiToken,
                CheckChapter,
                CheckEvent,
                UpdateAttendee,
                CommunicateWithBackend,
            }
        }

        data class Error(
            val throwable: Throwable,
            val previousState: State,
        ) : State() {
            init {
                Log.e("UIERROR", "UI SAID NO", throwable)
            }
        }

        object EnterApiToken : State()

        data class ScanAttendeeQrCode(
            val checkInState: Authenticated.CheckAttendeesIn
        ) : State()

        data class ShowApiTokenQrCode(
            val token: String,
            val qrCode: Bitmap,
            val previousState: State
        ) : State()

        sealed class Authenticated(
            open val token: String,
        ) : State() {
            data class EnterChapterId(
                override val token: String,
            ) : Authenticated(token)

            data class SelectEvent(
                override val token: String,
                val chapter: Chapter,
                val events: List<Event>
            ) : Authenticated(token)

            data class CheckAttendeesIn(
                override val token: String,
                val chapter: Chapter,
                val events: List<Event>,
                val event: Event,
                val attendees: List<Attendee>,
                val attendeesByHand: List<Attendee>,
            ) : Authenticated(token)

            data class ConfirmAttendeePrint(
                override val token: String,
                val chapter: Chapter,
                val events: List<Event>,
                val event: Event,
                val attendees: List<Attendee>,
                val attendeesByHand: List<Attendee>,
                val attendee: Attendee
            ) : Authenticated(token)
        }

        data class CreateNewAttendee(
            val checkInState: Authenticated.CheckAttendeesIn
        ) : State()

        data class ShowAdditionalAttendees(
            val attendees: List<Attendee>,
            val previousState: State
        ) : State()
    }

    val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

    val bevy by lazy { Bevy() }
    private var _state by mutableStateOf<State>(State.Initializing)
    val state: State get() = _state

    fun updateStateFromPreferences() {
        _state = State.Loading(Reason.CommunicateWithBackend)

        viewModelScope.launch {
            _state = if (has(API_TOKEN)) {
                val token = get(API_TOKEN)!!

                if (has(CHAPTER_ID)) {
                    val chapterId = get(CHAPTER_ID)!!
                    val chapter = bevy.getChapter(token, chapterId)

                    if (has(EVENT_ID)) {
                        val eventId = get(EVENT_ID)!!
                        val events = bevy.listEvents(token, chapterId).results.map { it.toView() }
                        val event = bevy.getEvent(token, eventId)

                        State.Authenticated.CheckAttendeesIn(
                            token = token,
                            chapter = chapter.toView(),
                            event = event.toView(),
                            events = events,
                            attendees = bevy.listAttendees(
                                token = token,
                                event = eventId,
                            ).results.map { it.toView() },
                            attendeesByHand = get(ADDITIONAL_ATTENDEES).toAttendeeList()
                        )
                    } else {
                        val listEvents = bevy.listEvents(token, chapterId)
                        val events = listEvents.results.map { it.toView() }
                        State.Authenticated.SelectEvent(
                            token = token,
                            chapter = chapter.toView(),
                            events = events
                        )
                    }
                } else {
                    State.Authenticated.EnterChapterId(
                        token = token
                    )
                }
            } else {
                State.EnterApiToken
            }
        }
    }

    fun canGoBack(): Boolean = state != State.Initializing
            && state != State.EnterApiToken

    fun goBack() {
        viewModelScope.launch {
            _state = when (val state = _state) {
                is State.CreateNewAttendee ->
                    state.checkInState

                is State.Authenticated.CheckAttendeesIn ->
                    State.Authenticated.SelectEvent(
                        state.token,
                        state.chapter,
                        state.events,
                    )

                is State.Authenticated.SelectEvent -> {
                    unset(EVENT_ID)
                    State.Authenticated.EnterChapterId(
                        state.token
                    )
                }

                is State.Authenticated.EnterChapterId -> {
                    unset(EVENT_ID)
                    unset(CHAPTER_ID)
                    State.EnterApiToken
                }

                is State.EnterApiToken -> {
                    unset(EVENT_ID)
                    unset(CHAPTER_ID)
                    unset(API_TOKEN)
                    State.Initializing
                }

                is State.Error -> state.previousState

                is State.Loading -> state

                is State.Initializing -> State.Initializing

                is State.ShowAdditionalAttendees -> state.previousState

                is State.Authenticated.ConfirmAttendeePrint -> State.Authenticated.CheckAttendeesIn(
                    token = state.token,
                    chapter = state.chapter,
                    events = state.events,
                    event = state.event,
                    attendees = state.attendees,
                    attendeesByHand = state.attendeesByHand,
                )

                is State.ScanAttendeeQrCode -> state.checkInState

                is State.ShowApiTokenQrCode -> state.previousState
            }
        }
    }

    fun copyToClipboard(message: String) {
        application.getSystemService<ClipboardManager>()!!.apply {
            setPrimaryClip(
                ClipData.newPlainText(
                    "printer", message
                )
            )
        }
    }

    fun dismissError() {
        (_state as? State.Error)?.let {
            _state = it.previousState
        }
    }

    fun apiTokenEntered(token: String) {
        _state = State.Loading(Reason.CheckApiToken)

        viewModelScope.launch {
            _state = try {
                val chapter = bevy.getChapter(
                    token,
                    981
                )
                if (chapter.title == "GDG Berlin Android") {
                    set(API_TOKEN, token)

                    State.Authenticated.EnterChapterId(token)
                } else {
                    throw IllegalStateException("Berlindroid chapter is not reachable. There is Sauerkraut in my Lederhosen.")
                }
            } catch (th: Throwable) {
                State.Error(
                    IllegalStateException("Invalid token given.", th),
                    State.EnterApiToken,
                )
            }
        }
    }

    fun chapterIdEntered(chapter: String) {
        if (_state !is State.Authenticated) {
            _state = State.Error(
                IllegalStateException("chapter id entered without an api token. Please report to HR."),
                _state
            )
        }

        val chapterId = chapter.toIntOrNull()

        if (chapterId == null) {
            _state = State.Error(
                NumberFormatException("$chapter is not a valid number."),
                _state,
            )

            return
        }

        viewModelScope.launch {
            val token = (_state as State.Authenticated).token
            try {
                _state = State.Loading(Reason.CheckChapter)

                val chapter = bevy.getChapter(
                    token,
                    chapterId
                )

                set(CHAPTER_ID, chapterId)

                val events = bevy.listEvents(
                    token,
                    chapterId
                ).results

                _state = State.Authenticated.SelectEvent(
                    token,
                    chapter.toView(),
                    events.map { it.toView() }
                )

            } catch (th: Throwable) {
                _state = State.Error(
                    th,
                    State.Authenticated.EnterChapterId(token)
                )
            }
        }
    }

    fun eventSelected(event: Event) {
        if (_state !is State.Authenticated) {
            _state = State.Error(
                IllegalStateException("chapter id entered without api token."),
                _state
            )
        }

        viewModelScope.launch {
            try {
                val eventState = _state as State.Authenticated.SelectEvent
                _state = State.Loading(Reason.CheckEvent)

                val attendees = bevy.listAttendees(
                    eventState.token,
                    event.id
                ).results.map { it.toView() }

                set(EVENT_ID, event.id)

                _state = State.Authenticated.CheckAttendeesIn(
                    eventState.token,
                    eventState.chapter,
                    eventState.events,
                    event,
                    attendees,
                    get(ADDITIONAL_ATTENDEES).toAttendeeList()
                )
            } catch (th: Throwable) {
                _state = State.Error(
                    th,
                    _state
                )
            }
        }
    }

    fun attendeeSelected(
        attendee: Attendee
    ) {
        (_state as? State.Authenticated.CheckAttendeesIn)?.let { selectAttendeeState ->
            _state = State.Loading(Reason.UpdateAttendee)
            if (attendee.checkedIn) {
                if (attendee.isArtificial) {
                    _state = selectAttendeeState.copy(
                        attendeesByHand = selectAttendeeState.attendeesByHand.map {
                            if (it.name == attendee.name) {
                                it.copy(checkedIn = false)
                            } else {
                                it
                            }
                        }
                    )
                } else {
                    viewModelScope.launch {
                        updateAttendeeOnline(
                            selectAttendeeState.token,
                            selectAttendeeState.chapter,
                            selectAttendeeState.events,
                            selectAttendeeState.event,
                            selectAttendeeState.attendees,
                            selectAttendeeState.attendeesByHand,
                            attendee.copy(checkedIn = false),
                        )
                    }
                }
            } else {
                _state = State.Authenticated.ConfirmAttendeePrint(
                    selectAttendeeState.token,
                    selectAttendeeState.chapter,
                    selectAttendeeState.events,
                    selectAttendeeState.event,
                    selectAttendeeState.attendees,
                    selectAttendeeState.attendeesByHand,
                    attendee.copy(checkedIn = true)
                )
            }
        } ?: run {
            _state = State.Error(
                IllegalStateException("Attendee selected while not in attendee selection state."),
                _state
            )
        }
    }

    fun printConfirmed(attendee: Attendee, context: Context, badge: Bitmap) {
        (_state as? State.Authenticated.ConfirmAttendeePrint)?.let { printConfirmedState ->
            viewModelScope.launch {
                val local = badge.copy(Bitmap.Config.ARGB_8888, true)
                local.shareToPrinter(context, attendee.name + attendee.id)

                if (attendee.isArtificial) {
                    updateHandishAttendees(
                        token = printConfirmedState.token,
                        chapter = printConfirmedState.chapter,
                        events = printConfirmedState.events,
                        event = printConfirmedState.event,
                        attendees = printConfirmedState.attendees,
                        attendeesByHand = printConfirmedState.attendeesByHand,
                        attendee = attendee,
                    )
                } else {
                    updateAttendeeOnline(
                        token = printConfirmedState.token,
                        chapter = printConfirmedState.chapter,
                        events = printConfirmedState.events,
                        event = printConfirmedState.event,
                        attendees = printConfirmedState.attendees,
                        attendeesByHand = printConfirmedState.attendeesByHand,
                        attendee = attendee,
                    )
                }
            }
        } ?: run {
            _state = State.Error(
                IllegalStateException("Cannot print in this state: $_state."),
                _state
            )
        }
    }

    fun showApiTokenQrCode() {
        _state = (_state as? State.Authenticated)?.let {
            val qr = QrCode.encodeText(
                it.token,
                Ecc.LOW
            ).toBitmap()

            State.ShowApiTokenQrCode(
                it.token,
                qr,
                it
            )
        } ?: State.Error(IllegalStateException("Not authenticated, cannot show authentication token."), _state)
    }

    fun scanAttendeeQrCode() {
        _state = (_state as? State.Authenticated.CheckAttendeesIn)?.let {
            State.ScanAttendeeQrCode(it)
        } ?: State.Error(IllegalStateException("Cannot scan qr code without being able to select attendees"), _state)
    }

    fun attendeeScanned(scanned: String) {
        (_state as? State.ScanAttendeeQrCode)?.let { scanState ->
            val checkAttendeesState = scanState.checkInState
            _state = checkAttendeesState

            val (eventId, attendeeId) = scanned.trim().split(":")
            (checkAttendeesState.attendees).firstOrNull {
                it.id == (attendeeId.toIntOrNull() ?: 0)
            }?.let { attendeeById ->
                attendeeSelected(attendeeById)
            } ?: run {
                _state = scanState
            }
        } ?: run {
            _state = State.Error(IllegalStateException("Cannot deal with results while not scanning."), _state)
        }
    }

    private suspend fun updateHandishAttendees(
        token: String,
        chapter: Chapter,
        events: List<Event>,
        event: Event,
        attendees: List<Attendee>,
        attendeesByHand: List<Attendee>,
        attendee: Attendee,
    ) {
        val updatedHandishAttendees = attendeesByHand.map {
            if (it.name == attendee.name) {
                it.copy(checkedIn = !it.checkedIn)
            } else {
                it
            }
        }

        set(ADDITIONAL_ATTENDEES, updatedHandishAttendees.toBlob())

        _state = State.Authenticated.CheckAttendeesIn(
            token = token,
            chapter = chapter,
            events = events,
            event = event,
            attendees = attendees,
            attendeesByHand = updatedHandishAttendees,
        )
    }

    private suspend fun updateAttendeeOnline(
        token: String,
        chapter: Chapter,
        events: List<Event>,
        event: Event,
        attendees: List<Attendee>,
        attendeesByHand: List<Attendee>,
        attendee: Attendee,
    ) {
        val updatedAttendees = bevy.checkInAttendee(
            token,
            event.id,
            attendee.id,
            attendee.checkedIn
        )

        _state = State.Authenticated.CheckAttendeesIn(
            token = token,
            chapter = chapter,
            events = events,
            event = event,
            attendees = attendees.map { oldAttendee ->
                val updatedAttendee = updatedAttendees.attendees.firstOrNull { updatedAttendee ->
                    updatedAttendee.id == oldAttendee.id
                }

                if (updatedAttendee != null) {
                    oldAttendee.copy(
                        checkedIn = updatedAttendee.isCheckedIn
                    )
                } else {
                    oldAttendee
                }
            },
            attendeesByHand = attendeesByHand,
        )
    }

    fun createNewAttendee() {
        _state =
            State.CreateNewAttendee(
                _state as State.Authenticated.CheckAttendeesIn
            )
    }

    fun newAttendeeCreated(
        name: String,
    ) {
        viewModelScope.launch {
            _state = (state as? State.CreateNewAttendee)?.let { createState ->
                val additionals = get(ADDITIONAL_ATTENDEES).toAttendeeList().toMutableList()
                additionals.add(Attendee(-1, name, false))
                set(ADDITIONAL_ATTENDEES, additionals.toBlob())

                State.Authenticated.ConfirmAttendeePrint(
                    createState.checkInState.token,
                    createState.checkInState.chapter,
                    createState.checkInState.events,
                    createState.checkInState.event,
                    createState.checkInState.attendees,
                    additionals,
                    Attendee(-1, name, false)
                )

            } ?: State.Error(
                IllegalStateException("State cannot create a new attendee."),
                _state
            )
        }
    }

    private suspend fun <T> has(key: Preferences.Key<T>): Boolean =
        get(key) != null

    private suspend fun <T> get(key: Preferences.Key<T>): T? =
        application.dataStore.data.map { prefs ->
            prefs[key]
        }.firstOrNull()

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        application.dataStore.updateData {
            it.toMutablePreferences().also {
                it[key] = value
            }
        }
    }

    private suspend fun <T> unset(key: Preferences.Key<T>) {
        application.dataStore.updateData {
            it.toMutablePreferences().also {
                it.remove(key)
            }
        }
    }
}

val Attendee.isArtificial: Boolean
    get() = this.id <= 0

private fun Bevy.Service.Chapter.toView(): Chapter = Chapter(id, title)

private fun Bevy.Service.Event.toView(): Event = Event(id, title, bannerUrl)

private fun Bevy.Service.DetailedEvent.toView(): Event = Event(id, title, bannerUrl)

private fun Bevy.Service.Attendee.toView(): Attendee = Attendee(id, "$firstName $lastName", isCheckedIn)

private fun String?.toAttendeeList(): List<Attendee> =
    orEmpty()
        .split(",")
        .mapNotNull { blob ->
            if (":" in blob) {
                val (name, checked) = blob.split(":")
                Attendee(-1, name, checked == "true")
            } else {
                null
            }
        }

private fun List<Attendee>.toBlob(): String = joinToString(separator = ",") { attendee ->
    "${attendee.name}:${attendee.checkedIn}"
}

private fun Bitmap.shareToPrinter(
    context: Context,
    name: String,
) {
    val cachePath = File(context.cacheDir, "images")
    cachePath.mkdirs()

    val stream = FileOutputStream("$cachePath/$name.png")
    compress(Bitmap.CompressFormat.PNG, 100, stream)
    stream.close()

    val imageFile = File(cachePath, "$name.png")
    val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)

    if (contentUri != null) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_VIEW
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

            setDataAndType(contentUri, "image/png")
        }

        context.startActivity(
            shareIntent,
        )
    }
}
