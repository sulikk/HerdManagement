package com.example.herdmanagementmobile.data.remote.irzplus

import kotlinx.serialization.Serializable

/**
 * DTO klienta API IRZplus (ARiMR). Nazwy pól zgodne z realną konwencją Portalu
 * (po polsku, camelCase), zgłoszenie jako "dyspozycja" z pozycjami, odpowiedź
 * z numerem dokumentu i listą błędów walidacji. Daty jako ISO-8601 String.
 */
@Serializable
data class LoginRequest(
    val login: String,
    val haslo: String,
)

@Serializable
data class LoginResponse(
    val token: String,
    val typTokenu: String = "Bearer",
    val wygasaZaSekundy: Int = 0,
)

@Serializable
data class PozycjaZgloszeniaDTO(
    val numerIdentyfikacyjnyZwierzecia: String,
    val numerSiedzibyStada: String,
    val dataZdarzenia: String,
    val numerIdentyfikacyjnyMatki: String? = null,
    val plec: String? = null,
    val rasa: String? = null,
    val dataUrodzenia: String? = null,
    val numerSiedzibyStadaPartnera: String? = null,
    val przyczynaPadniecia: String? = null,
)

@Serializable
data class ZgloszenieDTO(
    val pozycje: List<PozycjaZgloszeniaDTO>,
)

@Serializable
data class DyspozycjaZgloszenia(
    val numerProducenta: String,
    val komorkaOrganizacyjna: String? = null,
    val zgloszenie: ZgloszenieDTO,
)

@Serializable
data class BladWalidacjiDTO(
    val kod: String,
    val opis: String,
    val numerZwierzecia: String? = null,
)

@Serializable
data class ZlozenieDyspozycjiResponse(
    val numerDokumentu: String? = null,
    val komunikat: String = "",
    val bledy: List<BladWalidacjiDTO> = emptyList(),
)

@Serializable
data class ZwierzeIndywidualneDTO(
    val numerIdentyfikacyjnyZwierzecia: String,
    val numerSiedzibyStada: String? = null,
    val plec: String? = null,
    val rasa: String? = null,
    val dataUrodzenia: String? = null,
    val numerIdentyfikacyjnyMatki: String? = null,
)
