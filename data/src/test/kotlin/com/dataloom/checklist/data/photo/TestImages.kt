package com.dataloom.checklist.data.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import com.dataloom.checklist.domain.photo.ImageSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32

/** Images made in memory for the photo pipeline tests. Test data only; no real photos. */
object TestImages {

    /** Left half red, right half blue, so any rotation or flip is visible in the pixels. */
    fun halves(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.color = Color.RED
        canvas.drawRect(0f, 0f, width / 2f, height.toFloat(), paint)
        paint.color = Color.BLUE
        canvas.drawRect(width / 2f, 0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }

    fun jpegBytes(bitmap: Bitmap, quality: Int = 95): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    fun pngBytes(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    /**
     * A JPEG with an EXIF block: [orientation] (1..8) and, when [withGps], a GPS position. The tags
     * are written with the same library the app reads them with.
     */
    fun jpegWithExif(directory: File, width: Int, height: Int, orientation: Int, withGps: Boolean): File {
        val file = File.createTempFile("exif-", ".jpg", directory)
        file.writeBytes(jpegBytes(halves(width, height)))
        val exif = ExifInterface(file)
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
        if (withGps) {
            exif.setLatLong(12.9716, 77.5946)
            exif.setAttribute(ExifInterface.TAG_MAKE, "TestPhone")
        }
        exif.saveAttributes()
        return file
    }

    fun source(bytes: ByteArray) = ImageSource { ByteArrayInputStream(bytes) }

    fun source(file: File) = ImageSource { file.inputStream() }

    /**
     * Just enough PNG (signature, IHDR, a stub IDAT, IEND) to claim [width] x [height] pixels. The
     * pipeline must refuse it from the header alone, without decoding any pixel.
     */
    fun pngClaiming(width: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        val header = ByteArrayOutputStream()
        DataOutputStream(header).apply {
            writeInt(width)
            writeInt(height)
            writeByte(8) // bit depth
            writeByte(2) // truecolor
            writeByte(0)
            writeByte(0)
            writeByte(0)
        }
        chunk(out, "IHDR", header.toByteArray())
        chunk(out, "IDAT", byteArrayOf(0x78, 0x9C.toByte(), 0x03, 0x00, 0x00, 0x00, 0x00, 0x01))
        chunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun chunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        val stream = DataOutputStream(out)
        stream.writeInt(data.size)
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        stream.write(typeBytes)
        stream.write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        stream.writeInt(crc.value.toInt())
    }

    fun decode(file: File): Bitmap = requireNotNull(BitmapFactory.decodeFile(file.path)) { "Not an image: $file" }
}
