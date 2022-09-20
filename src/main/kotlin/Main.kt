import com.guardtime.ksi.KSI
import com.guardtime.ksi.KSIBuilder
import com.guardtime.ksi.service.client.KSIServiceCredentials
import com.guardtime.ksi.service.client.http.HttpClientSettings
import com.guardtime.ksi.service.http.simple.SimpleHttpClient
import com.guardtime.ksi.trust.X509CertificateSubjectRdnSelector
import com.guardtime.ksi.unisignature.inmemory.InMemoryKsiSignatureFactory
import com.guardtime.signatureconverter.SignatureConverter
import com.itextpdf.io.source.RASInputStream
import com.itextpdf.io.source.RandomAccessSourceFactory
import com.itextpdf.kernel.pdf.PdfDictionary
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfObject
import com.itextpdf.kernel.pdf.PdfReader
import org.apache.commons.io.FileUtils
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.ParameterException
import picocli.CommandLine.Parameters
import picocli.CommandLine.Spec
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*
import kotlin.system.exitProcess

@Command(name = "KSI Extract Tool")
class KsiExtractor : Runnable {

    private lateinit var fileName: String
    private val LEGACY_SIGNATURE_FILTER: String = "GTTS.TimeStamp"
    private val KSI_SIGNATURE_FILTER: String = "GT.KSI"

    @Spec
    lateinit var spec : CommandSpec

    @CommandLine.Option(names = ["-c", "--config-file"], description = ["Guardtime config file. Needed for extracting Legacy signatures."])
    var configFile : String = ""

    @Parameters(arity = "1", paramLabel = "<filename>", description = ["Input document. Must be a PDF"])
    fun setInputFile(fileNameParam: String) {
        fileName = fileNameParam
        if (!fileName.endsWith(".pdf")) {
            throw ParameterException(spec.commandLine(), "Input file must be a PDF.")
        }
    }

    override fun run() {
        val pdfReader = PdfReader(ByteArrayInputStream(FileUtils.readFileToByteArray(File(fileName))))
        pdfReader.use { reader ->
            PdfDocument(reader).use { pdfDocument ->
                repeat(pdfDocument.numberOfPdfObjects) { index ->
                    val pdfObject = pdfDocument.getPdfObject(index + 1)
                    pdfObject?.takeIf { checkIsDictionary(it) }?.apply {
                        val dictObj = pdfDocument.getPdfObject(index + 1) as PdfDictionary
                        dictObj.takeIf { containsKsiSig(it, PdfName(KSI_SIGNATURE_FILTER)) || containsKsiSig(it, PdfName(LEGACY_SIGNATURE_FILTER))}?.apply {
                            val gaps = dictObj.getAsArray(PdfName.ByteRange).toLongArray()
                            val readerSource = pdfDocument.reader.safeFile.createSourceView()
                            if (dictObj.containsValue(PdfName(LEGACY_SIGNATURE_FILTER)) && configFile.isEmpty()) {
                                println("Guardtime config file missing. It is needed for extracting Legacy signatures. Exiting...")
                                exitProcess(1)
                            }
                            RASInputStream(
                                RandomAccessSourceFactory().createRanged(
                                    readerSource,
                                    gaps
                                )
                            ).use { rangeStream ->
                                    writePdfResult(rangeStream, fileName)
                            }
                            if(dictObj.containsValue(PdfName(KSI_SIGNATURE_FILTER))) {
                                writeSignature(
                                    dictObj.getAsString(
                                        PdfName.Contents
                                    ).valueBytes, fileName
                                )
                            } else if(!configFile.isEmpty()) {
                                writeLegacySignature(
                                    dictObj.getAsString(
                                        PdfName.Contents
                                    ).valueBytes,
                                    fileName,
                                    createKsi(configFile)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun main(args: Array<String>) {
    val exitCode = CommandLine(KsiExtractor()).execute(*args)
    exitProcess(exitCode)
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

private fun writeLegacySignature(sigBytes: ByteArray, fileName: String, ksi: KSI) {
        val ksiSignature =
            SignatureConverter(ksi).convert(
                ByteArrayInputStream(
                    sigBytes
                )
            )
        val signatureFile = File(fileName.substring(0, fileName.length - 4).plus("_result.ksig"))
        FileOutputStream(signatureFile).use { signatureOutputStream ->
            ksiSignature.writeTo(signatureOutputStream)
        }
}

fun createKsi(configFileParam: String): KSI {
    val properties = Properties()
    properties.load(FileInputStream(configFileParam))
    val httpClient = SimpleHttpClient(
        HttpClientSettings(
            properties.getProperty("signer.url"),
            properties.getProperty("extender.url"),
            properties.getProperty("publicationsfile.url"),
            KSIServiceCredentials(
                properties.getProperty("login.user"),
                properties.getProperty("login.key")
            )
        )
    )
    return  KSIBuilder()
        .setKsiProtocolSignerClient(httpClient)
        .setKsiProtocolExtenderClient(httpClient)
        .setKsiProtocolPublicationsFileClient(httpClient)
        .setPublicationsFileTrustedCertSelector(
            X509CertificateSubjectRdnSelector(
                    properties.getProperty("publicationsfile.constraint"))
        )
        .build()

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
