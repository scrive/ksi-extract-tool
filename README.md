# ksi-extract-tool
A tool to extract and separate the KSI signature file and the signed doc.

This program takes one input argument that needs to be a .pdf document. 
If it is a Scrive signed document it will generate two files, one .ksig file and one pdf.
These two files can then be uploaded to Guardtimes verification tester at https://guardtime.com/verify#
and confirm that the document hasn't been tamepred with and that the seal is valid.

### Run with Docker
This project hase a Dockerfile that can be used to build the software and run it, so that one doesn't have to 
use other build tools. To build the docker container one just run `docker build -t ksi-extract-tool .` in the 
directory where you cloned the project.

When the container is built go to a directory where you have a Scrive signed document. Copy or rename the 
document so that you have a file called `input.pdf`. Then run the command
`docker run --rm -v $PWD:/files/ ksi-extract-tool`. You should end up with two files in the directory called 
`input_result.ksig` and `input_result.pdf`. Upload these two to the Guardtime verification service and see the
status and information about the KSI seal.  
