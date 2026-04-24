package com.carecompanion

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.carecompanion.data.database.dao.FacilityDao
import com.carecompanion.data.database.entities.Facility
import com.carecompanion.data.network.WincoApiService
import com.carecompanion.data.network.models.WincoTokenRequest
import com.carecompanion.utils.SharedPreferencesHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class SetupActivity : AppCompatActivity() {

    private lateinit var etEmrUrl: EditText
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnComplete: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView

    @Inject lateinit var facilityDao: FacilityDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!SharedPreferencesHelper.isFirstLaunch(this)) {
            startMain()
            return
        }

        setContentView(R.layout.activity_setup)

        etEmrUrl = findViewById(R.id.et_emr_url)
        etUsername = findViewById(R.id.et_username)
        etPassword = findViewById(R.id.et_password)
        btnComplete = findViewById(R.id.btn_complete_setup)

        val root = btnComplete.parent as LinearLayout
        progressBar = ProgressBar(this).also { pb ->
            pb.visibility = View.GONE
            pb.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.gravity = Gravity.CENTER_HORIZONTAL }
            root.addView(pb)
        }
        tvStatus = TextView(this).also { tv ->
            tv.visibility = View.GONE
            tv.setPadding(0, 16, 0, 0)
            root.addView(tv)
        }

        btnComplete.setOnClickListener { attemptConnect() }
    }

    private fun attemptConnect() {
        val rawUrl = etEmrUrl.text.toString().trim()
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (rawUrl.isEmpty()) { etEmrUrl.error = "Server URL is required"; return }
        if (username.isEmpty()) { etUsername.error = "Username is required"; return }
        if (password.isEmpty()) { etPassword.error = "Password is required"; return }

        val normalized = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) rawUrl else "http://$rawUrl"
        val baseUrl = normalized.trimEnd('/') + "/"
        setLoading(true, "Authenticating…")

        lifecycleScope.launch {
            try {
                val client = buildPlainClient()
                val wincoService = buildWincoService(baseUrl, client)

                // Step 1: authenticate against WINCO → obtain Bearer token
                val tokenResp = withContext(Dispatchers.IO) {
                    wincoService.getToken(WincoTokenRequest(username, password))
                }
                val token = tokenResp.accessToken.ifBlank {
                    throw Exception(tokenResp.error ?: "Server did not return a token. Check your credentials.")
                }

                SharedPreferencesHelper.setWincoBaseUrl(baseUrl)
                SharedPreferencesHelper.setWincoApiKey(token)
                SharedPreferencesHelper.setEmrUsername(username)
                SharedPreferencesHelper.setEmrPassword(password)

                // Step 2: fetch facilities from WINCO
                setLoading(true, "Loading facilities…")
                val authedWinco = buildWincoService(baseUrl, buildAuthedClient(token))
                val wincoFacilities = withContext(Dispatchers.IO) {
                    try { authedWinco.getFacilities() } catch (_: Exception) { emptyList() }
                }

                if (wincoFacilities.isEmpty()) {
                    setLoading(false, "")
                    showManualFacilityEntry()
                } else {
                    val dbFacilities = wincoFacilities.map {
                        Facility(id = it.id, name = it.name, facilityCode = null, state = it.state, lga = it.lga)
                    }
                    withContext(Dispatchers.IO) { facilityDao.insertAll(dbFacilities) }
                    setLoading(false, "")
                    if (wincoFacilities.size == 1) {
                        selectFacility(wincoFacilities[0].id, wincoFacilities[0].name)
                    } else {
                        showWincoFacilityPicker(wincoFacilities)
                    }
                }
            } catch (e: Exception) {
                setLoading(false, "")
                showError("Connection failed: ${e.message ?: "Check URL and credentials"}")
            }
        }
    }

    private fun selectFacility(facilityId: Long, facilityName: String) {
        SharedPreferencesHelper.setActiveFacilityId(facilityId)
        SharedPreferencesHelper.setActiveFacilityName(facilityName)
        lifecycleScope.launch(Dispatchers.IO) {
            facilityDao.clearActive()
            facilityDao.setActive(facilityId)
        }
        SharedPreferencesHelper.setFirstLaunchComplete(this)
        Toast.makeText(this, "Connected to $facilityName", Toast.LENGTH_SHORT).show()
        startMain()
    }

    private fun showWincoFacilityPicker(facilities: List<com.carecompanion.data.network.models.WincoFacility>) {
        val names = facilities.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select Your Facility")
            .setItems(names) { _, which ->
                val f = facilities[which]
                selectFacility(f.id, f.name)
            }
            .setCancelable(false)
            .show()
    }

    private fun showManualFacilityEntry() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Facility ID"
            setPadding(48, 16, 48, 16)
        }
        AlertDialog.Builder(this)
            .setTitle("Enter Facility ID")
            .setMessage("Could not load facilities automatically. Enter your facility ID manually.")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val id = input.text.toString().toLongOrNull()
                if (id != null && id > 0) selectFacility(id, "Facility $id")
                else Toast.makeText(this, "Invalid facility ID", Toast.LENGTH_SHORT).show()
            }
            .setCancelable(false)
            .show()
    }

    private fun buildPlainClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    private fun buildAuthedClient(token: String): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("X-Client-Type", "mobile")
                        .build()
                )
            }.build()

    private fun buildWincoService(baseUrl: String, client: OkHttpClient): WincoApiService =
        Retrofit.Builder().baseUrl(baseUrl).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(WincoApiService::class.java)

    private fun showError(message: String) {
        tvStatus.visibility = View.VISIBLE
        tvStatus.text = message
        tvStatus.setTextColor(getColor(android.R.color.holo_red_dark))
    }

    private fun setLoading(loading: Boolean, message: String) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnComplete.isEnabled = !loading
        if (loading && message.isNotEmpty()) {
            tvStatus.visibility = View.VISIBLE
            tvStatus.text = message
            tvStatus.setTextColor(getColor(android.R.color.darker_gray))
        } else if (!loading) {
            tvStatus.visibility = View.GONE
        }
    }

    private fun startMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
