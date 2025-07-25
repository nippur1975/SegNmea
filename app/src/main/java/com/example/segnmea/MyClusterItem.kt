package com.example.segnmea

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem

class MyClusterItem(
    lat: Double,
    lng: Double,
    private val title: String,
    private val snippet: String
) : ClusterItem {

    private val position: LatLng = LatLng(lat, lng)

    override fun getPosition(): LatLng {
        return position
    }

    override fun getTitle(): String? {
        return title
    }

    override fun getSnippet(): String? {
        return snippet
    }

    // Método obligatorio desde android-maps-utils 2.2.0+
    override fun getZIndex(): Float? {
        return 0f
    }
}
