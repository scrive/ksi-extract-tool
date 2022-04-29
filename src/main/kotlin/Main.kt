import com.guardtime.ksi.unisignature.inmemory.InMemoryKsiSignatureFactory
import com.itextpdf.io.source.RASInputStream
import com.itextpdf.io.source.RandomAccessSourceFactory
import com.itextpdf.kernel.pdf.PdfDictionary
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfObject
import com.itextpdf.kernel.pdf.PdfReader
import org.apache.commons.io.FileUtils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.size != 2 || !args[1].endsWith(".pdf")) {
        println("USAGE: java -jar executable.jar input")
        println("Input file must be a pdf.")
        println("This will generate two files, a .ksig that is the signature and a .pdf that is the document that is signed by the signature")
        exitProcess(1)
    }
    val fileName = args[0].plus(args[1])
    val pdfReader = PdfReader(ByteArrayInputStream(FileUtils.readFileToByteArray(File(fileName))))
    pdfReader.use { reader ->
        PdfDocument(reader).use { pdfDocument ->
            repeat(pdfDocument.numberOfPdfObjects) { index ->
                val pdfObject = pdfDocument.getPdfObject(index + 1)
                pdfObject?.takeIf { checkIsDictionary(it) }?.apply {
                    val dictObj = pdfDocument.getPdfObject(index + 1) as PdfDictionary
                    dictObj.takeIf { containsKsiSig(it, PdfName("GT.KSI")) }?.apply {
                        val gaps = dictObj.getAsArray(PdfName.ByteRange).toLongArray()
                        val readerSource = pdfDocument.reader.safeFile.createSourceView()
                        RASInputStream(
                            RandomAccessSourceFactory().createRanged(
                                readerSource,
                                gaps
                            )
                        ).use { rangeStream ->
                            writePdfResult(rangeStream, fileName)
                        }
                        writeSignature(
                            dictObj.getAsString(
                                PdfName.Contents
                            ).valueBytes, fileName
                        )
                    }
                }
            }
        }
    }
}

private fun writeSignature(sigBytes: ByteArray, fileName: String) {
    val ksiSignature =
        InMemoryKsiSignatureFactory().createSignature(
            ByteArrayInputStream(
                sigBytes
            )
        )
    val signatureFile = File(fileName.substring(0, fileName.length - 4).plus("_result.ksig"))
    FileOutputStream(signatureFile).use { signatureOutputStream ->
        ksiSignature.writeTo(signatureOutputStream)
    }
}

private fun writePdfResult(rangeStream: RASInputStream, fileName: String) {
    val result = ByteArrayOutputStream()
    val buf = ByteArray(8192)
    var readInt: Int
    while ((rangeStream.read(buf, 0, buf.size).also { readInt = it }) > 0) {
        result.write(buf, 0, readInt)
    }
    val excludedDoc = File(fileName.substring(0, fileName.length - 4).plus("_result.pdf"))
    FileOutputStream(excludedDoc).use { pdfOutStream ->
        result.writeTo(pdfOutStream)
    }
}

private fun containsKsiSig(
    dictObj: PdfDictionary,
    ksiSigName: PdfName
) = dictObj.containsKey(PdfName.Filter) && dictObj.containsValue(ksiSigName)

private fun checkIsDictionary(pdfObject: PdfObject) =
    pdfObject.isDictionary
