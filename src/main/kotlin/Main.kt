import com.guardtime.ksi.unisignature.inmemory.InMemoryKsiSignatureFactory
import com.itextpdf.io.source.RASInputStream
import com.itextpdf.io.source.RandomAccessSourceFactory
import com.itextpdf.kernel.pdf.PdfDictionary
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfReader
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
    PdfReader(ByteArrayInputStream(FileUtils.readFileToByteArray(File(args[0])))).use { reader ->
        PdfDocument(reader).use { pdfDocument ->
            val ksiSig = PdfName("GT.KSI")
            repeat(pdfDocument.numberOfPdfObjects) { index ->
                val pdfObject = pdfDocument.getPdfObject(index + 1)
                if (pdfObject != null) {
                    if (pdfObject.isDictionary) {
                        val dictObj = pdfDocument.getPdfObject(index + 1) as PdfDictionary
                        if (dictObj.containsKey(PdfName.Filter) && dictObj.containsValue(ksiSig)) {
                            val signatureString = dictObj.getAsString(PdfName.Contents)
                            val byteRangeArray = dictObj.getAsArray(PdfName.ByteRange)
                            val gaps = byteRangeArray.toLongArray()
                            val readerSource = pdfDocument.reader.safeFile.createSourceView()

                            RASInputStream(
                                RandomAccessSourceFactory().createRanged(
                                    readerSource,
                                    gaps
                                )
                            ).use { rangeStream ->
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
                            val ksiSignature =
                                InMemoryKsiSignatureFactory().createSignature(ByteArrayInputStream(signatureString.valueBytes))
                            val signaturefile = File(args[0].substring(0, args[0].length - 4).plus("_result.ksig"))
                            FileOutputStream(signaturefile).use { signatureOutputStream ->
                                ksiSignature.writeTo(signatureOutputStream)
                            }
                        }
                    }
                }
            }
        }
    }
}
