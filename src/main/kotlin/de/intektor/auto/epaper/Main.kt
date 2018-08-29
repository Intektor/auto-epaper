package de.intektor.auto.epaper

import com.google.common.hash.Hashing
import net.sourceforge.tess4j.Tesseract
import nu.pattern.OpenCV
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.awt.event.InputEvent
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.File
import javax.imageio.ImageIO

/**
 * @author Intektor
 */
fun main(args: Array<String>) {
//    launchEmulator(args[0], args[1])

    val trainedPath = createTempEngFile()

    OpenCV.loadLocally()

    Thread.sleep(args[2].toLong())

    val sleepPeriod = 5000L

    //The emulator must be located on the primary screen
    val sourceRobot = Robot()

    val screenRect = Rectangle(Toolkit.getDefaultToolkit().screenSize)

    val screenShot = sourceRobot.createScreenCapture(screenRect).convertType(BufferedImage.TYPE_3BYTE_BGR)

    val template = File("template.png").inputStream().use { ImageIO.read(it) }

    val resultCols = screenShot.width - template.width + 1
    val resultRows = screenShot.width - template.width + 1

    val resultMatrix = Mat(resultRows, resultCols, CvType.CV_32FC1)

    Imgproc.matchTemplate(screenShot.toMatrix(), template.toMatrix(), resultMatrix, Imgproc.TM_SQDIFF)

    Core.normalize(resultMatrix, resultMatrix, 0.0, 1.0, Core.NORM_MINMAX, -1, Mat())

    val r = Core.minMaxLoc(resultMatrix, Mat())
    println("${r.minLoc}")

    val width = args[3].toInt()
    val height = args[4].toInt()

    val robot = RelativeRobot(sourceRobot, r.minLoc.x.toInt(), r.minLoc.y.toInt(), width, height)

    //open app view
//    robot.dragMouse(width / 2.0, height * 0.8, width / 2.0, height * 0.2)

//    Thread.sleep(1000)

    while (true) {
        //open az app
        robot.moveMouse(171, 550)

        robot.click()

        //Wait for main app screen to open
        Thread.sleep(3000)

        val tesseract = Tesseract()
        tesseract.setDatapath(trainedPath)
        tesseract.setLanguage("eng")

        //screenshot
        val latestImage = robot.screenshot(15, 234, 139, 21)
        val date = tesseract.doOCR(latestImage)

        println(date)

        //open latest article
        robot.moveMouse(50, 200)

        robot.click()

        Thread.sleep(2000)

        robot.moveMouse(100, 300)

        robot.click()

        Thread.sleep(10000)

        //Article is now open
        //click on content

        robot.moveMouse(240, 760)

        robot.click()

        Thread.sleep(1000)

        var latestContentImage: BufferedImage
        do {
            robot.dragMouse(433, 670, 22, 670)

            Thread.sleep(5000)

            val newestContentImage = robot.screenshot(1, 544, 461, 245).convertType(BufferedImage.TYPE_3BYTE_BGR)

            latestContentImage = newestContentImage
        } while (latestContentImage.hash() != newestContentImage.hash())

        val pageAmountImage = robot.screenshot(218, 761, 25, 23)
        val pageAmount = tesseract.doOCR(pageAmountImage)

        println(pageAmount)

        //navigate back to main screen
        robot.moveMouse(102, 822)
        robot.click()

        Thread.sleep(3000)

        //close app
        robot.moveMouse(230, 822)
        robot.click()

        //make sleep
        Thread.sleep(sleepPeriod)
    }
}

class RelativeRobot(private val robot: Robot, private val sourceX: Int, private val sourceY: Int, private val width: Int, private val height: Int) {

    private val oWidth = 461.0
    private val oHeight = 850.0

    fun moveMouse(x: Int, y: Int) {
        robot.mouseMove(sourceX + x.rX(), sourceY + y.rY())
    }

    fun dragMouse(sX: Double, sY: Double, eX: Double, eY: Double) {
        dragMouse(sX.toInt(), sY.toInt(), eX.toInt(), eY.toInt())
    }

    fun dragMouse(sX: Int, sY: Int, eX: Int, eY: Int) {
        robot.mouseMove(sourceX + sX.rX(), sourceY + sY.rY())
        robot.mousePress(InputEvent.BUTTON1_MASK)

        robot.mouseMove(sourceX + eX.rX(), sourceY + eY.rY())
        robot.mouseRelease(InputEvent.BUTTON1_MASK)
    }

    fun click() {
        robot.mousePress(InputEvent.BUTTON1_MASK)
        robot.mouseRelease(InputEvent.BUTTON1_MASK)
    }

    fun screenshot(x: Int, y: Int, width: Int, height: Int): BufferedImage {
        return robot.createScreenCapture(
                Rectangle(sourceX + x.rX(), sourceY + y.rY(), width.rX(), height.rY())
        )
    }

    private fun Int.rX(): Int = ((this / oWidth) * width).toInt()

    private fun Int.rY(): Int = ((this / oHeight) * height).toInt()
}

fun BufferedImage.toMatrix(): Mat {
    val mat = Mat(height, width, CvType.CV_8UC3)
    val data = (raster.dataBuffer as DataBufferByte).data
    mat.put(0, 0, data)
    return mat
}

fun BufferedImage.convertType(newType: Int): BufferedImage {
    val new = BufferedImage(width, height, newType)
    new.graphics.drawImage(this, 0, 0, null)
    return new
}

fun createTempEngFile(): String {
    return RelativeRobot::class.java.getResourceAsStream("/eng.traineddata").use { input ->
        val tempFile = File("eng.traineddata")
        tempFile.outputStream().use {
            input.copyTo(it)
        }
        val path = tempFile.absolutePath
        path.substring(0 until path.length - "eng.traineddata".length)
    }
}

fun BufferedImage.hash(): Long = Hashing.sha512().hashBytes((this.raster.dataBuffer as DataBufferByte).data).asLong()
