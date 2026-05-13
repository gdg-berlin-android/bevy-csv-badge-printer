package de.berlindroid.bevybadgeprinter

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import de.berlindroid.bevybadgeprinter.bevy.Bevy
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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

        object Loading : State()

        data class Error(
            val throwable: Throwable,
            val previousState: State,
        ) : State()

        object EnterApiToken : State()

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
                val attendees: List<Attendee>
            ) : Authenticated(token)
        }

        data class CreateNewAttendee(
            val previousState: Authenticated.CheckAttendeesIn
        ) : State()

        data class ShowAdditionalAttendees(
            val attendees: List<String>,
            val previousState: State
        ) : State()
    }

    val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

    val bevy by lazy { Bevy() }
    private var _state by mutableStateOf<State>(State.Initializing)
    val state: State get() = _state

    fun updateStateFromPreferences() {
        _state = State.Loading

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
                            ).results.map { it.toView() }
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
                    state.previousState

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
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            unset(ADDITIONAL_ATTENDEES)
            unset(EVENT_ID)
            unset(CHAPTER_ID)
            unset(API_TOKEN)
        }
    }

    fun showAdditionals() {
        viewModelScope.launch {
            val additionals = get(ADDITIONAL_ATTENDEES)?.split(",") ?: listOf()
            _state = State.ShowAdditionalAttendees(
                additionals,
                _state
            )
        }
    }

    fun deleteAdditionals() {
        viewModelScope.launch {
            unset(ADDITIONAL_ATTENDEES)
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
        _state = State.Loading

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
                _state = State.Loading

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
                    Chapter(chapter.id, chapter.description),
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
                _state = State.Loading

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
                    attendees
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
        // TODO PRINT

        // TODO CHECKIN
        (_state as? State.Authenticated.CheckAttendeesIn)?.let { selectAttendeeState ->
            viewModelScope.launch {
                val updatedAttendees = bevy.checkInAttendee(
                    selectAttendeeState.token,
                    selectAttendeeState.event.id,
                    attendee.id,
                    !attendee.checkedIn
                )

                _state = selectAttendeeState.copy(
                    attendees = selectAttendeeState.attendees.map { oldAttendee ->
                        val newAttendeeWithId = updatedAttendees.attendees.firstOrNull { newAttendee ->
                            newAttendee.id == oldAttendee.id
                        }

                        if (newAttendeeWithId != null && newAttendeeWithId.isCheckedIn != oldAttendee.checkedIn) {
                            oldAttendee.copy(checkedIn = newAttendeeWithId.isCheckedIn)
                        } else {
                            oldAttendee
                        }
                    }
                )
            }
        }
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
                Log.i("YOOL", "New attendee created: $name.")

                set(ADDITIONAL_ATTENDEES, "${get(ADDITIONAL_ATTENDEES)}$name,")

                /// TODO PRINT

                createState.previousState
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

private fun Bevy.Service.Chapter.toView(): Chapter = Chapter(id, description)

private fun Bevy.Service.Event.toView(): Event = Event(id, title, bannerUrl)

private fun Bevy.Service.DetailedEvent.toView(): Event = Event(id, title, bannerUrl)

private fun Bevy.Service.Attendee.toView(): Attendee = Attendee(id, "$firstName $lastName", isCheckedIn)


private fun Int?.nullIfZero(): Int? = if (this == 0) null else this
