// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone.contacts

import android.content.ContentResolver
import android.content.ContentUris
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import dev.liquidphone.core.model.Contact
import dev.liquidphone.core.model.ContactEvent
import dev.liquidphone.core.model.ContactSummary
import dev.liquidphone.core.model.Email
import dev.liquidphone.core.model.EventKind
import dev.liquidphone.core.model.LabelKind
import dev.liquidphone.core.model.Name
import dev.liquidphone.core.model.Phone
import dev.liquidphone.core.model.PostalAddress
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Lectura de la agenda del sistema (ContactsContract). Es la parte de lectura de
 * contacts/SystemContacts.kt de Liquid Contacts, sin cambios. Esta app no escribe en la agenda.
 * Todas las funciones hacen E/S: llamarlas fuera del hilo principal.
 */
class SystemContacts(app: android.content.Context) {
    private val resolver: ContentResolver = app.contentResolver

    // ---------------------------------------------------------------- lectura

    /** Todos los contactos, con lo necesario para la lista, la búsqueda y el índice. */
    fun summaries(): List<ContactSummary> {
        val phones = HashMap<Long, MutableList<String>>()
        val emails = HashMap<Long, MutableList<String>>()
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(Data.CONTACT_ID, Data.MIMETYPE, Data.DATA1),
            "${Data.MIMETYPE} IN (?, ?)",
            arrayOf(CommonDataKinds.Phone.CONTENT_ITEM_TYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val value = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                val target = if (c.getString(1) == CommonDataKinds.Phone.CONTENT_ITEM_TYPE) phones else emails
                target.getOrPut(c.getLong(0)) { ArrayList(2) }.add(value)
            }
        }
        val out = ArrayList<ContactSummary>()
        resolver.query(
            Contacts.CONTENT_URI,
            arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.STARRED, Contacts.PHOTO_URI),
            null,
            null,
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                out += ContactSummary(
                    id = id,
                    lookupKey = c.getString(1) ?: continue,
                    displayName = c.getString(2).orEmpty(),
                    starred = c.getInt(3) != 0,
                    phones = phones[id].orEmpty().distinct(),
                    emails = emails[id].orEmpty().distinct(),
                    photoUri = c.getString(4),
                )
            }
        }
        return out
    }

    /** La URI estable de un contacto, para abrirlo en la app de contactos. */
    fun lookupUri(contactId: Long): Uri? =
        Contacts.getLookupUri(resolver, ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId))

    /** El contacto entero tal como se ve (todas sus fichas juntas, sin repetir). */
    fun load(contactId: Long): Contact? {
        val header = resolver.query(
            ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId),
            arrayOf(Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.STARRED),
            null,
            null,
            null,
        )?.use { c -> if (c.moveToFirst()) Triple(c.getString(0), c.getString(1).orEmpty(), c.getInt(2) != 0) else null } ?: return null
        val raw = rawContactIds(contactId)
        val parts = raw.map { readRaw(it) }
        val merged = parts.fold(Contact()) { acc, part -> acc.plusDistinct(part) }
        return merged.copy(
            id = contactId,
            lookupKey = header.first,
            name = if (merged.name.shown.isBlank()) Name(display = header.second) else merged.name,
            starred = header.third,
        )
    }

    /** Fichas (raw contacts) de un contacto, sin las borradas. */
    private fun rawContactIds(contactId: Long): List<Long> =
        resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID),
            "${RawContacts.CONTACT_ID} = ? AND ${RawContacts.DELETED} = 0",
            arrayOf(contactId.toString()),
            RawContacts._ID,
        )?.use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0)) }
        }.orEmpty()

    private fun readRaw(rawId: Long): Contact {
        var name = Name()
        var nickname: String? = null
        var organization: String? = null
        var jobTitle: String? = null
        var note: String? = null
        val phones = ArrayList<Phone>()
        val emails = ArrayList<Email>()
        val addresses = ArrayList<PostalAddress>()
        val events = ArrayList<ContactEvent>()
        val websites = ArrayList<String>()
        val groupIds = ArrayList<Long>()
        resolver.query(
            Data.CONTENT_URI,
            DATA_PROJECTION,
            "${Data.RAW_CONTACT_ID} = ?",
            arrayOf(rawId.toString()),
            "${Data.IS_PRIMARY} DESC, ${Data._ID}",
        )?.use { c ->
            while (c.moveToNext()) {
                when (c.str(Data.MIMETYPE)) {
                    CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> name = Name(
                        display = c.str(CommonDataKinds.StructuredName.DISPLAY_NAME).orEmpty(),
                        given = c.str(CommonDataKinds.StructuredName.GIVEN_NAME),
                        family = c.str(CommonDataKinds.StructuredName.FAMILY_NAME),
                        middle = c.str(CommonDataKinds.StructuredName.MIDDLE_NAME),
                        prefix = c.str(CommonDataKinds.StructuredName.PREFIX),
                        suffix = c.str(CommonDataKinds.StructuredName.SUFFIX),
                        phoneticGiven = c.str(CommonDataKinds.StructuredName.PHONETIC_GIVEN_NAME),
                        phoneticFamily = c.str(CommonDataKinds.StructuredName.PHONETIC_FAMILY_NAME),
                    )
                    CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> c.str(CommonDataKinds.Phone.NUMBER)?.takeIf { it.isNotBlank() }?.let {
                        phones += Phone(it, phoneKind(c.int(CommonDataKinds.Phone.TYPE)), c.str(CommonDataKinds.Phone.LABEL), c.int(Data.IS_PRIMARY) != 0)
                    }
                    CommonDataKinds.Email.CONTENT_ITEM_TYPE -> c.str(CommonDataKinds.Email.ADDRESS)?.takeIf { it.isNotBlank() }?.let {
                        emails += Email(it, emailKind(c.int(CommonDataKinds.Email.TYPE)), c.str(CommonDataKinds.Email.LABEL), c.int(Data.IS_PRIMARY) != 0)
                    }
                    CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> {
                        val street = c.str(CommonDataKinds.StructuredPostal.STREET)
                        val city = c.str(CommonDataKinds.StructuredPostal.CITY)
                        val region = c.str(CommonDataKinds.StructuredPostal.REGION)
                        val postcode = c.str(CommonDataKinds.StructuredPostal.POSTCODE)
                        val country = c.str(CommonDataKinds.StructuredPostal.COUNTRY)
                        // Si otra app guardó solo la dirección armada, va entera en "calle".
                        val structured = listOf(street, city, region, postcode, country).any { !it.isNullOrBlank() }
                        addresses += PostalAddress(
                            street = if (structured) street else c.str(CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS),
                            city = city, region = region, postcode = postcode, country = country,
                            kind = postalKind(c.int(CommonDataKinds.StructuredPostal.TYPE)),
                            label = c.str(CommonDataKinds.StructuredPostal.LABEL),
                        ).takeIf { it.formatted.isNotBlank() } ?: continue
                    }
                    CommonDataKinds.Event.CONTENT_ITEM_TYPE -> parseEvent(
                        c.str(CommonDataKinds.Event.START_DATE),
                        eventKind(c.int(CommonDataKinds.Event.TYPE)),
                        c.str(CommonDataKinds.Event.LABEL),
                    )?.let { events += it }
                    CommonDataKinds.Nickname.CONTENT_ITEM_TYPE -> nickname = nickname ?: c.str(CommonDataKinds.Nickname.NAME)
                    CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                        organization = organization ?: c.str(CommonDataKinds.Organization.COMPANY)
                        jobTitle = jobTitle ?: c.str(CommonDataKinds.Organization.TITLE)
                    }
                    CommonDataKinds.Note.CONTENT_ITEM_TYPE -> note = c.str(CommonDataKinds.Note.NOTE)?.takeIf { it.isNotBlank() } ?: note
                    CommonDataKinds.Website.CONTENT_ITEM_TYPE -> c.str(CommonDataKinds.Website.URL)?.takeIf { it.isNotBlank() }?.let { websites += it }
                    CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE -> groupIds += c.long(CommonDataKinds.GroupMembership.GROUP_ROW_ID)
                }
            }
        }
        return Contact(
            name = name,
            nickname = nickname,
            organization = organization,
            jobTitle = jobTitle,
            phones = phones,
            emails = emails,
            addresses = addresses,
            events = events,
            websites = websites,
            note = note,
            groups = groupTitles(groupIds),
        )
    }

    private fun groupTitles(ids: List<Long>): Set<String> {
        if (ids.isEmpty()) return emptySet()
        return resolver.query(
            Groups.CONTENT_URI,
            arrayOf(Groups.TITLE),
            "${Groups._ID} IN (${ids.joinToString(",")}) AND $EDITABLE_GROUP",
            null,
            null,
        )?.use { c -> buildSet { while (c.moveToNext()) c.getString(0)?.takeIf { it.isNotBlank() }?.let(::add) } }.orEmpty()
    }

    // ---------------------------------------------------------------- cambios

    /** Emite cada vez que cambia algo en la agenda (de esta app o de cualquier otra). */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(Contacts.CONTENT_URI, true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate()

    companion object {
        /** Etiquetas que maneja el usuario: fuera las del sistema ("Mis contactos") y las de solo lectura. */
        private val EDITABLE_GROUP =
            "${Groups.DELETED} = 0 AND ${Groups.GROUP_IS_READ_ONLY} = 0 AND ${Groups.AUTO_ADD} = 0 AND ${Groups.SYSTEM_ID} IS NULL"

        private val DATA_PROJECTION = arrayOf(
            Data._ID, Data.MIMETYPE, Data.IS_PRIMARY,
            Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4, Data.DATA5, Data.DATA6, Data.DATA7, Data.DATA8, Data.DATA9, Data.DATA10,
        )

        // Los tipos de dato comparten columnas DATA1..DATA10: cada constante es uno de esos nombres.
        private fun Cursor.str(col: String): String? = getColumnIndex(col).takeIf { it >= 0 }?.let { if (isNull(it)) null else getString(it) }
        private fun Cursor.int(col: String): Int = getColumnIndex(col).takeIf { it >= 0 }?.let { if (isNull(it)) 0 else getInt(it) } ?: 0
        private fun Cursor.long(col: String): Long = getColumnIndex(col).takeIf { it >= 0 }?.let { getLong(it) } ?: 0L

        fun phoneKind(t: Int): LabelKind = when (t) {
            CommonDataKinds.Phone.TYPE_MOBILE -> LabelKind.MOBILE
            CommonDataKinds.Phone.TYPE_HOME -> LabelKind.HOME
            CommonDataKinds.Phone.TYPE_WORK, CommonDataKinds.Phone.TYPE_WORK_MOBILE -> LabelKind.WORK
            CommonDataKinds.Phone.TYPE_MAIN -> LabelKind.MAIN
            CommonDataKinds.Phone.TYPE_FAX_WORK -> LabelKind.FAX_WORK
            CommonDataKinds.Phone.TYPE_FAX_HOME -> LabelKind.FAX_HOME
            CommonDataKinds.Phone.TYPE_PAGER -> LabelKind.PAGER
            CommonDataKinds.Phone.TYPE_CUSTOM -> LabelKind.CUSTOM
            else -> LabelKind.OTHER
        }

        fun emailKind(t: Int): LabelKind = when (t) {
            CommonDataKinds.Email.TYPE_HOME -> LabelKind.HOME
            CommonDataKinds.Email.TYPE_WORK -> LabelKind.WORK
            CommonDataKinds.Email.TYPE_MOBILE -> LabelKind.MOBILE
            CommonDataKinds.Email.TYPE_CUSTOM -> LabelKind.CUSTOM
            else -> LabelKind.OTHER
        }

        fun postalKind(t: Int): LabelKind = when (t) {
            CommonDataKinds.StructuredPostal.TYPE_HOME -> LabelKind.HOME
            CommonDataKinds.StructuredPostal.TYPE_WORK -> LabelKind.WORK
            CommonDataKinds.StructuredPostal.TYPE_CUSTOM -> LabelKind.CUSTOM
            else -> LabelKind.OTHER
        }

        fun eventKind(t: Int): EventKind = when (t) {
            CommonDataKinds.Event.TYPE_BIRTHDAY -> EventKind.BIRTHDAY
            CommonDataKinds.Event.TYPE_ANNIVERSARY -> EventKind.ANNIVERSARY
            CommonDataKinds.Event.TYPE_CUSTOM -> EventKind.CUSTOM
            else -> EventKind.OTHER
        }

        /** Lee las fechas que guardan las apps de contactos: "1990-03-14", "--03-14", "19900314" o con hora. */
        fun parseEvent(text: String?, kind: EventKind, label: String?): ContactEvent? {
            val t = text?.trim()?.substringBefore('T') ?: return null
            val m = Regex("^(\\d{4}|-)-?(\\d{2})-?(\\d{2})$").find(t.replace("--", "-")) ?: return null
            val year = m.groupValues[1].toIntOrNull()
            return runCatching { ContactEvent(m.groupValues[2].toInt(), m.groupValues[3].toInt(), year, kind, label) }.getOrNull()
        }
    }
}

/** Une dos lecturas del mismo contacto sin repetir datos (para mostrar un contacto de varias fichas). */
private fun Contact.plusDistinct(o: Contact): Contact = copy(
    name = if (name.shown.isBlank()) o.name else name,
    nickname = nickname ?: o.nickname,
    organization = organization ?: o.organization,
    jobTitle = jobTitle ?: o.jobTitle,
    phones = (phones + o.phones).distinctBy { it.number.filter { ch -> ch.isDigit() || ch == '+' } },
    emails = (emails + o.emails).distinctBy { it.address.trim().lowercase() },
    addresses = (addresses + o.addresses).distinctBy { it.formatted.lowercase() },
    events = (events + o.events).distinct(),
    websites = (websites + o.websites).distinct(),
    note = listOfNotNull(note, o.note).distinct().joinToString("\n\n").ifEmpty { null },
    groups = groups + o.groups,
)
