# ksi-extract-tool
A tool to extract and separate the KSI signature file and the signed doc.

This program takes one input argument that needs to be a .pdf document. 
If it is a Scrive signed document it will generate two files, one .ksig file and one pdf.
These two files can then be uploaded to GuardTimes verification tester at https://guardtime.com/verify#
and confirm that the document hasn't been tampered with and that the seal is valid.
To be able to use this tool with Scrive signed document created pre-2018 you need to provide the program with a config file
that contains credentials so that the program can connect to a Guardtime API and convert the legacy signature to the 
current format. This is done with using a `-c CONFIG_PROPERTIES_FILE` option as input.

### Run with Docker
This project has a Dockerfile that can be used to build the software and run it, so that one doesn't have to 
use other build tools. To build the docker container just run `docker build -t ksi-extract-tool .` in the 
directory where you cloned the project.

When the container is built, go to a directory where you have a Scrive signed document and run the command
`docker run --rm -v $PWD:/files/ ksi-extract-tool /files/_YOUR_FILENAME_` . You should end up with two files in the directory called 
`input_result.ksig` and `input_result.pdf`. Upload these two to the GuardTime verification service and see the
status and information about the KSI seal.  
