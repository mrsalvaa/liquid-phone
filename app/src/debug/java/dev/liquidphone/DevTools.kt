// SPDX-License-Identifier: GPL-3.0-or-later
package dev.liquidphone

import android.content.ContentProviderOperation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * Solo debug y profile: contactos de prueba y registro de estados. La release no lo tiene.
 *
 * Los contactos se crean y se borran desde adb (ver [DevReceiver]). Solo se borra lo que creó esta
 * herramienta, fila por fila, y se niega a crear si la agenda tiene contactos de otro origen.
 *
 * En el registro van cantidades y decisiones, nunca nombres ni números.
 */
object DevTools {
    private const val TAG = "LiquidPhoneDev"
    private const val SOURCE = "liquidphone-debug-"

    /** Estados de una llamada, sin números: pueden ser reales. */
    fun logCall(what: String) {
        Log.i(TAG, "llamada: $what")
    }

    // ---------------------------------------------------------------- contactos de prueba

    /** Un teléfono de prueba: número de un rango reservado y su tipo. */
    private data class TestPhone(val index: Int, val type: Int = CommonDataKinds.Phone.TYPE_MOBILE)

    /** Cada uno prueba algo distinto de la lista, del teclado o del detalle. */
    private data class TestContact(
        val name: String,
        val phones: List<TestPhone>,
        val starred: Boolean = false,
        val email: String? = null,
        val company: String? = null,
        val job: String? = null,
        val nickname: String? = null,
        val address: String? = null,
        val birthday: String? = null,
        val website: String? = null,
        val note: String? = null,
        val photo: Int? = null,
    )

    private val mobile = CommonDataKinds.Phone.TYPE_MOBILE
    private val work = CommonDataKinds.Phone.TYPE_WORK
    private val home = CommonDataKinds.Phone.TYPE_HOME

    // Con "(prueba)" el nombre nunca coincide con el de un contacto real ni se confunde con uno.
    // Los dominios .example y las direcciones no existen.
    private val testContacts = listOf(
        TestContact("Ángela Núñez (prueba)", listOf(TestPhone(0)), starred = true, photo = 0xFF3F7CAC.toInt()),
        TestContact("Beatriz Peña (prueba)", listOf(TestPhone(2), TestPhone(6, work)), starred = true, email = "beatriz@correo.example", company = "Estudio Ficticio", job = "Arquitecta"),
        TestContact("Carlos Muñoz (prueba)", listOf(TestPhone(4)), nickname = "Charly", birthday = "1990-03-14"),
        TestContact("Damián Ibáñez (prueba)", listOf(TestPhone(8), TestPhone(10, home), TestPhone(12, work)), email = "damian@correo.example", address = "Calle Inventada 123, Ciudad Ficticia", website = "https://damian.example"),
        TestContact("Elena Müller (prueba)", listOf(TestPhone(14)), starred = true, photo = 0xFF9C4F96.toInt(), company = "Taller Imaginario"),
        TestContact("Fernando O'Brien (prueba)", listOf(TestPhone(16))),
        TestContact("Gabriela Giménez (prueba)", listOf(TestPhone(18, home)), note = "Nota de prueba, con varias líneas.\nSegunda línea."),
        TestContact("Hernán Sánchez (prueba)", listOf(TestPhone(20)), starred = true),
        TestContact("Inés Núñez (prueba)", listOf(TestPhone(22)), photo = 0xFF4C8C4A.toInt()),
        TestContact("José Peña (prueba)", listOf(TestPhone(24), TestPhone(26, work)), company = "Empresa de Prueba", job = "Técnico"),
        TestContact("Julián Muñoz (prueba)", listOf(TestPhone(28))),
        TestContact("Lucía Ibáñez (prueba)", listOf(TestPhone(30)), starred = true, email = "lucia@correo.example"),
        TestContact("María de los Ángeles Fernández de la Santísima Trinidad (prueba)", listOf(TestPhone(32)), company = "Nombre muy largo para ver cómo se corta en la lista"),
        TestContact("Martín Müller (prueba)", listOf(TestPhone(34))),
        TestContact("Noelia O'Brien (prueba)", listOf(TestPhone(36)), starred = true, photo = 0xFFB5651D.toInt()),
        TestContact("Ñusta Giménez (prueba)", listOf(TestPhone(38))),
        TestContact("Óscar Sánchez (prueba)", listOf(TestPhone(40), TestPhone(42, home))),
        TestContact("Paula Núñez (prueba)", listOf(TestPhone(44))),
        TestContact("Quique Peña (prueba)", listOf(TestPhone(46)), nickname = "Kike"),
        TestContact("Rocío Muñoz (prueba)", listOf(TestPhone(48))),
        TestContact("Sin Teléfono (prueba)", emptyList(), email = "sintelefono@correo.example"),
        TestContact("Tomás Ibáñez (prueba)", listOf(TestPhone(50))),
        // Dos contactos con el mismo número: con varios candidatos no se elige ninguno.
        TestContact("Número Compartido Uno (prueba)", listOf(TestPhone(52))),
        TestContact("Número Compartido Dos (prueba)", listOf(TestPhone(52))),
    )

    /**
     * Cuántas fichas de la agenda no son de esta herramienta, sin leerlas: solo se cuenta. -1: sin permiso.
     */
    private fun foreignCount(context: Context): Int = try {
        context.contentResolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID),
            "${RawContacts.DELETED} = 0 AND (${RawContacts.SOURCE_ID} IS NULL OR ${RawContacts.SOURCE_ID} NOT LIKE ?)",
            arrayOf("$SOURCE%"),
            null,
        )?.use { it.count } ?: 0
    } catch (e: SecurityException) {
        -1
    }

    private fun ownCount(context: Context): Int = try {
        context.contentResolver.query(RawContacts.CONTENT_URI, arrayOf(RawContacts._ID), "${RawContacts.SOURCE_ID} LIKE ?", arrayOf("$SOURCE%"), null)?.use { it.count } ?: 0
    } catch (e: SecurityException) {
        0
    }

    private fun localAccount(context: Context): Pair<String?, String?> {
        val type = RawContacts.getLocalAccountType(context)
        if (type != null) return type to RawContacts.getLocalAccountName(context)
        return if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) "vnd.sec.contact.phone" to "vnd.sec.contact.phone" else null to null
    }

    internal fun photo(color: Int, name: String): ByteArray {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            textSize = 140f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(name.take(1), 128f, 128f - (paint.ascent() + paint.descent()) / 2, paint)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        bitmap.recycle()
        return out.toByteArray()
    }

    /** Crea los contactos de prueba. Devuelve el resultado para el registro. */
    @Synchronized
    fun generate(context: Context): String {
        val foreign = foreignCount(context)
        if (foreign == -1) return "rechazado: falta el permiso de contactos"
        if (foreign > 0) return "rechazado: la agenda tiene $foreign fichas de otro origen"
        val own = ownCount(context)
        if (own > 0) return "rechazado: ya hay $own contactos de prueba"

        val (type, name) = localAccount(context)
        testContacts.forEachIndexed { i, contact ->
            fun data(mime: String) = ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, mime)
            val ops = arrayListOf(
                ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                    .withValue(RawContacts.ACCOUNT_TYPE, type)
                    .withValue(RawContacts.ACCOUNT_NAME, name)
                    .withValue(RawContacts.SOURCE_ID, "$SOURCE$i")
                    // DISABLED dejaría la ficha fuera de la tabla de contactos: no se vería.
                    .withValue(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_SUSPENDED)
                    .withValue(RawContacts.STARRED, if (contact.starred) 1 else 0)
                    .build(),
                data(CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE).withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, contact.name).build(),
            )
            for (phone in contact.phones) {
                val number = FakeNumbers.number(phone.index)
                check(FakeNumbers.isReserved(number)) { "número fuera de los rangos reservados" }
                ops += data(CommonDataKinds.Phone.CONTENT_ITEM_TYPE).withValue(CommonDataKinds.Phone.NUMBER, number).withValue(CommonDataKinds.Phone.TYPE, phone.type).build()
            }
            contact.email?.let { ops += data(CommonDataKinds.Email.CONTENT_ITEM_TYPE).withValue(CommonDataKinds.Email.ADDRESS, it).withValue(CommonDataKinds.Email.TYPE, CommonDataKinds.Email.TYPE_HOME).build() }
            if (contact.company != null || contact.job != null) {
                ops += data(CommonDataKinds.Organization.CONTENT_ITEM_TYPE).withValue(CommonDataKinds.Organization.COMPANY, contact.company).withValue(CommonDataKinds.Organization.TITLE, contact.job).build()
            }
            contact.nickname?.let { ops += data(CommonDataKinds.Nickname.CONTENT_ITEM_TYPE).withValue(CommonDataKinds.Nickname.NAME, it).build() }
            contact.address?.let { ops += data(CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE).withValue(CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, it).withValue(CommonDataKinds.StructuredPostal.TYPE, CommonDataKinds.StructuredPostal.TYPE_HOME).build() }
            contact.birthday?.let { ops += data(CommonDataKinds.Event.CONTENT_ITEM_TYPE).withValue(CommonDataKinds.Event.START_DATE, it).withValue(CommonDataKinds.Event.TYPE, CommonDataKinds.Event.TYPE_BIRTHDAY).build() }
            contact.website?.let { ops += data(CommonDataKinds.Website.CONTENT_ITEM_TYPE).withValue(CommonDataKinds.Website.URL, it).withValue(CommonDataKinds.Website.TYPE, CommonDataKinds.Website.TYPE_HOMEPAGE).build() }
            contact.note?.let { ops += data(CommonDataKinds.Note.CONTENT_ITEM_TYPE).withValue(CommonDataKinds.Note.NOTE, it).build() }
            contact.photo?.let { ops += data(CommonDataKinds.Photo.CONTENT_ITEM_TYPE).withValue(CommonDataKinds.Photo.PHOTO, photo(it, contact.name)).build() }
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        }
        return "creados: ${testContacts.size} contactos"
    }

    /** Borra lo que creó la herramienta, fila por fila, y nada más. */
    @Synchronized
    fun clear(context: Context): String {
        val resolver = context.contentResolver
        val raw = resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID, RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME),
            "${RawContacts.SOURCE_ID} LIKE ?",
            arrayOf("$SOURCE%"),
            null,
        )?.use { c -> buildList { while (c.moveToNext()) add(Triple(c.getLong(0), c.getString(1), c.getString(2))) } }.orEmpty()
        var contacts = 0
        for ((id, type, name) in raw) {
            // Como adaptador de sincronización: la ficha se borra de verdad, sin quedar marcada.
            val builder = RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
            if (type != null && name != null) {
                builder.appendQueryParameter(RawContacts.ACCOUNT_TYPE, type).appendQueryParameter(RawContacts.ACCOUNT_NAME, name)
            }
            contacts += resolver.delete(builder.build(), "${RawContacts._ID} = ? AND ${RawContacts.SOURCE_ID} LIKE ?", arrayOf(id.toString(), "$SOURCE%"))
        }
        return "borrados: $contacts contactos"
    }

    /** Estado para adb. Solo cantidades y estados, nada que identifique a nadie. */
    fun status(context: Context): String =
        "contactosDePrueba=${ownCount(context)} fichasDeOtroOrigen=${foreignCount(context)}"
}
