package com.example.herdmanagementmobile.data.remote.irzplus

import com.example.herdmanagementmobile.data.settings.FarmSettingsRepository
import com.example.herdmanagementmobile.domain.model.Animal
import com.example.herdmanagementmobile.domain.model.AnimalEvent
import com.example.herdmanagementmobile.domain.model.EventType
import com.example.herdmanagementmobile.domain.model.Sex
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Klient IRZplus. Zarządza uwierzytelnieniem, mapuje zdarzenie domenowe na dyspozycję
 * zgłoszenia i kieruje je do odpowiedniej operacji (urodzenie, przybycie, ubycie,
 * padnięcie). Token jest buforowany i odświeżany po odpowiedzi 401.
 *
 * Hasło jest stałą demonstracyjną; w środowisku produkcyjnym powinno pochodzić
 * z bezpiecznego magazynu (np. EncryptedSharedPreferences).
 */
@Singleton
class IrzplusClient @Inject constructor(
    private val api: IrzplusApiService,
    private val farmSettings: FarmSettingsRepository,
) {
    @Volatile
    private var token: String? = null

    private suspend fun ensureToken(): String {
        token?.let { return it }
        // Loginem jest numer producenta, zgodnie z konwencją IRZplus.
        val producent = farmSettings.current().producerNumber
        val fresh = api.logowanie(LoginRequest(login = producent, haslo = PASSWORD)).token
        token = fresh
        return fresh
    }

    suspend fun pobierzZwierzeta(): List<ZwierzeIndywidualneDTO> =
        withBearer { auth -> api.pobierzZwierzeta(auth) }

    /** Zgłasza zdarzenie do IRZplus i zwraca odpowiedź z numerem dokumentu lub błędami walidacji. */
    suspend fun zglosZdarzenie(event: AnimalEvent, animal: Animal): ZlozenieDyspozycjiResponse {
        val settings = farmSettings.current()
        val dyspozycja = buildDyspozycja(event, animal, settings.producerNumber, settings.holdingNumber)
        return withBearer { auth ->
            when (event.type) {
                EventType.BIRTH -> api.zglosUrodzenie(auth, dyspozycja)
                EventType.ARRIVAL -> api.zglosPrzybycie(auth, dyspozycja)
                EventType.DEPARTURE -> api.zglosUbycie(auth, dyspozycja)
                EventType.DEATH -> api.zglosPadniecie(auth, dyspozycja)
            }
        }
    }

    /** Wykonuje wywołanie z tokenem; po odpowiedzi 401 odświeża token i jednokrotnie ponawia. */
    private suspend fun <T> withBearer(block: suspend (String) -> T): T {
        return try {
            block("Bearer ${ensureToken()}")
        } catch (e: HttpException) {
            if (e.code() == 401) {
                token = null
                block("Bearer ${ensureToken()}")
            } else {
                throw e
            }
        }
    }

    private fun buildDyspozycja(
        event: AnimalEvent,
        animal: Animal,
        numerProducenta: String,
        domyslnaSiedziba: String,
    ): DyspozycjaZgloszenia {
        val pozycja = PozycjaZgloszeniaDTO(
            numerIdentyfikacyjnyZwierzecia = animal.earTag,
            numerSiedzibyStada = animal.herdId?.takeIf { it.isNotBlank() } ?: domyslnaSiedziba,
            dataZdarzenia = event.eventDate.toString(),
            numerIdentyfikacyjnyMatki = animal.motherEarTag.takeIf { event.type == EventType.BIRTH },
            plec = animal.sex.toIrz().takeIf { event.type == EventType.BIRTH },
            rasa = animal.breed.takeIf { event.type == EventType.BIRTH },
            dataUrodzenia = animal.birthDate?.toString().takeIf { event.type == EventType.BIRTH },
            numerSiedzibyStadaPartnera = event.partnerHoldingNumber
                ?.takeIf { event.type == EventType.ARRIVAL || event.type == EventType.DEPARTURE },
        )
        return DyspozycjaZgloszenia(
            numerProducenta = numerProducenta,
            zgloszenie = ZgloszenieDTO(pozycje = listOf(pozycja)),
        )
    }

    private fun Sex.toIrz(): String = when (this) {
        Sex.FEMALE -> "samica"
        Sex.MALE -> "samiec"
        Sex.OTHER -> "nieokreslona"
    }

    companion object {
        // Symulator akceptuje dowolne niepuste hasło.
        private const val PASSWORD = "demo"
    }
}
