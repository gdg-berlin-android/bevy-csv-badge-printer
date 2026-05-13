package de.berlindroid.bevybadgeprinter.bevy

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

class Bevy {
    interface Service {
        @Serializable
        data class Links(
            val next: String?,
            val previous: String?,
        )

        @Serializable
        data class Pagination(
            @SerialName("previous_page")
            val previousPage: Int?,

            @SerialName("current_page")
            val currentPage: Int?,

            @SerialName("next_page")
            val nextPage: Int?,

            @SerialName("page_size")
            val pageSize: Int
        )

        @Serializable
        data class PaginatedEvents(
            val links: Links,
            val pagination: Pagination,
            val count: Int,
            val results: List<Event>,
        )

        @Serializable
        data class PaginatedAttendees(
            val links: Links,
            val pagination: Pagination,
            val count: Int,
            val results: List<Attendee>,
        )

        @Serializable
        data class Image(
            val url: String?,
            val path: String?,
            @SerialName("thumbnail_width")
            val thumbnailWidth: Int?,
            @SerialName("thumbnail_height")
            val thumbnailHeight: Int?,
            @SerialName("thumbnail_format")
            val thumbnailFormat: String?,
            @SerialName("thumbnail_url")
            val thumbnailUrl: String?,
        )

        @Serializable
        data class Photo(
            val id: Int,
            val order: Int,
            val picture: Image,
        )

        @Serializable
        data class Chapter(
            val id: Int,
            val title: String,
            val description: String,
            val logo: Image,
            val url: String,
            @SerialName("relative_url")
            val relativeUrl: String,
            @SerialName("chapter_location")
            val location: String,
            @SerialName("chapter_photos")
            val photos: List<Photo>?,
            val city: String,
            val country: String,
            @SerialName("country_name")
            val countryName: String,
        )

        @Serializable
        data class Event(
            @SerialName("allows_cohosting") val allowsCohosting: Boolean,
            @SerialName("checkin_count") val checkinCount: Int,
            @SerialName("cohost_registration_url") val cohostRegistrationIrl: String,
            @SerialName("end_date") val endDate: String,
            @SerialName("id") val id: Int,
            @SerialName("start_date") val startDate: String,
            @SerialName("status") val status: String,
            @SerialName("title") val title: String,
            @SerialName("total_attendees") val totalAttendees: Int,
            @SerialName("url") val url: String,
            @SerialName("cropped_banner_url") val bannerUrl: String?,
        )

        @Serializable
        data class DetailedEvent(
            val id: Int,
            val title: String,
            val chapter: Chapter,
            @SerialName("start_date")
            val startDate: String,
            @SerialName("end_date")
            val endDate: String,
            val url: String,
            val status: String,
            @SerialName("is_hidden")
            val hidden: Boolean,
            @SerialName("is_test")
            val testing: Boolean,
            @SerialName("allows_cohosting")
            val cohosting: Boolean,
            @SerialName("cohost_registration_url")
            val cohostUrl: String?,
            @SerialName("cropped_banner_url") val bannerUrl: String?,
        )

        @Serializable
        data class Attendee(
            @SerialName("id") val id: Int,
            @SerialName("first_name") val firstName: String,
            @SerialName("last_name") val lastName: String,
            @SerialName("email") val email: String,
            @SerialName("user_id") val userOd: String,
            @SerialName("created_date") val createdDate: String,
            @SerialName("is_checked_in") val isCheckedIn: Boolean = false
        )

        @Serializable
        data class ShortAttendee(
            val id: Int,
            @SerialName("is_checked_in")
            val isCheckedIn: Boolean
        )

        @Serializable
        data class CheckedIn(
            @SerialName("event") val eventId: Int,
            val attendees: List<ShortAttendee>
        )

        @Serializable
        data class AuthenticationProvider(
            @SerialName("provider") val provider: String,
            @SerialName("provider_user_id") val providerUserId: Int,
        )

        @Serializable
        data class Role(
            @SerialName("id") val id: Int,
            @SerialName("name") val name: String,
            @SerialName("description") val description: String,
            @SerialName("user_active_status") val userActiveStatus: Boolean,
        )

        @Serializable
        data class DetailedUser(
            @SerialName("authentication_providers") val authenticationProviders: List<AuthenticationProvider>,
            @SerialName("avatar") val avatar: Image,
            @SerialName("company") val company: String,
            @SerialName("cropped_avatar_url") val croppedAvatarUrl: String,
            @SerialName("extra_data") val extraData: JsonObject,
            @SerialName("title") val title: String,
            @SerialName("role") val role: Role,
            @SerialName("email") val email: String,
            @SerialName("first_name") val firstName: String,
            @SerialName("id") val id: Int,
            @SerialName("last_name") val lastName: String,
            @SerialName("username") val username: String,
        )

        @GET("api/chapter/{chapter_id}/")
        suspend fun getChapter(
            @Header("authorization") authorization: String,
            @Path("chapter_id") chapter: Int,
        ): Chapter


        @GET("/api/event/")
        suspend fun listEvents(
            @Header("authorization") authorization: String,
            @Query("chapter") chapter: Int,
            @Query("order_by") order: String,
            @Query("fields") fields: String,
        ): PaginatedEvents

        @GET("/api/attendee/")
        suspend fun listAttendees(
            @Header("authorization") authorization: String,
            @Query("event") event: Int,
        ): PaginatedAttendees

        @PUT("/api/attendee/checkin/")
        suspend fun checkInAttendee(
            @Header("authorization") authorization: String,
            @Body body: CheckedIn,
        ): CheckedIn

        @GET("api/event/{event_id}/")
        suspend fun getEvent(
            @Header("authorization") authorization: String,
            @Path("event_id") event: Int,
        ): DetailedEvent

        @GET("api/user/{user_id}/")
        suspend fun getUser(
            @Header("authorization") authorization: String,
            @Path("user_id") user: Int,
        ): DetailedUser
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val service: Service by lazy {
        val client = OkHttpClient.Builder()
            .addInterceptor(LoggingInterceptor())
            .build()

        Retrofit
            .Builder()
            .client(client)
            .baseUrl("https://gdg.community.dev")
            .addConverterFactory(
                json.asConverterFactory("application/json".toMediaType())
            )
            .build()
            .create<Service>()
    }

    suspend fun listEvents(
        token: String,
        chapter: Int,
        order: String = "-end_date",
        fields: String = "allows_cohosting,checkin_count,cohost_registration_url,end_date,id,start_date,status,title,total_attendees,url,cropped_banner_url"
    ): Service.PaginatedEvents = service.listEvents(
        token.toAuthorization(),
        chapter,
        order,
        fields
    )

    suspend fun listAttendees(
        token: String,
        event: Int
    ): Service.PaginatedAttendees = service.listAttendees(
        token.toAuthorization(),
        event
    )

    suspend fun checkInAttendee(
        token: String,
        eventId: Int,
        attendeeId: Int,
        checkedIn: Boolean,
    ): Service.CheckedIn = service.checkInAttendee(
        token.toAuthorization(),
        Service.CheckedIn(
            eventId,
            listOf(
                Service.ShortAttendee(
                    id = attendeeId,
                    isCheckedIn = checkedIn
                )
            )
        ),
    )

    suspend fun getChapter(
        token: String,
        chapter: Int
    ): Service.Chapter = service.getChapter(
        token.toAuthorization(),
        chapter
    )

    suspend fun getEvent(
        token: String,
        eventId: Int
    ): Service.DetailedEvent = service.getEvent(
        token.toAuthorization(),
        eventId,
    )

    suspend fun getUser(
        token: String,
        userId: Int
    ): Service.DetailedUser = service.getUser(
        token.toAuthorization(),
        userId,
    )
}

private fun String.toAuthorization(): String = "Token $this"
