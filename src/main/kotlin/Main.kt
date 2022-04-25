import com.guardtime.ksi.unisignature.inmemory.InMemoryKsiSignatureFactory
import com.itextpdf.io.source.RASInputStream
import com.itextpdf.io.source.RandomAccessSourceFactory
import com.itextpdf.kernel.pdf.*
import org.apache.commons.io.FileUtils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.size != 1 || !args[0].endsWith(".pdf")) {
        println("USAGE: java -jar executable.jar input")
        println("Input file must be a pdf.")
        println("This will generate two files, a .ksig that is the signature and a .pdf that is the document that is signed by the signature")
        exitProcess(1)
    }
    val pdfReader = PdfReader(ByteArrayInputStream(FileUtils.readFileToByteArray(File(args[0]))))
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
                            writePdfResult(rangeStream, args)
                        }
                        writeSignature(dictObj, args)
                    }
                }
            }
        }
    }
}

private fun writeSignature(dictObj: PdfDictionary, args: Array<String>) {
    val ksiSignature =
        InMemoryKsiSignatureFactory().createSignature(
            ByteArrayInputStream(
                dictObj.getAsString(
                    PdfName.Contents
                ).valueBytes
            )
        )
    val signatureFile = File(args[0].substring(0, args[0].length - 4).plus("_result.ksig"))
    FileOutputStream(signatureFile).use { signatureOutputStream ->
        ksiSignature.writeTo(signatureOutputStream)
    }
}

private fun writePdfResult(rangeStream: RASInputStream, args: Array<String>) {
    val result = ByteArrayOutputStream()
    val buf = ByteArray(8192)
    var readInt: Int
    while ((rangeStream.read(buf, 0, buf.size).also { readInt = it }) > 0) {
        result.write(buf, 0, readInt)
    }
    val excludedDoc = File(args[0].substring(0, args[0].length - 4).plus("_result.pdf"))
    FileOutputStream(excludedDoc).use { pdfOutStream ->
        result.writeTo(pdfOutStream)
    }
}

private fun containsKsiSig(
    dictObj: PdfDictionary,
    ksiSig: PdfName
) = dictObj.containsKey(PdfName.Filter) && dictObj.containsValue(ksiSig)

private fun checkIsDictionary(pdfObject: PdfObject) =
    pdfObject.isDictionary
