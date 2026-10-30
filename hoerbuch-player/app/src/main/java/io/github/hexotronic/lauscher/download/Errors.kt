package io.github.hexotronic.lauscher.download

import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Turns errors of the platforms and the network into sentences for the listener. */
object DownloadErrors {
    fun describe(e: Throwable): String {
        val text = e.message.orEmpty()
        return when {
            e is SignInConfirmNotBotException || e is ReCaptchaException || "not a bot" in text || "LOGIN_REQUIRED" in text ->
                "YouTube will gerade eine Anmeldung („Bist du ein Bot?“). Später noch einmal versuchen oder das Netz " +
                    "wechseln (WLAN ↔ mobile Daten)."
            e is AgeRestrictedContentException -> "Das Video ist altersbeschränkt und ohne Anmeldung nicht abrufbar."
            e is GeographicRestrictionException -> "Das Video ist in diesem Land gesperrt."
            e is PaidContentException -> "Das ist ein kostenpflichtiger Inhalt."
            e is PrivateContentException -> "Das Video ist privat."
            e is ContentNotAvailableException -> "Nicht verfügbar (gelöscht, privat oder gesperrt)."
            e is UnknownHostException -> "Keine Internetverbindung."
            e is SocketTimeoutException -> "Zeitüberschreitung – die Verbindung ist zu langsam."
            text.isNotBlank() -> text
            else -> e.javaClass.simpleName
        }
    }
}
