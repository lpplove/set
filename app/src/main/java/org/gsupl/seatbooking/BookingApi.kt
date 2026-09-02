package org.gsupl.seatbooking

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 座位预约 API 封装（对应原 Python 脚本的 login / yd 函数）
 */
object BookingApi {

    private const val BASE = "https://seat.gsupl.edu.cn"
    private const val LOGIN_URL = "$BASE/login"
    private const val BOOK_URL = "$BASE/readingroom/postbeskdata"

    private val MEDIA_FORM = "application/x-www-form-urlencoded".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .cookieJar(SimpleCookieJar())
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val commonHeaders = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36")
        .add("Host", "seat.gsupl.edu.cn")
        .add("Referer", "https://seat.gsupl.edu.cn/readingroommanage")
        .add("Origin", "https://seat.gsupl.edu.cn")
        .add("Accept", "application/json, text/javascript, */*; q=0.01")
        .add("X-Requested-With", "XMLHttpRequest")
        .build()

    /** 登录成功返回 true */
    fun login(username: String, passwordB64: String): Result<String> {
        return try {
            // 先访问首页拿cookie
            client.newCall(
                Request.Builder()
                    .url(BASE)
                    .get()
                    .header("User-Agent", commonHeaders["User-Agent"] ?: "")
                    .build()
            ).execute().use { it.body?.close() }

            val form = "url=readingroommanage" +
                    "&user=${username.urlEncode()}" +
                    "&passwd=${passwordB64.urlEncode()}"
            val req = Request.Builder()
                .url(LOGIN_URL)
                .headers(commonHeaders)
                .post(form.toRequestBody(MEDIA_FORM))
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.code == 200) {
                    // 登录接口通常 200 就是ok
                    Result.success("登录请求已发送 HTTP 200")
                } else {
                    Result.failure(Exception("登录失败 HTTP ${resp.code}: ${body.take(120)}"))
                }
            }
        } catch (t: Throwable) {
            Result.failure(Exception("登录异常: ${t.message}"))
        }
    }

    data class BookResp(val returnValue: Int, val msg: String)

    fun book(isAfternoon: Boolean, roomNo: String, tableNo: String): Result<BookResp> {
        return try {
            val (begin, end, beskid, beskCanId) = if (isAfternoon) {
                listOf("13:50:00", "22:30:00", "103", "69")
            } else {
                listOf("07:00:00", "13:50:00", "123", "73")
            }
            val form = "roomno=${roomNo.urlEncode()}" +
                    "&tableid=0" +
                    "&tableno=${tableNo.urlEncode()}" +
                    "&begintime=${begin.urlEncode()}" +
                    "&endtime=${end.urlEncode()}" +
                    "&beskid=${beskid.urlEncode()}" +
                    "&beskCanId=${beskCanId.urlEncode()}"

            val req = Request.Builder()
                .url(BOOK_URL)
                .headers(commonHeaders)
                .post(form.toRequestBody(MEDIA_FORM))
                .build()

            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.code == 200) {
                    val json = try { JSONObject(body) } catch (_: Exception) { null }
                    val code = json?.optInt("ReturnValue", -99) ?: -99
                    val msg = json?.optString("Msg", body.take(80)) ?: body.take(80)
                    Result.success(BookResp(code, msg))
                } else {
                    Result.failure(Exception("预约 HTTP ${resp.code}: ${body.take(100)}"))
                }
            }
        } catch (t: Throwable) {
            Result.failure(Exception("预约请求异常: ${t.message}"))
        }
    }

    private fun String.urlEncode(): String = java.net.URLEncoder.encode(this, "UTF-8")
}

/**
 * 简易 CookieJar（和 requests.session() 等价）
 */
class SimpleCookieJar : CookieJar {
    private val store = mutableListOf<Cookie>()
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(store) {
            val now = System.currentTimeMillis()
            store.removeAll { it.expiresAt <= now || it.matches(url) }
            store.addAll(cookies.filter { it.expiresAt > now })
        }
    }
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(store) {
            val now = System.currentTimeMillis()
            store.removeAll { it.expiresAt <= now }
            return store.filter { it.matches(url) }
        }
    }
}
