package me.swe.main

import java.awt.Color
import java.awt.Font
import java.awt.Image
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

/**
 * 将多行文字转换成图像
 */
object MultiLineTextToImage {
    @JvmStatic
    fun buildImage(title: String, toSaveFileName: String, inputMap: Map<String, String>?): String {
        var inputString = ""
        inputMap?.forEach {
            inputString += "${it.key}    ${it.value}\n"
        }
        var img = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        var g2d = img.createGraphics()
        val toUseFont = Font("", Font.PLAIN, 16)
        val titleFont = Font("", Font.PLAIN, 24)
        g2d.dispose()
        val inputStringArray = inputString.split("\n")
        var stringLines = 0
        var maxStringLength = 0

        inputStringArray.forEach {
            stringLines++
            if (g2d.fontMetrics.getStringBounds(it, g2d).width.toInt() > maxStringLength) {
                maxStringLength = g2d.fontMetrics.getStringBounds(it, g2d).width.toInt()
            }
        }
        val imageHeight = stringLines * g2d.getFontMetrics(toUseFont).height + 50
        val imageWidth = maxStringLength + 400
        img = BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB)
        g2d = img.createGraphics()

        val bgImage: Image = ImageIO.read(File("resource" + File.separator + "bg.png"))
        val bgImageHeight = bgImage.getHeight(null)
        val bgImageWidth = bgImage.getWidth(null)
        var bgImageDrawPosX = 0
        var bgImageDrawPosY = 0
        while (bgImageDrawPosY < imageHeight) {
            while (bgImageDrawPosX < imageWidth) {
                g2d.drawImage(bgImage, bgImageDrawPosX, bgImageDrawPosY, bgImageWidth, bgImageHeight, null)
                // println("draw pos: X=$bgImageDrawPosX, Y=$bgImageDrawPosY")
                bgImageDrawPosX += bgImageWidth
            }
            bgImageDrawPosX = 0
            bgImageDrawPosY += bgImageHeight
        }

        g2d.setRenderingHint(
            RenderingHints.KEY_ALPHA_INTERPOLATION,
            RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY
        )
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY)
        g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE)
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_DEFAULT)
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g2d.color = Color.BLACK
        g2d.font = titleFont
        g2d.drawString(title, 40, g2d.getFontMetrics(titleFont).height)
        g2d.font = toUseFont
        var linePosition = 60
        val fontSize = g2d.font.size
        println("font height = $fontSize")
        inputMap?.forEach {
            val beautifiedValue = it.value.replace("|", " | ")
            g2d.drawString(it.key, 10, linePosition)
            g2d.drawString(beautifiedValue, 150, linePosition)
            linePosition += fontSize + 5
        }
        g2d.dispose()
        try {
            val saveImage = File("cache" + File.separator + toSaveFileName)
            ImageIO.write(img, "png", saveImage)
            // println(saveImage.absolutePath)
            return saveImage.absolutePath
        } catch (ex: IOException) {
            ex.printStackTrace()
        }
        return ""
    }
}