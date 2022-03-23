# DigitalMammographyEnsembleValidation
External validation of the DREAM Digital Mammography Competition Ensemble Model

1. Download Docker images of the Ensemble Models
     > docker login docker.synapse.org
     > docker pull docker.synapse.org/syn7887972/9646823/scoring
     > docker pull docker.synapse.org/syn7887972/9648211/scoring
     > docker pull docker.synapse.org/syn7887972/9648881/scoring
     > docker pull docker.synapse.org/syn7887972/9648888/scoring
     > docker pull docker.synapse.org/syn7887972/9648889/scoring
     > docker pull docker.synapse.org/syn7887972/9648917/scoring
     > docker pull docker.synapse.org/syn7887972/9650054/scoring
     > docker pull docker.synapse.org/syn7887972/9650055/scoring
     > docker pull docker.synapse.org/syn7887972/9650232/scoring
     > docker pull docker.synapse.org/syn7887972/9650290/scoring
     > docker pull docker.synapse.org/syn7887972/9650300/scoring

  If Docker images cannot be downloaded, one needs to contact DREAM Challenge team for download permission. 
     

2. Prepare metadata and imaging files for running the ensemble models.
     1.1 Go to 'sc2_data_prep' and modify file, get_sc2_metadata.groovy, for db and other configs as indicated in the top of the file.
     1.2 Run 'get_sc2_metadata.groovy' to generate two metadata files. An imaging mapping file, exams_dcm_file_mappings.txt, is also created.
     1.3 Modify the original DICOM file source(s) for the file, 'sc2_copy_imgs.sh'. Run the shell script to copy DCM files into the 'images' directory.

3. Launch SC2 ensemble models
     place both 'metadata' and 'images' in a same parent directory. In the parent directory, run the following command.
	   > run_sc2_models.sh

