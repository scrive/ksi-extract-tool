# ksi-extract-tool
A tool to extract and separate the KSI signature file and the signed doc.

This program takes one input argument that needs to be a .pdf document. 
If it is a Scrive signed document it will generate two files, one .ksig file and one pdf.
These two files can then be uploaded to Guardtimes verification tester at https://guardtime.com/verify#
and confirm that the document hasn't been tamepred with and that the seal is valid.

