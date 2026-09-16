package com.example.herdmanagementmobile.data.sync

import kotlinx.serialization.SerializationException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class SyncErrorsTest {

    private fun http(code: Int) = HttpException(Response.error<Unit>(code, "{}".toResponseBody()))

    @Test
    fun `brak sieci jest ponawiany bez limitu`() {
        listOf(IOException("x"), SocketTimeoutException(), UnknownHostException()).forEach {
            assertEquals(SyncErrorKind.NETWORK, classifySyncError(it))
        }
    }

    @Test
    fun `chwilowe błędy serwera`() {
        listOf(500, 502, 503, 408, 429).forEach {
            assertEquals("HTTP $it", SyncErrorKind.SERVER, classifySyncError(http(it)))
        }
    }

    @Test
    fun `błędy danych nie są ponawiane`() {
        listOf(400, 404, 409, 422).forEach {
            assertEquals("HTTP $it", SyncErrorKind.PERMANENT, classifySyncError(http(it)))
        }
        assertEquals(SyncErrorKind.PERMANENT, classifySyncError(SerializationException("x")))
        assertEquals(SyncErrorKind.PERMANENT, classifySyncError(IllegalStateException("x")))
    }

    @Test
    fun `opis błędu HTTP zawiera kod i treść odpowiedzi`() {
        val error = HttpException(Response.error<Unit>(422, """{"detail":"zły kolczyk"}""".toResponseBody()))

        assertEquals("""HTTP 422: {"detail":"zły kolczyk"}""", describeSyncError(error))
    }

    @Test
    fun `backoff rośnie wykładniczo do 5 minut`() {
        assertEquals(30L, SyncContract.retryDelaySeconds(1))
        assertEquals(60L, SyncContract.retryDelaySeconds(2))
        assertEquals(120L, SyncContract.retryDelaySeconds(3))
        assertEquals(240L, SyncContract.retryDelaySeconds(4))
        assertEquals(300L, SyncContract.retryDelaySeconds(5))
        assertEquals(300L, SyncContract.retryDelaySeconds(100))
        assertEquals(30L, SyncContract.retryDelaySeconds(0))
    }
}
