package com.ifpr.androidapptemplate.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.*
import com.google.firebase.database.*
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.ifpr.androidapptemplate.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import com.ifpr.androidapptemplate.R
import com.ifpr.androidapptemplate.baseclasses.Item
import com.ifpr.androidapptemplate.databinding.FragmentHomeBinding
import kotlin.math.*
import com.ifpr.androidapptemplate.ui.ai.AiLogicActivity

class HomeFragment : Fragment() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var database: DatabaseReference

    private lateinit var textNearestMarket: TextView
    private lateinit var btnDistanceMarket: Button
    private lateinit var btnOpenMaps: Button

    private var currentLocation: Location? = null
    private var nearestMarket: Market? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        inicializaGerenciamentoLocalizacao(view)

        val container = view.findViewById<LinearLayout>(R.id.itemContainer)
        carregarItensMarketplace(container)

        val fab = view.findViewById<FloatingActionButton>(R.id.fab_ai)

        fab.setOnClickListener {
            val context = view.context
            val intent = Intent(context, AiLogicActivity::class.java)
            context.startActivity(intent)
        }

        return view
    }

    private fun inicializaGerenciamentoLocalizacao(view: View) {
        currentAddressTextView = view.findViewById(R.id.currentAddressTextView)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        database = FirebaseDatabase.getInstance().getReference("markets")

        textNearestMarket = view.findViewById(R.id.textNearestMarket)
        btnDistanceMarket = view.findViewById(R.id.btnDistanceMarket)
        btnOpenMaps = view.findViewById(R.id.btnOpenMaps)

        btnDistanceMarket.setOnClickListener {
            getCurrentLocation()
        }

        btnOpenMaps.setOnClickListener {
            nearestMarket?.let {
                val uri = Uri.parse("geo:${it.latitude},${it.longitude}?q=${Uri.encode(it.name)}")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.setPackage("com.google.android.apps.maps")
                startActivity(intent)
            } ?: Toast.makeText(requireContext(), "Nenhum mercado encontrado", Toast.LENGTH_SHORT).show()
        }

        return view
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
            return
        }

        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30000)
            .setMinUpdateIntervalMillis(30000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    currentLocation = location
                    getNearestMarket()
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest, locationCallback, Looper.getMainLooper()
        )
    }

    private fun getNearestMarket() {
        currentLocation?.let { location ->
            database.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var nearest: Market? = null
                    var shortestDistance = Double.MAX_VALUE

                    for (marketSnapshot in snapshot.children) {
                        val market = marketSnapshot.getValue(Market::class.java)
                        if (market != null) {
                            val distance = calculateDistance(
                                location.latitude, location.longitude,
                                market.latitude, market.longitude
                            )

                            if (distance < shortestDistance) {
                                shortestDistance = distance
                                nearest = market
                            }
                        }
                    }

                    if (nearest != null) {
                        nearestMarket = nearest
                        textNearestMarket.text =
                            "Mercado mais próximo:\n${nearest.name}\n${nearest.address}\n(${shortestDistance.roundToInt()} m)"
                    } else {
                        textNearestMarket.text = "Nenhum mercado encontrado no banco."
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(requireContext(), "Erro ao acessar o banco", Toast.LENGTH_SHORT).show()
                }
            })
        } ?: Toast.makeText(requireContext(), "Localização não encontrada", Toast.LENGTH_SHORT).show()
    }

    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val R = 6371000.0 // raio da Terra em metros
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) *
                cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    data class Market(
        val name: String = "",
        val address: String = "",
        val latitude: Double = 0.0,
        val longitude: Double = 0.0
    )
}