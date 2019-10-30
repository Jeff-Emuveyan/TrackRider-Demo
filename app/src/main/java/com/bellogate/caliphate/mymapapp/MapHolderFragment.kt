package com.bellogate.caliphate.mymapapp

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import androidx.lifecycle.ViewModelProviders
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import kotlinx.android.synthetic.main.map_holder_fragment.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.animation.LinearInterpolator
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.checkSelfPermission
import com.androidnetworking.error.ANError
import org.json.JSONArray
import com.androidnetworking.interfaces.JSONArrayRequestListener
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.common.Priority
import com.androidnetworking.interfaces.JSONObjectRequestListener
import com.bellogate.caliphate.mymapapp.driverMotionTracker.models.events.BeginJourneyEvent
import com.bellogate.caliphate.mymapapp.driverMotionTracker.models.events.CurrentJourneyEvent
import com.bellogate.caliphate.mymapapp.driverMotionTracker.models.events.EndJourneyEvent
import com.bellogate.caliphate.mymapapp.driverMotionTracker.utils.JourneyEventBus
import com.bellogate.caliphate.mymapapp.driverMotionTracker.utils.Util
import com.bellogate.caliphate.mymapapp.driverMotionTracker.utils.Util.getBearing
import com.google.android.gms.maps.model.*
import com.google.android.gms.maps.model.JointType.ROUND
import org.json.JSONObject


class MapHolderFragment : Fragment(), OnMapReadyCallback {


    companion object {
        fun newInstance() = MapHolderFragment()
        var ALL_PERMISSIONS_RESULT = 101
    }

    var googleMap: GoogleMap? = null
    var marker: Marker? = null
    var latLngA: LatLng? = null //this will hold our current location
    var latLngB: LatLng? = null//this will hold the location we tapped on the screen

    private lateinit var viewModel: MapHolderViewModel


    var locationTrack: LocationTrack? = null
    private lateinit var permissionsToRequest: ArrayList<String>
    private lateinit var permissionsRejected: ArrayList<String>
    private lateinit var permissions: ArrayList<String>

    private var polyLineList: List<LatLng>? = null
    private lateinit var blackPolyline: Polyline
    private lateinit var greyPolyLine: Polyline
    private lateinit var  blackPolyLineOptions: PolylineOptions

    private var startPosition: LatLng? = null
    private var endPosition: LatLng? = null
    private var v: Float? = null
    private var lat: Double? = null
    private var lng: Double? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.map_holder_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(MapHolderViewModel::class.java)
        // TODO: Use the ViewModel
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val map = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        map?.getMapAsync(this)

        permissions = ArrayList<String>()
        permissions.add(ACCESS_FINE_LOCATION)
        permissions.add(ACCESS_COARSE_LOCATION)

        permissionsToRequest = findUnAskedPermissions(permissions);
        //get the permissions we have asked for before but are not granted..
        //we will store this in a global list to access later.


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (permissionsToRequest.size > 0) {
                val array = arrayOfNulls<String>(permissionsToRequest.size)
                requestPermissions(permissionsToRequest.toArray(array), ALL_PERMISSIONS_RESULT)
            }
        }


        trackButton.setOnClickListener {
            //change the default location to the location the Android phone is currently:
            locationTrack = LocationTrack(context!!)
            if (locationTrack!!.canGetLocation()) {
                val longitude = locationTrack!!.getLongitude()
                val latitude = locationTrack!!.getLatitude()

                moveToLocation(latitude, longitude)
                ///save this location
                latLngA = LatLng(latitude, longitude)
                Toast.makeText(context, "Longitude:" + java.lang.Double.toString(longitude) + "\nLatitude:" + java.lang.Double.toString(latitude), Toast.LENGTH_SHORT).show()
            } else {

                locationTrack!!.showSettingsAlert()
            }
        }

        showRiderButton.setOnClickListener {

            if(latLngA != null && latLngB != null){
                var requestUrl = getRequestUrl(latLngA!!, latLngB!!)

                Log.e(MapHolderFragment::javaClass.name, "URL: $requestUrl")
                AndroidNetworking.get(requestUrl)
                    .setPriority(Priority.HIGH)
                    .build()
                    .getAsJSONObject(object: JSONObjectRequestListener{
                        override fun onResponse(response: JSONObject?) {
                            response?.let {
                                Log.e(MapHolderFragment.javaClass.simpleName, response?.toString())

                                //get the routes:
                                val routes: JSONArray = response.getJSONArray("routes")

                                if (routes.length() > 0) {
                                    for (x in 0 until routes.length()) {
                                        val route = routes.getJSONObject(x)
                                        val poly = route.getJSONObject("overview_polyline")
                                        val polyLine = poly.getString("points")
                                        polyLineList = Util.decodePoly(polyLine)
                                        drawPolyLineAndAnimateCar()
                                    }
                                }

                            }

                        }

                        override fun onError(anError: ANError?) {
                            Toast.makeText(context,"Network Error", Toast.LENGTH_LONG).show()
                        }
                    }
                    )

            }else{
                Toast.makeText(context,"One of the two locations is null", Toast.LENGTH_LONG).show()

            }
        }
    }




    override fun onMapReady(gm:  GoogleMap?) {
        Toast.makeText(context, "mapReady", Toast.LENGTH_LONG).show()
        googleMap = gm

        val latLng = LatLng(37.7750, 122.4183)//default location (Somewhere in South Korea)
        val markerOptions = MarkerOptions()
        markerOptions.position(latLng).title("Gleem Kitchen").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        marker = googleMap?.addMarker(markerOptions)
        googleMap?.mapType = GoogleMap.MAP_TYPE_NORMAL
        googleMap?.moveCamera(CameraUpdateFactory.newLatLng(latLng))


        googleMap?.setOnMapLongClickListener {
            //add a new marker at the position you just clicked:
            Toast.makeText(context,"lat: ${it.latitude}  long:${it.longitude}", Toast.LENGTH_LONG).show()
            addNewMarkerToMap(it)
            //save this second location
            latLngB = LatLng(it.latitude, it.longitude)
        }


    }



    private fun moveToLocation(location: Location?){
        if(location != null){
            //this will just change the position of the red marker on the map to the location you provided:
            marker?.position = LatLng(location.latitude, location.longitude)
            //Now to move the camera to that same position too, we need to do this:
            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 16.0f)) //0.0f will give a wide country view. To zoom closer you need to increase the value.
        }else{
            Log.e("MapHolderFragment", "Location is null")
            Toast.makeText(activity, "Can't move", Toast.LENGTH_LONG).show()
        }
    }


    private fun moveToLocation(lat: Double, long: Double){
        //this will just change the position of the red marker on the map to the location you provided:
        marker?.position = LatLng(lat, long)
        //Now to move the camera to that same position too, we need to do this:
        googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, long), 16.0f)) //0.0f will give a wide country view. To zoom closer you need to increase the value.

    }




    /***
     * Adds a new marker on the map and returns the marker object
     * ***/
    private fun addNewMarkerToMap(latLng: LatLng): Marker?{
        val markerOptions = MarkerOptions()
        markerOptions.position(latLng)
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))// color of any new marker is green.
        return googleMap?.addMarker(markerOptions)

    }


    private fun getRequestUrl(origin: LatLng, dest: LatLng): String {
        //Value of origin
        val start = "origin=" + origin.latitude + "," + origin.longitude
        //Value of destination
        val destination = "destination=" + dest.latitude + "," + dest.longitude
        //Set value enable the sensor
        val sensor = "sensor=false"
        //transit preference
        val transit_routing_preference = "less_driving"
        //Mode for find direction
        val mode = "mode=driving"
        //key
        val key = "key=${resources.getString(R.string.my_api_key)}"
        //Build the full param
        val param = "$mode&$transit_routing_preference&$start&$destination&$key"
        //Output format
        val output = "json"
        //Create url to request
        return "https://maps.googleapis.com/maps/api/directions/$output?$param"
    }



    private fun findUnAskedPermissions(wanted: ArrayList<String>): ArrayList<String> {
        val result = ArrayList<String>()

        for (perm in wanted) {
            if (!hasPermission(perm)) {
                result.add(perm)
            }
        }
        return result
    }


    private fun hasPermission(premission: String): Boolean{

        if (canMakeSmores()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return (checkSelfPermission(context!!, premission) == PackageManager.PERMISSION_GRANTED);
            }
        }
        return true
    }


    private fun canMakeSmores(): Boolean {
        return Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        when(requestCode){

            ALL_PERMISSIONS_RESULT->{
                for (perms in permissionsToRequest) {
                    if (!hasPermission(perms)) {
                        permissionsRejected = ArrayList<String>()
                        permissionsRejected.add(perms)
                    }
                }

                if (permissionsRejected.size > 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (shouldShowRequestPermissionRationale(permissionsRejected[0])) {
                            showMessageOKCancel("These permissions are mandatory for the application. Please allow access.",
                                DialogInterface.OnClickListener { dialog, which ->
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        requestPermissions(
                                            permissionsRejected.toArray(
                                                arrayOfNulls(
                                                    permissionsRejected.size
                                                )
                                            ), ALL_PERMISSIONS_RESULT
                                        )
                                    }
                                })
                            return
                        }
                    }

                }


            }

        }
    }


    private fun showMessageOKCancel(message: String, okListener: DialogInterface.OnClickListener) {
        AlertDialog.Builder(context)
            .setMessage(message)
            .setPositiveButton("OK", okListener)
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        locationTrack?.stopListener()
    }


    private fun drawPolyLineAndAnimateCar() {
        //Adjusting bounds
        val builder = LatLngBounds.Builder()
        for (latLng in polyLineList!!) {
            builder.include(latLng)
        }
        val bounds = builder.build()
        val mCameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, 2)
        googleMap?.animateCamera(mCameraUpdate)

        var polylineOptions = PolylineOptions()
        polylineOptions.color(Color.GRAY)
        polylineOptions.width(5f)
        polylineOptions.startCap(SquareCap())
        polylineOptions.endCap(SquareCap())
        polylineOptions.jointType(ROUND)
        polylineOptions.addAll(polyLineList)
        greyPolyLine = googleMap!!.addPolyline(polylineOptions)

        var blackPolylineOptions = PolylineOptions()
        blackPolylineOptions.width(5f)
        blackPolylineOptions.color(Color.BLACK)
        blackPolylineOptions.startCap(SquareCap())
        blackPolylineOptions.endCap(SquareCap())
        blackPolylineOptions.jointType(ROUND)
        blackPolyline = googleMap!!.addPolyline(blackPolylineOptions)

        googleMap?.addMarker(
            MarkerOptions()
                .position(polyLineList!!.get(polyLineList!!.size - 1))
        )

        val polylineAnimator = ValueAnimator.ofInt(0, 100)
        polylineAnimator.duration = 2000
        polylineAnimator.interpolator = LinearInterpolator()
        polylineAnimator.addUpdateListener { valueAnimator ->
            val points = greyPolyLine.points
            val percentValue = valueAnimator.animatedValue as Int
            val size = points.size
            val newPoints = (size * (percentValue / 100.0f)).toInt()
            val p = points.subList(0, newPoints)
            blackPolyline.points = p
        }
        polylineAnimator.start()
        marker = googleMap?.addMarker(
            MarkerOptions().position(latLngA!!)
                .flat(true)
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.rsz_ic_car))
        )
        val handler = Handler()
        var index = -1
        var next = 1
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (index < polyLineList!!.size - 1) {
                    index++
                    next = index + 1
                }
                if (index < polyLineList!!.size - 1) {
                    startPosition = polyLineList!!.get(index)
                    endPosition = polyLineList!!.get(next)
                }
                if (index == 0) {
                    val beginJourneyEvent = BeginJourneyEvent()
                    beginJourneyEvent.setBeginLatLng(startPosition)
                    JourneyEventBus.getInstance().setOnJourneyBegin(beginJourneyEvent)
                }
                if (index == polyLineList!!.size - 1) {
                    val endJourneyEvent = EndJourneyEvent()
                    endJourneyEvent.setEndJourneyLatLng(
                        LatLng(
                            polyLineList?.get(index)!!.latitude,
                            polyLineList?.get(index)!!.longitude
                        )
                    )
                    JourneyEventBus.getInstance().setOnJourneyEnd(endJourneyEvent)
                }
                val valueAnimator = ValueAnimator.ofFloat(0f, 1f)
                valueAnimator.duration = 3000
                valueAnimator.interpolator = LinearInterpolator()
                valueAnimator.addUpdateListener { valueAnimator ->
                    v = valueAnimator.animatedFraction
                    lng = v!! * endPosition!!.longitude + (1 - v!!) * startPosition!!.longitude
                    lat = v!! * endPosition!!.latitude + (1 - v!!) * startPosition!!.latitude
                    val newPos = LatLng(lat!!, lng!!)
                    val currentJourneyEvent = CurrentJourneyEvent()
                    currentJourneyEvent.setCurrentLatLng(newPos)
                    JourneyEventBus.getInstance().setOnJourneyUpdate(currentJourneyEvent)
                    marker!!.setPosition(newPos)
                    marker!!.setAnchor(0.5f, 0.5f)
                    marker!!.setRotation(getBearing(startPosition, newPos))
                    googleMap!!.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder().target(newPos)
                                .zoom(15.5f).build()
                        )
                    )
                }
                valueAnimator.start()
                if (index != polyLineList!!.size - 1) {
                    handler.postDelayed(this, 3000)
                }
            }
        }, 3000)
    }



}
