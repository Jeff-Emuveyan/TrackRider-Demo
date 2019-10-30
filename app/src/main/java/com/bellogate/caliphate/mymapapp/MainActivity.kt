package com.bellogate.caliphate.mymapapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import kotlinx.android.synthetic.main.activity_main.*




class MainActivity : AppCompatActivity() {

    lateinit var mapFragment : MapFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        var destFragment = MapHolderFragment.newInstance()

        // First get FragmentManager object.
        val fragmentManager = this.supportFragmentManager

        // Begin Fragment transaction.
        val fragmentTransaction = fragmentManager.beginTransaction()

        // Replace the layout holder with the required Fragment object.
        fragmentTransaction.replace(R.id.main_frame_layout, destFragment)

        // Commit the Fragment replace action.
        fragmentTransaction.commit()


    }



    private fun getDisplayHeight(): Float {
        return this.resources.displayMetrics.heightPixels.toFloat()
    }

}
