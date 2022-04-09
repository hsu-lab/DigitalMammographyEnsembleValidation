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
     

2. Prepare metadata and imaging files by running the Python program, sc2_data_prep.py.
   When facing a large number of exams, the software can divide all exams into sub-collections. User can specify a max number of subjects to be in a collection through
   a command-line argument.

3. Launch SC2 ensemble models
     place both 'metadata' and 'images' in a same parent directory. In the parent directory, run the following command.
	   > run_sc2_models.sh
	 In the case running the models for sub-collections, copy the 'run_sc2_models.sh' in each sub-collections and execute them in each place.

