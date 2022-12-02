package com.example.samsdrawingapp

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Interpolator
import android.media.MediaScannerConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Message
import android.provider.ContactsContract.CommonDataKinds.Im
import android.provider.ContactsContract.Directory
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private var drawingView: DrawingView? = null
    private var mImageButtonCurrentPaint: ImageButton ?= null
    var customProgessDialogue:Dialog ?= null

    private var linkLoc:String ?= null

    val openGalleryLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            result ->
            if(result.resultCode == RESULT_OK && result.data != null){
                val imageBackground:ImageView = findViewById(R.id.iv_background)
                imageBackground.setImageURI(result.data?.data)
            }
        }

    val requestPermission: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
            permissions ->
            permissions.entries.forEach{
                val permissionName = it.key
                val isGranted = it.value

                if(isGranted){
                    Toast.makeText(this, "Permission granted, you can read the files", Toast.LENGTH_LONG).show()

                    val pickIntent:Intent = Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    openGalleryLauncher.launch(pickIntent)
                }
                else{
                    Toast.makeText(this, "Permission Denied", Toast.LENGTH_LONG).show()
                }
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        drawingView = findViewById(R.id.drawingView)
        drawingView?.setSizeForBrush(20.0f)

        val linearLayoutPaintColours:LinearLayout = findViewById(R.id.colour_pallet)

        mImageButtonCurrentPaint = linearLayoutPaintColours[1] as ImageButton
        mImageButtonCurrentPaint!!.setImageDrawable(
            ContextCompat.getDrawable(this,R.drawable.pallet_pressed)
        )

        val brushBtn: ImageButton = findViewById(R.id.brush_picker)

        brushBtn.setOnClickListener{
            brushSizeChooserDialog()
        }

        val mGalleryButton = findViewById<ImageButton>(R.id.gallery_btn)

        mGalleryButton.setOnClickListener {
            requestStoragePermission()
        }

        val undoButton: ImageButton = findViewById(R.id.undo_btn)
        undoButton.setOnClickListener {
            drawingView?.onClickUndo()
        }

        val saveButton:ImageButton = findViewById(R.id.save_btn)
        saveButton.setOnClickListener {
            showProgressDialog()
            if(isReadStorageAllowed()){
                lifecycleScope.launch {
                    val fl:FrameLayout = findViewById(R.id.fl_drawing_container)
                    val mBitmap:Bitmap = getBitmapFromView(fl)
                    saveBitmap(mBitmap)
                }
            }
        }

        val shareButton:ImageButton = findViewById(R.id.share_btn)
        shareButton.setOnClickListener {
            if(linkLoc!=null){
                shareImage(linkLoc!!)
            }
            else{
                Toast.makeText(this, "Save the image before sharing", Toast.LENGTH_LONG).show()
            }
        }

    }

    private fun brushSizeChooserDialog(){
        val dialog:Dialog = Dialog(this)
        dialog.setContentView(R.layout.dialogue_brush_size)
        dialog.setTitle("Select Brush Size")
        val smallBtn = dialog.findViewById<ImageButton>(R.id.ib_small_brush)
        val mediumBtn = dialog.findViewById<ImageButton>(R.id.ib_medium_brush)
        val bigBtn = dialog.findViewById<ImageButton>(R.id.ib_large_brush)
        smallBtn.setOnClickListener{
            drawingView?.setSizeForBrush(10.0f)
            dialog.dismiss()
        }
        mediumBtn.setOnClickListener {
            drawingView?.setSizeForBrush(20.toFloat())
            dialog.dismiss()
        }
        bigBtn.setOnClickListener {
            drawingView?.setSizeForBrush(30.toFloat())
            dialog.dismiss()
        }

        dialog.show()
    }

    fun paintClicked(view:View){
        if(view !== mImageButtonCurrentPaint){
            val imageButton = view as ImageButton
            val colourTag = imageButton.tag.toString()
            drawingView!!.setColour(colourTag)

            imageButton.setImageDrawable(
                ContextCompat.getDrawable(this,R.drawable.pallet_pressed)
            )

            mImageButtonCurrentPaint!!.setImageDrawable(
                ContextCompat.getDrawable(this,R.drawable.pallet_normal)
            )

            mImageButtonCurrentPaint = view
        }
    }

    /*We implement the adding of permissions to access the external storage here with the following functions
     */

    fun showRationaleDialogue(title:String, message: String){
        val builder:AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("Cancel"){dialogue,_ ->
            dialogue.dismiss()
        }
        builder.create()
        builder.show()
    }

    private fun isReadStorageAllowed():Boolean{
        var result = ContextCompat.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE)
        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission(){
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this, Manifest.permission.READ_EXTERNAL_STORAGE)){
            showRationaleDialogue("Sam's Drawing App","Sam's Drawing app needs permission to Access your Storage")
        }else{
            requestPermission.launch(arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        }
    }

    private fun getBitmapFromView(view: View):Bitmap{
        val returnedBitmap:Bitmap = Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background
        if (bgDrawable != null){
            bgDrawable.draw(canvas)
        }
        else{
            canvas.drawColor(Color.WHITE)
        }

        view.draw(canvas)

        return returnedBitmap
    }

    private suspend fun saveBitmap(mBitmap : Bitmap):String{
        var result = ""
        withContext(Dispatchers.IO){
            if(mBitmap != null){
                try {
                    val bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG,90,bytes)

                    val file:File = File(externalCacheDir?.absoluteFile.toString() + File.separator + "SamsDrawing" + System.currentTimeMillis()/1000 + ".png")
                    val fileOutput = FileOutputStream(file)
                    fileOutput.write(bytes.toByteArray())
                    fileOutput.close()

                    result = file.absolutePath
                    linkLoc = result

                    runOnUiThread{
                        cancelProgressDialogue()
                        if(result.isNotEmpty()){
                            Toast.makeText(this@MainActivity, "File saved successful: $result", Toast.LENGTH_SHORT).show()
                        }
                        else{
                            Toast.makeText(this@MainActivity, "Something went wrong, File not saved", Toast.LENGTH_SHORT).show()
                        }
                    }

                }catch (e:Exception){
                    result=""
                    e.printStackTrace()
                }
            }
        }
        return result
    }

    private fun showProgressDialog(){
        customProgessDialogue = Dialog(this)
        customProgessDialogue?.setContentView(R.layout.custom_dialogue_progress)
        customProgessDialogue?.show()
    }

    private fun cancelProgressDialogue(){
        if(customProgessDialogue!=null){
            customProgessDialogue?.dismiss()
            customProgessDialogue = null
        }
    }

    private fun shareImage(result:String){
        MediaScannerConnection.scanFile(this, arrayOf(result),null){
            path, uri ->
            val shareIntent:Intent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM,uri)
            shareIntent.type = "image/png"
            startActivity(Intent.createChooser(shareIntent,"Share"))
        }
    }

}