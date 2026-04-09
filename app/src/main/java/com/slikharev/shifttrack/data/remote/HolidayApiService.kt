package com.slikharev.shifttrack.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class HolidayApiResult(
    val date: String,
    val name: String,
    val countryCode: String,
)

class HolidayApiService {

    suspend fun fetchHolidays(year: Int, countryCode: String): Result<List<HolidayApiResult>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = URL("https://date.nager.at/api/v3/PublicHolidays/$year/$countryCode")
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 10_000

                    val responseCode = connection.responseCode
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        error("API returned HTTP $responseCode")
                    }

                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(body)
                    val results = mutableListOf<HolidayApiResult>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        results += HolidayApiResult(
                            date = obj.getString("date"),
                            name = obj.getString("localName"),
                            countryCode = obj.getString("countryCode"),
                        )
                    }
                    results
                } finally {
                    connection.disconnect()
                }
            }
        }
}
